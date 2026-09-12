import * as Speech from 'expo-speech';

/** Conservative chunk size that stays well under Speech.maxSpeechInputLength on all platforms. */
export const DEVICE_TTS_MAX_CHARS = 3000;

export function speakChunk(
  text: string,
  options: { voiceIdentifier?: string | null; onDone: () => void; onError: (message: string) => void }
) {
  Speech.speak(text, {
    voice: options.voiceIdentifier ?? undefined,
    onDone: options.onDone,
    onStopped: options.onDone,
    onError: (error) => options.onError(String(error?.message ?? error)),
  });
}

export function stopSpeaking() {
  Speech.stop();
}
