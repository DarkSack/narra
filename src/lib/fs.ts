import { Directory, File, Paths } from 'expo-file-system';

const LIBRARY_DIR_NAME = 'audiobooks';

export function libraryDir(): Directory {
  const dir = new Directory(Paths.document, LIBRARY_DIR_NAME);
  if (!dir.exists) dir.create({ intermediates: true });
  return dir;
}

export function bookDir(bookId: string): Directory {
  const dir = new Directory(libraryDir(), bookId);
  if (!dir.exists) dir.create({ intermediates: true });
  return dir;
}

export function chunkTextFile(dir: Directory, index: number): File {
  return new File(dir, `chunk-${String(index).padStart(5, '0')}.txt`);
}

export function chunkAudioFile(dir: Directory, index: number): File {
  return new File(dir, `chunk-${String(index).padStart(5, '0')}.mp3`);
}

export function deleteDir(dir: Directory): void {
  if (dir.exists) dir.delete();
}
