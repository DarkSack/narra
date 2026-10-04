# Narra

App de Android que convierte tus PDF en audiolibros organizados, en el teléfono y sin conexión.

- **Importa un PDF** y Narra detecta título, autor, portada, idioma y capítulos (índice interno,
  índice impreso o títulos), y limpia encabezados, pies, números de página y notas al pie.
- **Revisa** el resultado: renombra, reordena o excluye capítulos y lee el texto limpio.
- **Crea el audiolibro** con las voces del teléfono. El audio se genera por capítulos en segundo
  plano y puedes empezar a escuchar el primero mientras se crean los demás.
- **Escucha** con reproductor completo: velocidad, temporizador, saltos configurables,
  notificación, pantalla de bloqueo y progreso guardado.
- **Exporta** el audiolibro a la carpeta que elijas: un M4A por capítulo y la portada.
- **PDF escaneados:** reconoce el texto de las páginas en el propio teléfono.

El PDF nunca sale del teléfono.

## Tecnología

Kotlin, Jetpack Compose y Material 3, Hilt, Room, WorkManager, DataStore, Media3 (ExoPlayer),
PdfBox-Android, ML Kit (OCR), MediaCodec/MediaMuxer (AAC/M4A). La arquitectura y el sistema de
diseño están descritos en [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md).

## Compilar

Requiere JDK 17 y el SDK de Android (compileSdk 36).

```bash
./gradlew :app:assembleDebug
```

Para publicar, genera un App Bundle en lugar de un APK:

```bash
./gradlew :app:bundleRelease
```

El reconocimiento de texto de ML Kit incluye una librería nativa de unos 11 MB por arquitectura.
Un APK universal las lleva todas (≈ 49 MB); con el App Bundle, cada teléfono descarga solo la suya.

Pruebas:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
