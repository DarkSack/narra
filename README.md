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

La app tiene dos canales de distribución (flavors), que solo cambian cómo se actualiza:

| Canal | Para | Actualización |
|---|---|---|
| `interna` | APK que se reparte a mano | Busca en GitHub Releases al abrir y una vez al día, descarga con Wi-Fi y avisa para instalar |
| `play` | Google Play | Actualización flexible de Play: aviso de Play, descarga en segundo plano y botón para reiniciar |

```bash
./gradlew :app:assembleInternaDebug
```

El reconocimiento de texto de ML Kit incluye una librería nativa de unos 11 MB por arquitectura.
Un APK universal las lleva todas (≈ 49 MB); con el App Bundle, cada teléfono descarga solo la suya.

Pruebas:

```bash
./gradlew :app:testInternaDebugUnitTest :app:testPlayDebugUnitTest
./gradlew :app:connectedInternaDebugAndroidTest
```

## Publicar una versión

1. En `app/build.gradle.kts`, sube **los dos**: `versionCode` (+1) y `versionName` (p. ej. `3.1.0`).
   La app interna compara el nombre para saber si hay versión nueva y Android exige un
   `versionCode` mayor para instalarla.
2. **Interna.** El APK se firma con `~/.android/debug.keystore`, la misma llave que firmó la 3.0.0:
   con otra llave Android no instala la actualización. Guarda una copia de ese archivo.

   ```bash
   ./gradlew :app:assembleInternaRelease
   gh release create v3.1.0 app/build/outputs/apk/interna/release/app-interna-release.apk --title "Narra 3.1.0" --notes "Qué cambió"
   ```

   La etiqueta debe ser `v` + `versionName`. La 3.0.0 no trae el actualizador: quien la tenga
   instala esta versión a mano una vez (encima, sin desinstalar) y desde ahí se actualiza sola.
3. **Play.** Genera el App Bundle firmado con tu llave de subida y súbelo a Play Console:

   ```bash
   ./gradlew :app:bundlePlayRelease
   ```

   La actualización dentro de la app solo funciona con la app instalada desde Play.
