// Uso de administrador: este CLI y el panel /admin (con contraseña propia) comparten estas funciones.
import { createHash } from 'node:crypto';
import { mkdtemp,writeFile,rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { platform,unwrap,patchBook,removeObject } from './platform.mjs';
import { scan } from './antivirus.mjs';
import { PolicyError } from './policy.mjs';

/** Pendientes de revisión y publicados con denuncias desde su última aprobación, con sus denuncias. */
export async function reviewQueue(client) {
  const pending=unwrap(await client.database.from('catalog_books').select('id,title,author,description,reason,pages,pdf_bytes,created_at').eq('status','review').order('created_at').limit(100));
  // Por debajo del umbral el libro sigue publicado: aquí se ven esas denuncias.
  const reports=unwrap(await client.database.from('catalog_reports').select('book_id,reason,trusted,created_at').order('created_at',{ascending:false}).limit(1000));
  const ids=[...new Set(reports.map(report=>report.book_id))];
  const published=ids.length ? unwrap(await client.database.from('catalog_books').select('id,title,author,reviewed_at').eq('status','published').in('id',ids)) : [];
  const of=book=>reports.filter(report=>report.book_id===book.id && !(Date.parse(report.created_at)<=Date.parse(book.reviewed_at)))
    .map(({reason,trusted,created_at})=>({reason,trusted,created_at}));
  return {
    pending: pending.map(book=>({...book,reports:of(book)})), // vuelto por denuncias: reviewed_at es null, salen todas
    reported: published.map(book=>({...book,reports:of(book)})).filter(book=>book.reports.length),
  };
}

export async function decide(client,action,id) {
  if (!['approve','reject'].includes(action) || !/^[a-f0-9-]{36}$/i.test(id ?? '')) throw new PolicyError('Acción o libro no válidos',404);
  const [book]=unwrap(await client.database.from('catalog_books').select('*').eq('id',id).eq('status','review').limit(1));
  if (!book) throw new PolicyError('El libro no está pendiente de revisión',409);
  if (action==='reject') {
    await patchBook(client,id,{status:'rejected',reason:'No aprobado en revisión humana',checks_passed:false},'review');
    if (book.pdf_key) await removeObject(client,'books',book.pdf_key);
    return;
  }
  if (!book.checks_passed || !book.pdf_key || !book.checked_at) throw new PolicyError('Faltan comprobaciones automáticas',409);
  const blob=unwrap(await client.storage.from('books').download(book.pdf_key));
  const bytes=Buffer.from(await blob.arrayBuffer());
  if (bytes.length!==book.pdf_bytes || createHash('sha256').update(bytes).digest('hex')!==book.sha256) throw new Error('Falló la integridad');
  const dir=await mkdtemp(join(tmpdir(),'narra-approval-'));
  try {
    const file=join(dir,'book.pdf'); await writeFile(file,bytes,{mode:0o600}); await scan(file);
    await patchBook(client,id,{status:'published',reviewed_at:new Date().toISOString(),reason:null},'review');
  } finally { await rm(dir,{recursive:true,force:true}); }
}

if (process.argv[1]===fileURLToPath(import.meta.url)) {
  const [action,id,confirmation]=process.argv.slice(2);
  const client=platform();
  if (action==='list') {
    const { pending,reported }=await reviewQueue(client);
    console.log(`Pendientes de revisión: ${pending.length}`);
    for (const book of pending) console.log(`  ${book.id}  ${book.title} — ${book.reason ?? ''}`);
    console.log('Publicados con denuncias desde su última aprobación:');
    for (const book of reported) console.log(`  ${book.id}  ${book.title} — ${book.reports.length} (${book.reports.filter(report=>report.trusted).length} de cuentas con antigüedad): ${book.reports.map(report=>report.reason.slice(0,80)).join(' | ')}`);
  } else {
    if (confirmation!=='--reviewed') throw new Error('Uso: npm run review -- list | approve|reject ID --reviewed. Revisa el libro completo antes de aprobar.');
    await decide(client,action,id);
    console.log(action==='approve' ? 'Libro publicado':'Libro rechazado');
  }
}
