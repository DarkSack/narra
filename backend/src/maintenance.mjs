import { unwrap,patchBook,removeObject } from './platform.mjs';

/** Tras una caída nunca se publica un trabajo incompleto. */
export async function cleanup(client, now=Date.now()) {
  for (const [status,age] of [['uploading',3600_000],['processing',2*3600_000],['queued',24*3600_000]]) {
    const column=status==='processing' ? 'checked_at':'created_at';
    const stale=unwrap(await client.database.from('catalog_books').select('id').eq('status',status).lt(column,new Date(now-age).toISOString()).limit(100));
    for(const book of stale) await patchBook(client,book.id,{status:'blocked',checks_passed:false,reason:'La revisión no se completó a tiempo. Puedes volver a compartir el PDF'},status);
  }
  for(const status of ['blocked','rejected']) {
    const rows=unwrap(await client.database.from('catalog_books').select('id,source_key,pdf_key').eq('status',status).or('source_key.not.is.null,pdf_key.not.is.null').limit(100));
    for(const row of rows) {
      for(const [bucket,key] of [['quarantine',row.source_key],['books',row.pdf_key ?? `${row.id}/book.pdf`]]) {
        if(key) {
          await removeObject(client,bucket,key);
          await patchBook(client,row.id,{[bucket==='quarantine' ? 'source_key':'pdf_key']:null},status);
        }
      }
    }
  }
  for(const status of ['review','published']) {
    const rows=unwrap(await client.database.from('catalog_books').select('id,source_key').eq('status',status).not('source_key','is',null).limit(100));
    for(const row of rows) {
      await removeObject(client,'quarantine',row.source_key);
      await patchBook(client,row.id,{source_key:null},status);
    }
  }
}
