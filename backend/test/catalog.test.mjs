import test from 'node:test';
import assert from 'node:assert/strict';
process.env.NODE_ENV='test';
process.env.INSFORGE_URL='https://auth.invalid';
const {createApp,watchRate}=await import('../src/server.mjs');
const {unwrap}=await import('../src/platform.mjs');
const {PolicyError}=await import('../src/policy.mjs');
const originalFetch=globalThis.fetch;
const id='00000000-0000-4000-8000-000000000002';

// Base de datos en memoria con los filtros que usa la API.
function database(tables) {
  return { from(name) {
    const filters=[]; let insert, update, remove;
    const q={select:()=>q,order:()=>q,limit:()=>q,range:()=>q,not:()=>q,or:()=>q,ilike:()=>q,
      eq(k,v){filters.push(r=>r[k]===v);return q},gt(k,v){filters.push(r=>r[k]>v);return q},
      insert(rows){insert=rows;return q},update(values){update=values;return q},delete(){remove=true;return q},
      then(resolve){
        const rows=tables[name]??=[];
        if(insert) rows.push(...insert.map(row=>({created_at:new Date().toISOString(),...row})));
        const data=insert ?? rows.filter(row=>filters.every(f=>f(row)));
        if(update) data.forEach(row=>Object.assign(row,update));
        if(remove) tables[name]=rows.filter(row=>!data.includes(row));
        return Promise.resolve({data,error:null}).then(resolve);
      }};
    return q;
  } };
}
async function serve(client,run) {
  const server=createApp(client).listen(0,'127.0.0.1');
  await new Promise(resolve=>server.once('listening',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  // El token «Bearer usuario» identifica a ese usuario; los «nuevo…» se crearon hoy.
  const created=user=>new Date(Date.now()-(user.startsWith('nuevo') ? 3600_000 : 30*86400_000)).toISOString();
  globalThis.fetch=async(url,options)=>String(url).startsWith('https://auth.invalid')
    ? new Response(JSON.stringify({user:{id:options.headers.Authorization.slice(7),role:'authenticated',createdAt:created(options.headers.Authorization.slice(7))}}),{status:200})
    : originalFetch(url,options);
  try { await run((path,user,init={})=>originalFetch(base+path,{...init,headers:{Authorization:`Bearer ${user}`,'Content-Type':'application/json',...init.headers}})); }
  finally { globalThis.fetch=originalFetch; await new Promise(resolve=>server.close(resolve)); }
}

test('denuncias: hacen falta tres cuentas distintas con antigüedad, nunca la del autor, y repetir no falla',async()=> {
  const tables={catalog_books:[{id,title:'Libro',status:'published',owner_id:'autor',reviewed_at:'2026-01-01T00:00:00.000Z'}],catalog_blocks:[],catalog_reports:[]};
  await serve({database:database(tables)},async call=> {
    const report=user=>call(`/books/${id}/report`,user,{method:'POST',body:JSON.stringify({reason:'Contenido obsceno'})});
    assert.deepEqual((await (await call('/books','autor')).json()).map(b=>[b.mine,'owner_id' in b]),[[true,false]]);
    assert.equal((await (await call('/books','lector1')).json())[0].mine,false);
    const own=await report('autor');
    assert.equal(own.status,422);
    assert.match((await own.json()).message,/retíralo/);
    for (const user of ['nuevo1','nuevo2','nuevo3']) assert.equal((await report(user)).status,204);
    assert.equal(tables.catalog_books[0].status,'published'); // cuentas de hoy: solo para el operador
    assert.deepEqual(tables.catalog_reports.map(r=>r.trusted),[false,false,false]);
    assert.equal((await report('lector1')).status,204);
    assert.equal((await report('lector1')).status,204);
    assert.equal(tables.catalog_reports.length,4);
    assert.equal((await report('lector2')).status,204);
    assert.equal(tables.catalog_books[0].status,'published');
    assert.equal((await report('lector3')).status,204);
    assert.equal(tables.catalog_books[0].status,'review');
  });
});

test('transferencias: una por cuenta y tres a la vez en total',async()=> {
  const tables={catalog_books:[{id,title:'Libro',status:'published',owner_id:'autor',pdf_key:'k',pdf_bytes:8,sha256:'0'.repeat(64)}],catalog_blocks:[]};
  let release; const held=new Promise(resolve=>{release=resolve;});
  const storage={from:()=>({download:async()=>{ await held; return {data:new Blob(['%PDF-bad']),error:null}; }})};
  await serve({database:database(tables),storage},async call=> {
    const pending=['a','b','c'].map(user=>call(`/books/${id}/pdf`,user));
    await new Promise(resolve=>setTimeout(resolve,100));
    assert.equal((await call(`/books/${id}/pdf`,'a')).status,429);
    const busy=await call(`/books/${id}/pdf`,'d');
    assert.equal(busy.status,503);
    assert.match((await busy.json()).message,/transferencias/);
    release();
    assert.deepEqual((await Promise.all(pending)).map(r=>r.status),[503,503,503]); // hash incorrecto
    // Los huecos se liberan: ahora falla por el hash, no por falta de hueco.
    assert.doesNotMatch((await (await call(`/books/${id}/pdf`,'d')).json()).message,/transferencias/);
  });
});

test('cupo agotado llega como mensaje claro, no como «intenta más tarde»',()=> {
  assert.throws(()=>unwrap({error:{message:'upload_quota_exceeded'}}),e=>e instanceof PolicyError && e.status===429 && /5 al día/.test(e.message));
  assert.throws(()=>unwrap({error:{message:'otro fallo'}}),e=>!(e instanceof PolicyError));
});

test('bloqueos: quien bloquea nunca recibe el id de la otra cuenta, y aun así puede desbloquear',async()=> {
  const tables={catalog_books:[{id,title:'Libro',status:'published',owner_id:'autor-secreto'}],catalog_blocks:[]};
  await serve({database:database(tables)},async call=> {
    assert.equal((await call(`/books/${id}/block`,'lector',{method:'POST'})).status,204);
    const body=await (await call('/me/blocks','lector')).text();
    assert.doesNotMatch(body,/autor-secreto/);
    const [block]=JSON.parse(body);
    assert.equal(block.label,'Libro');
    assert.equal((await call(`/me/blocks/${block.id}`,'otro',{method:'DELETE'})).status,204);
    assert.equal(tables.catalog_blocks.length,1); // el valor de otra persona no desbloquea nada
    assert.equal((await call(`/me/blocks/${block.id}`,'lector',{method:'DELETE'})).status,204);
    assert.equal(tables.catalog_blocks.length,0);
  });
});

test('una transferencia que gotea se corta tras un minuto; una lenta pero constante sigue',t=> {
  t.mock.timers.enable({apis:['setInterval','Date']});
  let cut=0;
  const stopSlow=watchRate(()=>1000,()=>cut++);
  const stopSteady=watchRate(()=>(Date.now()/1000)*80*1024,()=>{ throw new Error('no debía cortarse'); });
  t.mock.timers.tick(55_000); assert.equal(cut,0);
  t.mock.timers.tick(10_000); assert.ok(cut>=1);
  stopSlow(); stopSteady();
});

test('aviso de revisión: escapa el título y nunca hace fallar al que llama',async()=> {
  const {notifyReview}=await import('../src/platform.mjs');
  const sent=[]; let reply=async()=>new Response('{}',{status:200});
  globalThis.fetch=async(url,options)=>{ sent.push(JSON.parse(options.body)); return reply(); };
  Object.assign(process.env,{RESEND_API_KEY:'re_prueba',EMAIL_FROM:'Narra <narra@example.com>',REVIEW_EMAIL:'operador@example.com'});
  try {
    await notifyReview({id,title:'<img src=x onerror=alert(1)>'},'pasó las comprobaciones automáticas');
    assert.equal(sent[0].to,'operador@example.com');
    assert.doesNotMatch(sent[0].html,/<img/);
    reply=async()=>{ throw new Error('sin red'); };
    await notifyReview({id,title:'x'},'y');
    reply=async()=>new Response('{}',{status:403});
    await notifyReview({id,title:'x'},'y');
    delete process.env.RESEND_API_KEY;
    await notifyReview({id,title:'sin aviso'},'z');
    assert.equal(sent.length,3);
  } finally {
    globalThis.fetch=originalFetch;
    for (const key of ['RESEND_API_KEY','EMAIL_FROM','REVIEW_EMAIL']) delete process.env[key];
  }
});
