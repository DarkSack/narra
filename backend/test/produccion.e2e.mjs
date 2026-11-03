// Prueba de humo contra el catálogo desplegado (ver backend/README.md): usuario confirmado de usar y
// tirar, subida real, ClamAV + reconstrucción + moderación reales, aprobación por el panel /admin
// (entra con ADMIN_PASSWORD), descarga íntegra y,
// al final, DELETE /me, que borra el libro, sus archivos y el usuario.
import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
const API = process.env.CATALOG_API ?? 'http://api:8080';
const { INSFORGE_URL, INSFORGE_API_KEY } = process.env;
const email = `narra-e2e-${Date.now()}@example.com`;
const password = randomBytes(18).toString('base64url');
const read = async response => { const text = await response.text(); try { return JSON.parse(text); } catch { return text; } };

function fixture() {
  const text='BT /F1 22 Tf 72 700 Td (Prueba de humo de Narra) Tj 0 -40 Td /F1 12 Tf (Una familia viaja al bosque y disfruta de una tarde tranquila.) Tj ET';
  const objects=[
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${text.length} >>\nstream\n${text}\nendstream`,
  ];
  let pdf='%PDF-1.7\n';const offsets=[0];
  objects.forEach((object,index)=>{offsets.push(Buffer.byteLength(pdf));pdf+=`${index+1} 0 obj\n${object}\nendobj\n`;});
  const xref=Buffer.byteLength(pdf);
  pdf+=`xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(offset=>String(offset).padStart(10,'0')+' 00000 n \n').join('')}trailer\n<< /Root 1 0 R /Size 6 >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf);
}

const signUp = await fetch(`${INSFORGE_URL}/api/auth/users?client_type=mobile`, {
  method:'POST', headers:{ Authorization:`Bearer ${INSFORGE_API_KEY}`, 'Content-Type':'application/json' },
  body: JSON.stringify({ email, password, autoConfirm:true }),
});
assert.ok(signUp.ok, `alta del usuario de prueba: ${signUp.status}`);
const session = await read(await fetch(`${INSFORGE_URL}/api/auth/sessions?client_type=mobile`, {
  method:'POST', headers:{ 'Content-Type':'application/json' }, body: JSON.stringify({ email, password }),
}));
assert.ok(session.accessToken, 'sin sesión: InsForge no confirmó al usuario de prueba');
const auth = { Authorization: `Bearer ${session.accessToken}` };
try {
  const upload = await fetch(`${API}/books?filename=libro.pdf&title=${encodeURIComponent('Prueba de humo de Narra')}&author=Narra&rights=true&consent=true`, {
    method:'POST', headers:{ ...auth, 'Content-Type':'application/pdf' }, body: fixture(),
  });
  const created = await read(upload);
  assert.equal(upload.status, 202, JSON.stringify(created));
  let book;
  for (const started = Date.now(); Date.now()-started < 15*60_000; await new Promise(resolve => setTimeout(resolve, 10_000))) {
    book = (await read(await fetch(`${API}/me/books`, { headers:auth }))).find(row => row.id === created.id);
    if (!['uploading','queued','processing'].includes(book?.status)) break;
  }
  assert.equal(book?.status, 'review', book?.reason);
  const entered = await fetch(`${API}/admin/login`, {
    method:'POST', headers:{ 'Content-Type':'application/json', 'X-Narra-Admin':'1' }, body: JSON.stringify({ password: process.env.ADMIN_PASSWORD }),
  });
  assert.equal(entered.status, 204, 'no se pudo entrar al panel');
  const admin = { Cookie: entered.headers.get('set-cookie').split(';')[0], 'X-Narra-Admin':'1' };
  assert.ok((await read(await fetch(`${API}/admin/queue`, { headers:admin }))).pending?.some(row => row.id === book.id), 'el libro no sale en el panel');
  const approved = await fetch(`${API}/admin/books/${book.id}/approve`, { method:'POST', headers:admin });
  assert.equal(approved.status, 204, JSON.stringify(await read(approved)));
  const listed = (await read(await fetch(`${API}/books`, { headers:auth }))).find(row => row.id === book.id);
  assert.equal(listed?.mine, true);
  assert.equal('owner_id' in listed, false);
  const own = await fetch(`${API}/books/${book.id}/report`, {
    method:'POST', headers:{ ...auth, 'Content-Type':'application/json' }, body: JSON.stringify({ reason:'prueba' }),
  });
  assert.equal(own.status, 422);
  const download = await fetch(`${API}/books/${book.id}/pdf`, { headers:auth });
  assert.equal(download.status, 200);
  const bytes = Buffer.from(await download.arrayBuffer());
  assert.equal(createHash('sha256').update(bytes).digest('hex'), download.headers.get('x-pdf-sha256'));
  assert.equal(bytes.subarray(0,5).toString(), '%PDF-');
  console.log(`PASS: subida, comprobaciones reales, aprobación en el panel y descarga íntegra (${bytes.length} bytes)`);
} finally {
  const gone = await fetch(`${API}/me`, { method:'DELETE', headers:auth });
  console.log(gone.status === 204 ? 'Cuenta de prueba y su libro borrados' : `OJO: no se borró la cuenta de prueba ${email} (${gone.status})`);
}
