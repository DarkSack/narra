import * as SQLite from 'expo-sqlite';
import type { Book, BookStatus, VoiceEngine } from '../types/book';

const DB_NAME = 'audiobooks.db';

let dbPromise: ReturnType<typeof SQLite.openDatabaseAsync> | null = null;

function getDb() {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync(DB_NAME).then(async (db) => {
      await db.execAsync(`
        PRAGMA journal_mode = WAL;
        CREATE TABLE IF NOT EXISTS books (
          id TEXT PRIMARY KEY NOT NULL,
          title TEXT NOT NULL,
          sourceFileName TEXT NOT NULL,
          createdAt INTEGER NOT NULL,
          engine TEXT NOT NULL,
          voiceId TEXT,
          status TEXT NOT NULL,
          errorMessage TEXT,
          totalChunks INTEGER NOT NULL DEFAULT 0,
          readyChunks INTEGER NOT NULL DEFAULT 0,
          dir TEXT NOT NULL,
          lastChunkIndex INTEGER NOT NULL DEFAULT 0,
          lastPositionSeconds REAL NOT NULL DEFAULT 0,
          durationSeconds REAL NOT NULL DEFAULT 0
        );
      `);
      return db;
    });
  }
  return dbPromise;
}

type BookRow = {
  id: string;
  title: string;
  sourceFileName: string;
  createdAt: number;
  engine: string;
  voiceId: string | null;
  status: string;
  errorMessage: string | null;
  totalChunks: number;
  readyChunks: number;
  dir: string;
  lastChunkIndex: number;
  lastPositionSeconds: number;
  durationSeconds: number;
};

function rowToBook(row: BookRow): Book {
  return {
    id: row.id,
    title: row.title,
    sourceFileName: row.sourceFileName,
    createdAt: row.createdAt,
    engine: row.engine as VoiceEngine,
    voiceId: row.voiceId,
    status: row.status as BookStatus,
    errorMessage: row.errorMessage,
    totalChunks: row.totalChunks,
    readyChunks: row.readyChunks,
    dir: row.dir,
    lastChunkIndex: row.lastChunkIndex,
    lastPositionSeconds: row.lastPositionSeconds,
    durationSeconds: row.durationSeconds,
  };
}

export async function insertBook(book: Book): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    `INSERT INTO books
      (id, title, sourceFileName, createdAt, engine, voiceId, status, errorMessage, totalChunks, readyChunks, dir, lastChunkIndex, lastPositionSeconds, durationSeconds)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      book.id,
      book.title,
      book.sourceFileName,
      book.createdAt,
      book.engine,
      book.voiceId,
      book.status,
      book.errorMessage,
      book.totalChunks,
      book.readyChunks,
      book.dir,
      book.lastChunkIndex,
      book.lastPositionSeconds,
      book.durationSeconds,
    ]
  );
}

export async function updateBook(id: string, patch: Partial<Book>): Promise<void> {
  const entries = Object.entries(patch).filter(([key]) => key !== 'id');
  if (entries.length === 0) return;
  const db = await getDb();
  const setClause = entries.map(([key]) => `${key} = ?`).join(', ');
  const values = entries.map(([, value]) => (value === undefined ? null : value));
  await db.runAsync(`UPDATE books SET ${setClause} WHERE id = ?`, [...values, id]);
}

export async function deleteBook(id: string): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM books WHERE id = ?', [id]);
}

export async function getBook(id: string): Promise<Book | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<BookRow>('SELECT * FROM books WHERE id = ?', [id]);
  return row ? rowToBook(row) : null;
}

export async function listBooks(): Promise<Book[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<BookRow>('SELECT * FROM books ORDER BY createdAt DESC');
  return rows.map(rowToBook);
}
