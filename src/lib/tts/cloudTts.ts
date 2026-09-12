import type { File } from 'expo-file-system';

const ENDPOINT = 'https://api.streamelements.com/kappa/v2/speech';

/** Max characters accepted per request by the free StreamElements TTS endpoint. */
export const CLOUD_TTS_MAX_CHARS = 500;

export class CloudTtsError extends Error {}

/** Synthesizes one chunk of text to speech and writes the mp3 bytes to `destination`. */
export async function synthesizeToFile(text: string, voiceId: string, destination: File): Promise<void> {
  const url = `${ENDPOINT}?voice=${encodeURIComponent(voiceId)}&text=${encodeURIComponent(text)}`;

  const response = await fetch(url);
  if (!response.ok) {
    throw new CloudTtsError(`El servicio de voz en la nube respondió con error ${response.status}.`);
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('audio')) {
    throw new CloudTtsError('El servicio de voz en la nube no devolvió audio (puede estar saturado, intenta de nuevo).');
  }

  const buffer = await response.arrayBuffer();
  if (destination.exists) destination.delete();
  destination.create();
  destination.write(new Uint8Array(buffer));
}
