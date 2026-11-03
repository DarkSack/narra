import test from 'node:test';
import assert from 'node:assert/strict';
process.env.NODE_ENV='test';
process.env.INSFORGE_URL='https://auth.invalid';
const {createApp}=await import('../src/server.mjs');
const originalFetch=globalThis.fetch;
const id='00000000-0000-4000-8000-000000000001';

test('API: auth obligatoria, pendiente invisible, descarga solo publicada e íntegra',async()=> {
  const rows=[{id,title:'Libro pendiente',status:'review',owner_id:'reader'}];
  const storage=new Map();
  let touched=false;
  function table(name){
    const filters=[];
    const q={select(){return q},eq(k,v){filters.push([k,v]);return q},order(){return q},limit(){return q},range(){return q},
      then(resolve){touched=true;return Promise.resolve({data:(name==='catalog_blocks' ? []:rows).filter(row=>filters.every(([k,v])=>row[k]===v)),error:null}).then(resolve)}};
    return q;
  }
  const client={database:{from:name=>table(name)},storage:{from:()=>({download:async key=>({data:storage.get(key),error:null})})}};
  const server=createApp(client).listen(0,'127.0.0.1');
  await new Promise(resolve=>server.once('listening',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  globalThis.fetch=async(url,options)=>String(url).startsWith('https://auth.invalid') ? new Response(JSON.stringify({user:{id:'reader',role:'authenticated'}}),{status:200}):originalFetch(url,options);
  try {
    assert.equal((await originalFetch(`${base}/books`)).status,401);
    assert.equal(touched,false);
    const headers={Authorization:'Bearer reader-token'};
    assert.deepEqual(await (await originalFetch(`${base}/books`,{headers})).json(),[]);
    assert.equal((await originalFetch(`${base}/books/${id}/pdf`,{headers})).status,404);
    assert.equal((await originalFetch(`${base}/me/books`,{headers})).status,200);
    rows[0].status='published';rows[0].pdf_key='safe/book.pdf';rows[0].pdf_bytes=8;rows[0].sha256='0'.repeat(64);
    storage.set('safe/book.pdf',new Blob(['%PDF-bad']));
    assert.equal((await originalFetch(`${base}/books/${id}/pdf`,{headers})).status,503);
    rows[0].status='rejected';
    assert.equal((await originalFetch(`${base}/books/${id}/pdf`,{headers})).status,404);
  } finally {
    globalThis.fetch=originalFetch;
    await new Promise(resolve=>server.close(resolve));
  }
});
