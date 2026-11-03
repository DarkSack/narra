// Uso intencional de administrador; sin endpoint público de aprobación.
import { createHash } from 'node:crypto';
import { mkdtemp,writeFile,rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { platform,unwrap,patchBook,removeObject } from './platform.mjs';
import { scan } from './antivirus.mjs';
const [action,id,confirmation]=process.argv.slice(2);
if (action==='list') {
  const client=platform();
  const pending=unwrap(await client.database.from('catalog_books').select('id,title,reason').eq('status','review').order('created_at').limit(100));
  console.log(`Pendientes de revisión: ${pending.length}`);
  for (const book of pending) console.log(`  ${book.id}  ${book.title} — ${book.reason ?? ''}`);
  // Por debajo del umbral el libro sigue publicado: aquí se ven esas denuncias.
  const reports=unwrap(await client.database.from('catalog_reports').select('book_id,reason,trusted,created_at').order('created_at',{ascending:false}).limit(1000));
  const ids=[...new Set(reports.map(report=>report.book_id))];
  const published=ids.length ? unwrap(await client.database.from('catalog_books').select('id,title,reviewed_at').eq('status','published').in('id',ids)) : [];
  console.log('Publicados con denuncias desde su última aprobación:');
  for (const book of published) {
    const open=reports.filter(report=>report.book_id===book.id && Date.parse(report.created_at)>Date.parse(book.reviewed_at));
    if (open.length) console.log(`  ${book.id}  ${book.title} — ${open.length} (${open.filter(report=>report.trusted).length} de cuentas con antigüedad): ${open.map(report=>report.reason.slice(0,80)).join(' | ')}`);
  }
  process.exit(0);
}
if (!['approve','reject'].includes(action) || !/^[a-f0-9-]{36}$/i.test(id ?? '') || confirmation!=='--reviewed') throw new Error('Uso: npm run review -- list | approve|reject ID --reviewed. Revisa el libro completo antes de aprobar.');
const client=platform();
const [book]=unwrap(await client.database.from('catalog_books').select('*').eq('id',id).eq('status','review').limit(1));
if (!book) throw new Error('El libro no está pendiente de revisión');
if (action==='reject') {
  await patchBook(client,id,{status:'rejected',reason:'No aprobado en revisión humana',checks_passed:false},'review');
  if (book.pdf_key) await removeObject(client,'books',book.pdf_key);
} else {
  if (!book.checks_passed || !book.pdf_key || !book.checked_at) throw new Error('Faltan comprobaciones automáticas');
  const blob=unwrap(await client.storage.from('books').download(book.pdf_key));
  const bytes=Buffer.from(await blob.arrayBuffer());
  if (bytes.length!==book.pdf_bytes || createHash('sha256').update(bytes).digest('hex')!==book.sha256) throw new Error('Falló la integridad');
  const dir=await mkdtemp(join(tmpdir(),'narra-approval-'));
  try {
    const file=join(dir,'book.pdf'); await writeFile(file,bytes,{mode:0o600}); await scan(file);
    await patchBook(client,id,{status:'published',reviewed_at:new Date().toISOString(),reason:null},'review');
  } finally { await rm(dir,{recursive:true,force:true}); }
}
console.log(action==='approve' ? 'Libro publicado':'Libro rechazado');
