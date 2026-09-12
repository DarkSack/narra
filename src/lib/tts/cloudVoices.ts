import type { CloudVoice } from '../../types/book';

/**
 * Voices exposed by the free StreamElements text-to-speech endpoint
 * (Amazon Polly voices, no API key required). A curated subset covering
 * Spanish and English is enough for an audiobook app's voice picker.
 */
export const CLOUD_VOICES: CloudVoice[] = [
  { id: 'Lucia', label: 'Lucía (español, mujer)', language: 'es-ES' },
  { id: 'Enrique', label: 'Enrique (español, hombre)', language: 'es-ES' },
  { id: 'Mia', label: 'Mia (español latino, mujer)', language: 'es-MX' },
  { id: 'Miguel', label: 'Miguel (español latino, hombre)', language: 'es-US' },
  { id: 'Conchita', label: 'Conchita (español, mujer)', language: 'es-ES' },
  { id: 'Joanna', label: 'Joanna (inglés, mujer)', language: 'en-US' },
  { id: 'Matthew', label: 'Matthew (inglés, hombre)', language: 'en-US' },
  { id: 'Brian', label: 'Brian (inglés británico, hombre)', language: 'en-GB' },
  { id: 'Amy', label: 'Amy (inglés británico, mujer)', language: 'en-GB' },
];

export const DEFAULT_CLOUD_VOICE_ID = 'Lucia';
