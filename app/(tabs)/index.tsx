import { useCallback, useState } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';

import { deleteBook, listBooks } from '../../src/db/database';
import { bookDir, deleteDir } from '../../src/lib/fs';
import type { Book } from '../../src/types/book';

const STATUS_LABEL: Record<Book['status'], string> = {
  extracting: 'Leyendo PDF…',
  converting: 'Convirtiendo a audio…',
  ready: 'Listo',
  error: 'Error',
};

export default function LibraryScreen() {
  const router = useRouter();
  const [books, setBooks] = useState<Book[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(() => {
    listBooks()
      .then(setBooks)
      .finally(() => setLoading(false));
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
      const interval = setInterval(refresh, 1500);
      return () => clearInterval(interval);
    }, [refresh])
  );

  function confirmDelete(book: Book) {
    Alert.alert('Eliminar audiolibro', `¿Eliminar "${book.title}"? Esta acción no se puede deshacer.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Eliminar',
        style: 'destructive',
        onPress: async () => {
          deleteDir(bookDir(book.id));
          await deleteBook(book.id);
          refresh();
        },
      },
    ]);
  }

  if (!loading && books.length === 0) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyEmoji}>🎧</Text>
        <Text style={styles.emptyTitle}>Tu biblioteca está vacía</Text>
        <Text style={styles.emptySubtitle}>
          Ve a la pestaña "Subir PDF" para convertir tu primer libro en audiolibro.
        </Text>
      </View>
    );
  }

  return (
    <FlatList
      contentContainerStyle={styles.list}
      data={books}
      keyExtractor={(book) => book.id}
      renderItem={({ item }) => (
        <Pressable
          style={styles.card}
          onPress={() => router.push(`/player/${item.id}`)}
          onLongPress={() => confirmDelete(item)}
        >
          <View style={styles.cover}>
            <Text style={styles.coverEmoji}>🎧</Text>
          </View>
          <View style={styles.info}>
            <Text style={styles.title} numberOfLines={2}>
              {item.title}
            </Text>
            <Text style={styles.status}>
              {STATUS_LABEL[item.status]}
              {item.status === 'converting' && item.totalChunks > 0
                ? ` (${item.readyChunks}/${item.totalChunks})`
                : ''}
            </Text>
            {item.status === 'error' && item.errorMessage ? (
              <Text style={styles.error} numberOfLines={2}>
                {item.errorMessage}
              </Text>
            ) : null}
          </View>
        </Pressable>
      )}
    />
  );
}

const styles = StyleSheet.create({
  list: { padding: 16, gap: 12 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 8 },
  emptyEmoji: { fontSize: 48 },
  emptyTitle: { fontSize: 18, fontWeight: '600' },
  emptySubtitle: { textAlign: 'center', color: '#666' },
  card: {
    flexDirection: 'row',
    gap: 12,
    padding: 12,
    backgroundColor: '#F5F4FB',
    borderRadius: 14,
    alignItems: 'center',
  },
  cover: {
    width: 56,
    height: 56,
    borderRadius: 10,
    backgroundColor: '#5B4FE0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  coverEmoji: { fontSize: 26 },
  info: { flex: 1, gap: 2 },
  title: { fontSize: 16, fontWeight: '600' },
  status: { fontSize: 13, color: '#666' },
  error: { fontSize: 12, color: '#C0392B' },
});
