/**
 * Splits long text into speech-sized chunks, breaking on sentence boundaries
 * (falling back to spaces) so each chunk stays under maxLength characters.
 */
export function chunkText(text: string, maxLength: number): string[] {
  const normalized = text.replace(/\s+/g, ' ').trim();
  if (!normalized) return [];

  const sentences = normalized.match(/[^.!?]+[.!?]*\s*/g) ?? [normalized];
  const chunks: string[] = [];
  let current = '';

  for (const sentence of sentences) {
    const trimmed = sentence.trim();
    if (!trimmed) continue;

    if (trimmed.length > maxLength) {
      if (current) {
        chunks.push(current.trim());
        current = '';
      }
      for (const piece of splitLongSentence(trimmed, maxLength)) {
        chunks.push(piece);
      }
      continue;
    }

    if ((current + ' ' + trimmed).trim().length > maxLength) {
      if (current) chunks.push(current.trim());
      current = trimmed;
    } else {
      current = (current + ' ' + trimmed).trim();
    }
  }

  if (current) chunks.push(current.trim());
  return chunks.filter((chunk) => chunk.length > 0);
}

function splitLongSentence(sentence: string, maxLength: number): string[] {
  const words = sentence.split(' ');
  const pieces: string[] = [];
  let current = '';

  for (const word of words) {
    if ((current + ' ' + word).trim().length > maxLength) {
      if (current) pieces.push(current.trim());
      current = word;
    } else {
      current = (current + ' ' + word).trim();
    }
  }
  if (current) pieces.push(current.trim());
  return pieces;
}
