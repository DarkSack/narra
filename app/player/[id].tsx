import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { useLocalSearchParams, useNavigation } from 'expo-router';
import { useAudioPlayer, useAudioPlayerStatus } from 'expo-audio';

import { getBook, updateBook } from '../../src/db/database';
import { bookDir, chunkAudioFile, chunkTextFile } from '../../src/lib/fs';
import { speakChunk, stopSpeaking } from '../../src/lib/tts/deviceTts';
import type { Book } from '../../src/types/book';

export default function PlayerScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const navigation = useNavigation();
  const [book, setBook] = useState<Book | null | undefined>(undefined);

  useEffect(() => {
    if (!id) return;
    getBook(id).then(setBook);
  }, [id]);

  useEffect(() => {
    if (book) navigation.setOptions({ title: book.title });
  }, [book, navigation]);

  if (book === undefined) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  if (!book) {
    return (
      <View style={styles.center}>
        <Text>No se encontró el audiolibro.</Text>
      </View>
    );
  }

  if (book.totalChunks === 0) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
        <Text style={styles.hint}>Todavía se está preparando este audiolibro…</Text>
      </View>
    );
  }

  return book.engine === 'cloud' ? <CloudPlayer book={book} /> : <DevicePlayer book={book} />;
}

// ---------- Cloud engine: real mp3 chunks played back-to-back with expo-audio ----------

function CloudPlayer({ book }: { book: Book }) {
  const dir = bookDir(book.id);
  const [chunkIndex, setChunkIndex] = useState(Math.min(book.lastChunkIndex, book.totalChunks - 1));
  const availableChunks = book.readyChunks;

  const player = useAudioPlayer(chunkAudioFile(dir, chunkIndex).uri);
  const status = useAudioPlayerStatus(player);

  const startedAtChunk = useRef(false);
  useEffect(() => {
    if (!startedAtChunk.current && book.lastPositionSeconds > 0) {
      startedAtChunk.current = true;
      player.seekTo(book.lastPositionSeconds).catch(() => {});
    }
  }, [player, book.lastPositionSeconds]);

  useEffect(() => {
    if (status.didJustFinish) {
      if (chunkIndex < availableChunks - 1) {
        setChunkIndex((index) => index + 1);
      }
    }
  }, [status.didJustFinish, chunkIndex, availableChunks]);

  useEffect(() => {
    player.replace(chunkAudioFile(dir, chunkIndex).uri);
    player.play();
  }, [chunkIndex]);

  useEffect(() => {
    const interval = setInterval(() => {
      updateBook(book.id, {
        lastChunkIndex: chunkIndex,
        lastPositionSeconds: status.currentTime ?? 0,
        durationSeconds: status.duration ?? 0,
      }).catch(() => {});
    }, 4000);
    return () => clearInterval(interval);
  }, [book.id, chunkIndex, status.currentTime, status.duration]);

  const progress = book.totalChunks > 0 ? (chunkIndex + 1) / book.totalChunks : 0;
  const stillConverting = availableChunks < book.totalChunks;

  return (
    <View style={styles.container}>
      <View style={styles.cover}>
        <Text style={styles.coverEmoji}>🎧</Text>
      </View>
      <Text style={styles.title}>{book.title}</Text>
      <Text style={styles.subtitle}>
        Fragmento {chunkIndex + 1} de {book.totalChunks}
        {stillConverting ? ` · generando ${availableChunks}/${book.totalChunks}` : ''}
      </Text>

      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
      </View>

      <Controls
        playing={status.playing}
        onPlayPause={() => (status.playing ? player.pause() : player.play())}
        onPrev={() => setChunkIndex((i) => Math.max(0, i - 1))}
        onNext={() => setChunkIndex((i) => Math.min(availableChunks - 1, i + 1))}
        canPrev={chunkIndex > 0}
        canNext={chunkIndex < availableChunks - 1}
        rate={player.playbackRate || 1}
        onRateChange={(rate) => {
          player.playbackRate = rate;
        }}
      />
    </View>
  );
}

// ---------- Device engine: re-synthesizes each chunk's saved text on the fly, for free ----------

function DevicePlayer({ book }: { book: Book }) {
  const dir = bookDir(book.id);
  const [chunkIndex, setChunkIndex] = useState(Math.min(book.lastChunkIndex, book.totalChunks - 1));
  const [playing, setPlaying] = useState(false);

  useEffect(() => {
    updateBook(book.id, { lastChunkIndex: chunkIndex }).catch(() => {});
  }, [book.id, chunkIndex]);

  useEffect(() => {
    return () => stopSpeaking();
  }, []);

  function playFromChunk(index: number) {
    stopSpeaking();
    const text = chunkTextFile(dir, index).textSync();
    setPlaying(true);
    speakChunk(text, {
      voiceIdentifier: book.voiceId,
      onDone: () => {
        setChunkIndex((current) => {
          const next = index + 1;
          if (next < book.totalChunks) {
            playFromChunk(next);
            return next;
          }
          setPlaying(false);
          return current;
        });
      },
      onError: () => setPlaying(false),
    });
  }

  function handlePlayPause() {
    if (playing) {
      stopSpeaking();
      setPlaying(false);
    } else {
      playFromChunk(chunkIndex);
    }
  }

  function goTo(index: number) {
    const clamped = Math.max(0, Math.min(book.totalChunks - 1, index));
    setChunkIndex(clamped);
    if (playing) playFromChunk(clamped);
  }

  const progress = book.totalChunks > 0 ? (chunkIndex + 1) / book.totalChunks : 0;

  return (
    <View style={styles.container}>
      <View style={styles.cover}>
        <Text style={styles.coverEmoji}>📱</Text>
      </View>
      <Text style={styles.title}>{book.title}</Text>
      <Text style={styles.subtitle}>
        Fragmento {chunkIndex + 1} de {book.totalChunks} · voz del dispositivo
      </Text>

      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
      </View>

      <Controls
        playing={playing}
        onPlayPause={handlePlayPause}
        onPrev={() => goTo(chunkIndex - 1)}
        onNext={() => goTo(chunkIndex + 1)}
        canPrev={chunkIndex > 0}
        canNext={chunkIndex < book.totalChunks - 1}
      />
      <Text style={styles.hint}>
        La voz del dispositivo se reproduce sin conexión. Al pausar, el fragmento actual vuelve a empezar al
        reanudar.
      </Text>
    </View>
  );
}

function Controls({
  playing,
  onPlayPause,
  onPrev,
  onNext,
  canPrev,
  canNext,
  rate,
  onRateChange,
}: {
  playing: boolean;
  onPlayPause: () => void;
  onPrev: () => void;
  onNext: () => void;
  canPrev: boolean;
  canNext: boolean;
  rate?: number;
  onRateChange?: (rate: number) => void;
}) {
  return (
    <View style={{ alignItems: 'center', gap: 20 }}>
      <View style={styles.controlsRow}>
        <Pressable onPress={onPrev} disabled={!canPrev} style={styles.sideButton}>
          <Text style={[styles.sideButtonText, !canPrev && styles.disabledText]}>⏮</Text>
        </Pressable>
        <Pressable onPress={onPlayPause} style={styles.playButton}>
          <Text style={styles.playButtonText}>{playing ? '⏸' : '▶️'}</Text>
        </Pressable>
        <Pressable onPress={onNext} disabled={!canNext} style={styles.sideButton}>
          <Text style={[styles.sideButtonText, !canNext && styles.disabledText]}>⏭</Text>
        </Pressable>
      </View>

      {onRateChange && (
        <View style={styles.rateRow}>
          {[0.75, 1, 1.25, 1.5, 2].map((value) => (
            <Pressable key={value} onPress={() => onRateChange(value)} style={[styles.rateChip, rate === value && styles.rateChipActive]}>
              <Text style={[styles.rateChipText, rate === value && styles.rateChipTextActive]}>{value}×</Text>
            </Pressable>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8 },
  container: { flex: 1, alignItems: 'center', padding: 24, gap: 16 },
  cover: {
    width: 180,
    height: 180,
    borderRadius: 20,
    backgroundColor: '#5B4FE0',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 16,
  },
  coverEmoji: { fontSize: 64 },
  title: { fontSize: 20, fontWeight: '700', textAlign: 'center' },
  subtitle: { fontSize: 13, color: '#666' },
  progressTrack: { width: '100%', height: 6, borderRadius: 3, backgroundColor: '#eee', overflow: 'hidden' },
  progressFill: { height: '100%', backgroundColor: '#5B4FE0' },
  controlsRow: { flexDirection: 'row', alignItems: 'center', gap: 28, marginTop: 8 },
  sideButton: { padding: 10 },
  sideButtonText: { fontSize: 26 },
  disabledText: { opacity: 0.3 },
  playButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#5B4FE0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  playButtonText: { fontSize: 28, color: '#fff' },
  rateRow: { flexDirection: 'row', gap: 8 },
  rateChip: { paddingVertical: 6, paddingHorizontal: 10, borderRadius: 16, borderWidth: 1, borderColor: '#ddd' },
  rateChipActive: { backgroundColor: '#EDE9FE', borderColor: '#5B4FE0' },
  rateChipText: { fontSize: 12, color: '#333' },
  rateChipTextActive: { color: '#5B4FE0', fontWeight: '700' },
  hint: { fontSize: 12, color: '#888', textAlign: 'center' },
});
