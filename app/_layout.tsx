import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { PdfExtractorProvider } from '../src/context/PdfExtractorContext';

export default function RootLayout() {
  return (
    <PdfExtractorProvider>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerTitleAlign: 'center' }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="player/[id]" options={{ title: 'Reproduciendo' }} />
      </Stack>
    </PdfExtractorProvider>
  );
}
