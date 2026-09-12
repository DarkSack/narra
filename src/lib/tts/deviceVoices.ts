import * as Speech from 'expo-speech';

export async function getDeviceVoices() {
  const voices = await Speech.getAvailableVoicesAsync();
  // Keep the list manageable: one voice per language, preferring higher quality.
  const byLanguage = new Map<string, Speech.Voice>();
  for (const voice of voices) {
    const existing = byLanguage.get(voice.language);
    if (!existing || (voice.quality ?? 0) > (existing.quality ?? 0)) {
      byLanguage.set(voice.language, voice);
    }
  }
  return Array.from(byLanguage.values()).sort((a, b) => a.language.localeCompare(b.language));
}
