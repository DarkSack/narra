import { updateBook } from '../db/database';
import type { VoiceEngine } from '../types/book';
import { bookDir, chunkAudioFile, chunkTextFile } from './fs';
import { chunkText } from './chunkText';
import { CLOUD_TTS_MAX_CHARS, synthesizeToFile } from './tts/cloudTts';
import { DEVICE_TTS_MAX_CHARS } from './tts/deviceTts';

export interface ConvertProgress {
  phase: 'chunking' | 'synthesizing';
  readyChunks: number;
  totalChunks: number;
}

/**
 * Turns extracted PDF pages into a book's on-disk chunks.
 * - device engine: only writes text chunks; audio is synthesized live during playback.
 * - cloud engine: writes text chunks AND pre-renders each one to an mp3 via the free API.
 */
export async function convertBookToAudio(
  bookId: string,
  pages: string[],
  engine: VoiceEngine,
  voiceId: string | null,
  onProgress?: (progress: ConvertProgress) => void
): Promise<void> {
  const dir = bookDir(bookId);
  const fullText = pages.join('\n\n');
  const maxChars = engine === 'cloud' ? CLOUD_TTS_MAX_CHARS : DEVICE_TTS_MAX_CHARS;
  const chunks = chunkText(fullText, maxChars);

  if (chunks.length === 0) {
    await updateBook(bookId, { status: 'error', errorMessage: 'No se encontró texto legible en el PDF.' });
    return;
  }

  chunks.forEach((text, index) => {
    const file = chunkTextFile(dir, index);
    if (file.exists) file.delete();
    file.create();
    file.write(text);
  });

  await updateBook(bookId, {
    status: engine === 'cloud' ? 'converting' : 'ready',
    totalChunks: chunks.length,
    readyChunks: engine === 'cloud' ? 0 : chunks.length,
  });
  onProgress?.({ phase: 'chunking', readyChunks: 0, totalChunks: chunks.length });

  if (engine === 'device') {
    // Nothing to pre-render: the player synthesizes each chunk on the fly, for free, offline.
    return;
  }

  for (let index = 0; index < chunks.length; index++) {
    try {
      await synthesizeToFile(chunks[index], voiceId ?? 'Lucia', chunkAudioFile(dir, index));
    } catch (error) {
      await updateBook(bookId, {
        status: 'error',
        errorMessage: error instanceof Error ? error.message : String(error),
      });
      return;
    }

    const readyChunks = index + 1;
    await updateBook(bookId, { readyChunks });
    onProgress?.({ phase: 'synthesizing', readyChunks, totalChunks: chunks.length });
  }

  await updateBook(bookId, { status: 'ready' });
}
