import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import { useRouter } from 'expo-router';
import type { Voice } from 'expo-speech';

import { insertBook, updateBook } from '../../src/db/database';
import { usePdfExtractor } from '../../src/context/PdfExtractorContext';
import { convertBookToAudio } from '../../src/lib/convertBook';
import { CLOUD_VOICES, DEFAULT_CLOUD_VOICE_ID } from '../../src/lib/tts/cloudVoices';
import { getDeviceVoices } from '../../src/lib/tts/deviceVoices';
import type { Book, VoiceEngine } from '../../src/types/book';

type Stage = 'idle' | 'extracting' | 'converting' | 'done' | 'error';

function newBookId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function UploadScreen() {
  const router = useRouter();
  const extractor = usePdfExtractor();

  const [pickedFile, setPickedFile] = useState<DocumentPicker.DocumentPickerAsset | null>(null);
  const [title, setTitle] = useState('');
  const [engine, setEngine] = useState<VoiceEngine>('device');
  const [deviceVoices, setDeviceVoices] = useState<Voice[]>([]);
  const [deviceVoiceId, setDeviceVoiceId] = useState<string | null>(null);
  const [cloudVoiceId, setCloudVoiceId] = useState(DEFAULT_CLOUD_VOICE_ID);

  const [stage, setStage] = useState<Stage>('idle');
  const [progressLabel, setProgressLabel] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    getDeviceVoices()
      .then((voices) => {
        setDeviceVoices(voices);
        const spanish = voices.find((voice) => voice.language.startsWith('es'));
        setDeviceVoiceId((spanish ?? voices[0])?.identifier ?? null);
      })
      .catch(() => {});
  }, []);

  async function pickPdf() {
    const result = await DocumentPicker.getDocumentAsync({ type: 'application/pdf' });
    if (result.canceled || !result.assets?.[0]) return;
    const asset = result.assets[0];
    setPickedFile(asset);
    setTitle(asset.name.replace(/\.pdf$/i, ''));
    setStage('idle');
    setErrorMessage('');
  }

  async function startConversion() {
    if (!pickedFile) return;
    const bookId = newBookId();
    const voiceId = engine === 'device' ? deviceVoiceId : cloudVoiceId;

    const book: Book = {
      id: bookId,
      title: title.trim() || pickedFile.name,
      sourceFileName: pickedFile.name,
      createdAt: Date.now(),
      engine,
      voiceId,
      status: 'extracting',
      errorMessage: null,
      totalChunks: 0,
      readyChunks: 0,
      dir: bookId,
      lastChunkIndex: 0,
      lastPositionSeconds: 0,
      durationSeconds: 0,
    };

    try {
      setStage('extracting');
      setErrorMessage('');
      setProgressLabel('Leyendo PDF…');
      await insertBook(book);

      const pages = await extractor.extractText(pickedFile.uri, (page, totalPages) => {
        setProgressLabel(`Leyendo página ${page} de ${totalPages}…`);
      });

      setStage('converting');
      setProgressLabel(engine === 'cloud' ? 'Generando audio en la nube…' : 'Preparando lectura…');

      await convertBookToAudio(bookId, pages, engine, voiceId, (progress) => {
        if (progress.phase === 'synthesizing') {
          setProgressLabel(`Generando audio ${progress.readyChunks}/${progress.totalChunks}…`);
        }
      });

      setStage('done');
      setPickedFile(null);
      setTitle('');
      Alert.alert('¡Listo!', 'Tu audiolibro se agregó a la biblioteca.', [
        { text: 'Ver biblioteca', onPress: () => router.push('/') },
        { text: 'Ok' },
      ]);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setStage('error');
      setErrorMessage(message);
      await updateBook(bookId, { status: 'error', errorMessage: message }).catch(() => {});
    }
  }

  const busy = stage === 'extracting' || stage === 'converting';

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Nuevo audiolibro</Text>

      <Pressable style={styles.pickButton} onPress={pickPdf} disabled={busy}>
        <Text style={styles.pickButtonText}>{pickedFile ? '📄 ' + pickedFile.name : '📄 Elegir PDF'}</Text>
      </Pressable>

      {pickedFile && (
        <>
          <Text style={styles.label}>Título</Text>
          <TextInput
            value={title}
            onChangeText={setTitle}
            editable={!busy}
            style={styles.input}
            placeholder="Título del audiolibro"
          />

          <Text style={styles.label}>Voz</Text>
          <View style={styles.segmented}>
            <SegmentButton label="📱 Dispositivo (gratis)" active={engine === 'device'} disabled={busy} onPress={() => setEngine('device')} />
            <SegmentButton label="☁️ Nube (mejor calidad)" active={engine === 'cloud'} disabled={busy} onPress={() => setEngine('cloud')} />
          </View>

          {engine === 'device' ? (
            <VoicePicker
              options={deviceVoices.map((voice) => ({ id: voice.identifier, label: `${voice.name} (${voice.language})` }))}
              selectedId={deviceVoiceId}
              onSelect={setDeviceVoiceId}
              disabled={busy}
              emptyLabel="Cargando voces del dispositivo…"
            />
          ) : (
            <VoicePicker
              options={CLOUD_VOICES.map((voice) => ({ id: voice.id, label: voice.label }))}
              selectedId={cloudVoiceId}
              onSelect={setCloudVoiceId}
              disabled={busy}
            />
          )}

          {engine === 'cloud' && (
            <Text style={styles.hint}>La voz en la nube necesita internet solo durante la conversión; luego el audiolibro se reproduce sin conexión.</Text>
          )}

          <Pressable
            style={[styles.convertButton, busy && styles.convertButtonDisabled]}
            onPress={startConversion}
            disabled={busy || !pickedFile}
          >
            {busy ? <ActivityIndicator color="#fff" /> : <Text style={styles.convertButtonText}>Convertir a audiolibro</Text>}
          </Pressable>

          {busy && progressLabel ? <Text style={styles.progress}>{progressLabel}</Text> : null}
          {stage === 'error' && errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}
        </>
      )}
    </ScrollView>
  );
}

function SegmentButton({
  label,
  active,
  disabled,
  onPress,
}: {
  label: string;
  active: boolean;
  disabled: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      style={[styles.segmentButton, active && styles.segmentButtonActive]}
      onPress={onPress}
      disabled={disabled}
    >
      <Text style={[styles.segmentButtonText, active && styles.segmentButtonTextActive]}>{label}</Text>
    </Pressable>
  );
}

function VoicePicker({
  options,
  selectedId,
  onSelect,
  disabled,
  emptyLabel,
}: {
  options: { id: string; label: string }[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  disabled: boolean;
  emptyLabel?: string;
}) {
  if (options.length === 0) {
    return <Text style={styles.hint}>{emptyLabel ?? 'No hay voces disponibles.'}</Text>;
  }
  return (
    <View style={styles.voiceList}>
      {options.map((option) => (
        <Pressable
          key={option.id}
          style={[styles.voiceChip, selectedId === option.id && styles.voiceChipActive]}
          onPress={() => onSelect(option.id)}
          disabled={disabled}
        >
          <Text style={[styles.voiceChipText, selectedId === option.id && styles.voiceChipTextActive]}>
            {option.label}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 12 },
  heading: { fontSize: 22, fontWeight: '700', marginBottom: 4 },
  pickButton: {
    borderWidth: 1.5,
    borderColor: '#5B4FE0',
    borderStyle: 'dashed',
    borderRadius: 14,
    padding: 20,
    alignItems: 'center',
  },
  pickButtonText: { color: '#5B4FE0', fontWeight: '600' },
  label: { fontSize: 14, fontWeight: '600', marginTop: 8, color: '#333' },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 10,
    padding: 12,
    fontSize: 15,
  },
  segmented: { flexDirection: 'row', gap: 8 },
  segmentButton: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#ddd',
    alignItems: 'center',
  },
  segmentButtonActive: { backgroundColor: '#5B4FE0', borderColor: '#5B4FE0' },
  segmentButtonText: { fontSize: 13, color: '#333' },
  segmentButtonTextActive: { color: '#fff', fontWeight: '600' },
  voiceList: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  voiceChip: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  voiceChipActive: { backgroundColor: '#EDE9FE', borderColor: '#5B4FE0' },
  voiceChipText: { fontSize: 13, color: '#333' },
  voiceChipTextActive: { color: '#5B4FE0', fontWeight: '600' },
  hint: { fontSize: 12, color: '#888' },
  convertButton: {
    backgroundColor: '#5B4FE0',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  convertButtonDisabled: { opacity: 0.7 },
  convertButtonText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  progress: { textAlign: 'center', color: '#555' },
  error: { textAlign: 'center', color: '#C0392B' },
});
