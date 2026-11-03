import { createAdminClient } from '@insforge/sdk';
import { createHash } from 'node:crypto';
import { PolicyError } from './policy.mjs';

export function platform() {
  const { INSFORGE_URL, INSFORGE_API_KEY } = process.env;
  if (!INSFORGE_URL?.startsWith('https://') || !INSFORGE_API_KEY) throw new Error('Backend sin configurar');
  return createAdminClient({ baseUrl: INSFORGE_URL, apiKey: INSFORGE_API_KEY });
}
// Excepciones de las funciones SQL que la persona debe entender, no un «intenta más tarde».
const KNOWN_ERRORS = {
  upload_quota_exceeded: ['Llegaste al límite de subidas: 5 al día, 3 en revisión a la vez y 100 publicaciones activas', 429],
  catalog_storage_quota_exceeded: ['El catálogo no admite más libros por ahora. Intenta más tarde', 503],
};
export function unwrap(result) {
  if (result.error) {
    const detail = `${result.error.message ?? ''} ${result.error.error ?? ''}`;
    const known = Object.keys(KNOWN_ERRORS).find(code => detail.includes(code));
    if (known) throw new PolicyError(...KNOWN_ERRORS[known]);
    throw new Error('Error del almacenamiento o base de datos', { cause: result.error });
  }
  return result.data;
}
export async function sha256(blob) {
  const hash = createHash('sha256');
  for await (const chunk of blob.stream()) hash.update(chunk);
  return hash.digest('hex');
}
const escapeHtml = text => String(text).replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);
/** Avisa al operador (REVIEW_EMAIL) de que un libro espera revisión. Nunca hace fallar al que llama. */
export async function notifyReview(book, why) {
  const { RESEND_API_KEY, EMAIL_FROM, REVIEW_EMAIL } = process.env;
  if (!RESEND_API_KEY || !EMAIL_FROM || !REVIEW_EMAIL) return;
  // Resend y no InsForge: el plan gratis de InsForge rechaza los correos propios (403).
  // Si falla el correo, el libro sigue en `review` y aparece en `npm run review -- list`.
  // Nunca lanza: en el worker, un error aquí llevaría al catch que borra la copia revisada.
  try {
    const response = await fetch('https://api.resend.com/emails', {
      method: 'POST', signal: AbortSignal.timeout(10_000),
      headers: { Authorization: `Bearer ${RESEND_API_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        from: EMAIL_FROM, to: REVIEW_EMAIL, subject: 'Narra: un libro espera revisión',
        html: `<p>«${escapeHtml(book.title)}»: ${escapeHtml(why)}.</p><p><code>npm run review -- list</code><br>${escapeHtml(book.id)}</p>`,
      }),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
  } catch (error) { console.error('catalog_notice_failed', book.id, error.message); }
}
export const PUBLIC_COLUMNS ='id,title,author,description,status,reason,original_bytes,pdf_bytes,sha256,pages,created_at';
export async function patchBook(client, id, values, expectedStatus) {
  let query = client.database.from('catalog_books').update(values).eq('id', id);
  if (expectedStatus) query = query.eq('status', expectedStatus);
  return unwrap(await query.select('id'));
}
export async function removeObject(client, bucket, key) {
  const result=await client.storage.from(bucket).remove(key);
  if(result.error?.statusCode===404) return;
  unwrap(result);
}
