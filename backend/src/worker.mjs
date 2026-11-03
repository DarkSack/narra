import { mkdtemp,readFile,rm,stat } from 'node:fs/promises';
import { createWriteStream,openAsBlob } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Readable,Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { platform,unwrap,patchBook,removeObject,sha256,notifyReview } from './platform.mjs';
import { PolicyError,MAX_BYTES,MAX_PAGES } from './policy.mjs';
import { scan } from './antivirus.mjs';
import { extractBundle } from './bundle.mjs';
import { moderate } from './moderation.mjs';
import { cleanup } from './maintenance.mjs';

const MODERATION_LANES=3;

export async function processBook(client,book) {
  const deadline=Date.now()+2*3600_000;
  const dir = await mkdtemp(join(tmpdir(),'narra-review-'));
  const safeKey = `${book.id}/book.pdf`;
  try {
    const original = unwrap(await client.storage.from('quarantine').download(book.source_key));
    if (original.size > MAX_BYTES || original.size !== book.original_bytes) throw new PolicyError('Tamaño del PDF incorrecto');
    const source = join(dir,'source.pdf');
    await pipeline(Readable.fromWeb(original.stream()),createWriteStream(source,{mode:0o600}));
    await scan(source);
    await moderate(`${book.title}\n${book.author}\n${book.description}`);
    const response = await fetch(`${process.env.RENDERER_URL ?? 'http://renderer:8090'}/render`,{
      method:'POST',headers:{'Content-Type':'application/pdf'},body:await openAsBlob(source),signal:AbortSignal.timeout(1800_000),
    });
    if (response.status === 422) throw new PolicyError('PDF protegido, dañado, activo o fuera de los límites');
    if (!response.ok) throw new Error('Reconstrucción no disponible');
    let bytes=0;
    const guard = new Transform({transform(chunk,_,done){ bytes+=chunk.length; done(bytes>250*1024*1024 ? new Error('Revisión demasiado grande') : null,chunk); }});
    const archive = join(dir,'bundle.zip');
    await pipeline(Readable.fromWeb(response.body),guard,createWriteStream(archive));
    const names = await extractBundle(archive,dir);
    const manifest = JSON.parse(await readFile(join(dir,'manifest.json'),'utf8'));
    if (!Number.isInteger(manifest.pages) || manifest.pages<1 || manifest.pages>MAX_PAGES || names.size!==manifest.pages*2+2) throw new Error('Revisión incompleta');
    // Varias páginas a la vez; el primer fallo detiene las demás y se espera a las que van en curso.
    let next=0, failure;
    const lane=async()=> {
      while (!failure && next<manifest.pages) {
        const page=next++;
        try {
          if(Date.now()>deadline) throw new Error('La revisión superó su tiempo máximo');
          const text = await readFile(join(dir,`page-${page}.txt`),'utf8');
          if (text.length>50_000) throw new PolicyError('Texto de página fuera de los límites');
          const image = await readFile(join(dir,`page-${page}.jpg`));
          if (image.length>10*1024*1024) throw new PolicyError('Página fuera de los límites');
          await moderate(text,image);
        } catch(error) { failure ??= error; }
      }
    };
    await Promise.all(Array.from({length:MODERATION_LANES},lane));
    if (failure) throw failure;
    const file = join(dir,'book.pdf');
    if ((await stat(file)).size>MAX_BYTES) throw new PolicyError('La copia revisada supera 100 MB');
    await scan(file);
    const safe = await openAsBlob(file,{type:'application/pdf'});
    const active = unwrap(await client.database.from('catalog_books').select('id').eq('id',book.id).eq('status','processing').limit(1));
    if (!active.length) return; // El propietario pudo retirar el libro mientras se revisaba.
    unwrap(await client.storage.from('books').upload(safeKey,safe));
    const updated = await patchBook(client,book.id,{
      status:'review',reason:'Comprobaciones automáticas terminadas; pendiente de revisión humana',
      checks_passed:true,checked_at:new Date().toISOString(),pdf_key:safeKey,pdf_bytes:safe.size,
      sha256:await sha256(safe),pages:manifest.pages,
    },'processing');
    if (!updated.length) await removeObject(client,'books',safeKey);
    else await notifyReview(book,'pasó las comprobaciones automáticas');
  } catch(error) {
    console.error('catalog_check_failed',book.id,error.name);
    await removeObject(client,'books',safeKey).catch(()=>{});
    await patchBook(client,book.id,{status:error instanceof PolicyError ? 'rejected':'blocked',checks_passed:false,
      reason:error instanceof PolicyError ? error.message:'No se completó una comprobación de seguridad; el libro permanece bloqueado'},'processing');
  } finally {
    // El original y las imágenes/textos de revisión no se conservan en la nube.
    if (book.source_key) {
      try {
        await removeObject(client,'quarantine',book.source_key);
        await patchBook(client,book.id,{source_key:null});
      } catch { console.error('catalog_cleanup_pending',book.id); }
    }
    await rm(dir,{recursive:true,force:true});
  }
}

if (process.env.NODE_ENV !== 'test') {
  const client=platform();
  let lastCleanup=0;
  for (;;) {
    try {
      if(Date.now()-lastCleanup>300_000) { await cleanup(client); lastCleanup=Date.now(); }
      const jobs=unwrap(await client.database.rpc('claim_catalog_job'));
      if (jobs.length) await processBook(client,jobs[0]);
      else await new Promise(resolve=>setTimeout(resolve,5000));
    } catch(error) { console.error('catalog_worker_unavailable',error.name); await new Promise(resolve=>setTimeout(resolve,15000)); }
  }
}
