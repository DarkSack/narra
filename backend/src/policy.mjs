import { Transform } from 'node:stream';
export const MAX_BYTES = 100 * 1024 * 1024;
export const MAX_PAGES = 500;
export class PolicyError extends Error { constructor(message, status = 422) { super(message); this.status = status; } }
export function metadata(input) {
  const result = {};
  for (const [key, max] of [['title',160],['author',160],['description',1000]]) {
    const value = input[key] ?? '';
    if (typeof value !== 'string' || value.length > max || /[\x00-\x1f\x7f]/.test(value)) throw new PolicyError('Metadatos no válidos');
    result[key] = value.trim();
  }
  if (!result.title) throw new PolicyError('Escribe el título del libro');
  if (input.rights !== 'true' || input.consent !== 'true') throw new PolicyError('Confirma los derechos y la revisión del PDF');
  return result;
}
export function validateUploadHeaders(contentType, name) {
  if (contentType?.split(';')[0].trim().toLowerCase() !== 'application/pdf' ||
      typeof name !== 'string' || !/^[^/\\:\x00-\x1f]{1,200}\.pdf$/i.test(name)) {
    throw new PolicyError('Solo se permite un archivo PDF', 415);
  }
}
export class PdfStream extends Transform {
  bytes = 0; prefix = Buffer.alloc(0);
  _transform(chunk, _, done) {
    this.bytes += chunk.length;
    if (this.bytes > MAX_BYTES) return done(new PolicyError('El PDF supera 100 MB', 413));
    if (this.prefix.length < 5) this.prefix = Buffer.concat([this.prefix, chunk.subarray(0, 5-this.prefix.length)]);
    if (this.prefix.length === 5 && this.prefix.toString('ascii') !== '%PDF-') return done(new PolicyError('El archivo no es PDF', 415));
    done(null, chunk);
  }
  _flush(done) { done(this.prefix.length < 5 ? new PolicyError('PDF vacío o incompleto') : undefined); }
}
export function validateVerdict(value) {
  if (!value || Object.keys(value).sort().join(',') !== 'obscene,uncertain' || typeof value.obscene !== 'boolean' || typeof value.uncertain !== 'boolean') throw new Error('Moderación incompleta');
  if (value.obscene) throw new PolicyError('El libro contiene contenido sexual explícito u obsceno');
  if (value.uncertain) throw new Error('La moderación necesita revisión; no se puede publicar');
}
