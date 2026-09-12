# PDF Audiolibros

App de Android (Expo / React Native) para convertir PDFs en audiolibros y guardarlos en una biblioteca local.

## Cómo funciona

1. **Subir PDF** — se elige un archivo con `expo-document-picker` y su texto se extrae en el dispositivo usando `pdf.js` dentro de un `WebView` oculto (sin backend, sin subir el PDF a ningún servidor).
2. **Elegir voz** — al convertir, se elige uno de dos motores:
   - **Dispositivo** (`expo-speech`): 100% gratis y offline. No genera archivos de audio; el reproductor vuelve a "leer" el texto guardado cada vez que reproduces ese fragmento.
   - **Nube** (API gratuita de StreamElements, sin API key): genera archivos `mp3` reales por cada fragmento de texto y los guarda en el dispositivo, así que después de convertir se reproducen offline. Requiere internet solo durante la conversión.
3. **Biblioteca** — cada audiolibro creado queda guardado en SQLite (`expo-sqlite`) con su progreso de lectura, y los archivos de texto/audio viven en `FileSystem.documentDirectory/audiobooks/<id>/`.
4. **Reproductor** — controles de play/pause, siguiente/anterior fragmento y (en modo nube) velocidad de reproducción, usando `expo-audio`.

## Ejecutar

```bash
npm install
npx expo start
```

Escanea el QR con Expo Go en un teléfono Android, o presiona `a` para abrir un emulador.

## Limitaciones conocidas (MVP)

- La API de voz en la nube (StreamElements) es un servicio gratuito no oficial: no hay SLA ni garantía de disponibilidad. Si falla, el libro queda marcado como "Error" pero conserva los fragmentos ya generados.
- La conversión ocurre solo con la app abierta (no hay tarea en segundo plano); no cierres la app mientras convierte un libro largo.
- En Android, pausar con la voz del dispositivo reinicia el fragmento actual al reanudar (limitación de `expo-speech`, no soporta pausa real en Android).
- pdf.js extrae texto plano; PDFs escaneados (imágenes sin texto) no producen audio.
