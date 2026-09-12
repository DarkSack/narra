export type VoiceEngine = 'device' | 'cloud';

export type BookStatus =
  | 'extracting' // reading text out of the PDF
  | 'converting' // running text-to-speech
  | 'ready' // playable
  | 'error';

export interface CloudVoice {
  id: string;
  label: string;
  language: string;
}

export interface Book {
  id: string;
  title: string;
  sourceFileName: string;
  createdAt: number;
  engine: VoiceEngine;
  voiceId: string | null;
  status: BookStatus;
  errorMessage: string | null;
  totalChunks: number;
  readyChunks: number;
  /** Directory (under document storage) holding this book's text + audio chunks. */
  dir: string;
  /** Playback progress. */
  lastChunkIndex: number;
  lastPositionSeconds: number;
  durationSeconds: number;
}

export interface TextChunk {
  index: number;
  text: string;
}
