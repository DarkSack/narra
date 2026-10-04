# Narra — arquitectura y sistema de diseño

> Narra convierte tus PDF en audiolibros organizados, en tu teléfono y sin conexión.

## 1. Punto de partida

El repositorio ya tenía una app Kotlin (Compose + Room + WorkManager + Media3 + PdfBox) que
extraía el texto de un PDF y lo leía **en vivo** con el motor de voz del sistema. Se conserva y
se reutiliza lo que funciona (normalizador de texto, divisor de frases, extractor PdfBox, manejo
de errores para el usuario), pero el producto cambia de naturaleza:

| Antes | Ahora |
|---|---|
| Lectura en vivo, sin archivos de audio | Audio **pregenerado** por segmentos (M4A/AAC), exportable |
| Libro = texto plano por páginas | Libro → capítulos → segmentos → pistas de audio |
| Sin metadatos ni portada | Metadatos, portada, índice, idioma, ISBN |
| Sin OCR | OCR en el dispositivo para PDF escaneados |
| 3 pantallas | Home, Biblioteca, Importar, Procesamiento, Detalle, Now Playing, Capítulos, Voces, Ajustes, Onboarding |

**Por qué pregenerar el audio.** La lectura en vivo no permite duraciones reales, exportar,
mostrar progreso fiable en la pantalla de bloqueo, ni escuchar a 3× sin que el motor distorsione.
El audio en archivos sí, y además permite el flujo pedido: *capítulo 1 listo → escuchar mientras
se genera el 2*. Coste: espacio. AAC mono a 48 kbps ≈ 21 MB por hora de audio (un libro de 10 h
≈ 210 MB); la calidad es configurable y el espacio se muestra en Ajustes.

## 2. Identidad

- **Nombre:** Narra (imperativo de *narrar*: corto, pronunciable en español e inglés).
- **Tagline:** «Tus libros, en voz alta.»
- **Concepto:** la lámpara de lectura nocturna. Tinta (índigo profundo) y luz cálida (ámbar).
- **Personalidad:** serena, literaria, precisa. Habla claro y en segunda persona, nunca técnico.
- **Icono:** un libro abierto cuyas páginas se convierten en ondas de sonido.
- **Tipografía:** *Literata* (serif editorial de Google, OFL) para títulos y portadas; *Inter*
  (OFL) para la interfaz. Ambas incluidas en la app como fuentes variables, funcionan sin red.
- **Paleta:**

| Rol | Claro | Oscuro |
|---|---|---|
| Primario «tinta» | `#4B45C6` | `#C4C1FF` |
| Acento «lámpara» | `#C77A12` | `#F2B45A` |
| Disponible «salvia» | `#3F7A63` | `#8FD1B5` |
| Error | `#C2363B` | `#FF9E9A` |
| Fondo | `#FBF8F2` (papel) | `#111016` (noche) |
| Superficies | papel cálido escalonado | 5 niveles: `#17161D` → `#2D2B36` |

El tema oscuro no invierte colores: las superficies suben de luminosidad con la elevación, el
texto primario es `#ECEAF2` (no blanco puro), los acentos bajan de saturación y las portadas
reciben un velo para no deslumbrar.

### Dos estilos de interfaz (mismo estado, distinta composición)

| | Android Modern | Lectura |
|---|---|---|
| Densidad | media, listas y tarjetas | aireada, portadas protagonistas |
| Portadas | 56–96 dp, esquinas 12 dp | 140–280 dp, sombra de libro, fondo teñido con el color de la portada |
| Títulos | Inter | Literata |
| Movimiento | transiciones cortas del sistema | elementos compartidos, fundidos más largos |

Ambos leen `LocalNarraStyle` y los mismos `UiState`; cambiar de estilo no cambia la funcionalidad.

## 3. Arquitectura

Un módulo Gradle, con capas separadas por paquete (`app.narra`):

```
core/designsystem   tokens, tema, tipografía, componentes reutilizables
core/common         resultados, errores para el usuario, dispatchers
domain/model        Book, Chapter, Segment, AudioTrack, PlaybackProgress, ProcessingJob, Voice…
domain/repository   interfaces: BookRepository, PlaybackRepository, SettingsRepository
domain/processing   interfaces: PdfParser, MetadataExtractor, OcrProcessor, TextProcessor,
                    Segmenter, TtsEngine/TtsProvider, AudioProcessor, ProcessingQueue
data/db             Room: entidades, DAO, migraciones
data/files          BookStorage: rutas, espacio, limpieza
data/repository     implementaciones
data/settings       DataStore
pdf/                PdfBox: texto con posiciones, esquema, metadatos; PdfRenderer: portada/OCR
ocr/                ML Kit Text Recognition (en el dispositivo)
text/               limpieza, párrafos, capítulos, segmentación, idioma
tts/                proveedores (Android TTS; preparado para proveedores en la nube)
audio/              WAV → AAC/M4A (MediaCodec + MediaMuxer), silencios, exportación
processing/         workers de WorkManager, cola, notificaciones
playback/           MediaSessionService + ExoPlayer, temporizador, conexión desde la UI
presentation/       navegación y pantallas (cada una con ViewModel + UiState)
```

Inyección de dependencias con **Hilt**. Ajustes con **DataStore**. Imágenes con **Coil 3**.
Navegación Compose con rutas tipadas (`kotlinx.serialization`).

### Modelo de datos (Room)

- `books`: metadatos, ruta de portada y PDF, estado global, configuración de voz, tamaños.
- `chapters`: orden, título, páginas, nivel, incluido/excluido, estado, duración.
- `segments`: texto (≈1.500 caracteres, nunca parte un párrafo salvo que sea gigante),
  tipo/hablante (preparado para voces por personaje), estado, intentos, error.
- `audio_tracks`: archivo, duración real, tamaño y formato de cada segmento generado.
- `playback_progress`: segmento, posición, velocidad, terminado.
- `processing_jobs`: estado de la cola, prioridad, pausa del usuario, intentos, error.

El audio nunca va a la base de datos: solo sus rutas. El texto de cada página se guarda en
archivos durante el análisis; nada carga el libro completo en RAM.

### Estados

`BookState`: `IDLE → ANALYZING → (OCR) → PARSING → READY → GENERATING ⇄ PAUSED → COMPLETED`, más `ERROR`.
`ChapterState` / `SegmentState`: `PENDING ○`, `PROCESSING ⟳`, `AVAILABLE ✓`, `ERROR ⚠`, `SKIPPED`.

### Flujo de procesamiento

1. **Importar:** el selector del sistema entrega la Uri; se copia el PDF al almacenamiento privado.
2. **Analizar** (`AnalysisWorker`): metadatos, portada (página 1 renderizada), texto con posición
   y tamaño de letra por línea, detección de páginas escaneadas, encabezados/pies repetidos,
   números de página, notas al pie, índice (marcadores del PDF → índice impreso → títulos
   tipográficos → partes de N páginas) e idioma.
3. **OCR** (si hace falta y el usuario acepta): página a página, persistente, reanudable.
4. **Revisión:** «Tu libro está listo». Editar metadatos, portada, capítulos (renombrar,
   reordenar, excluir), revisar el texto limpio, elegir voz y audio.
5. **Generar** (`GenerationWorker`, una cola): segmento a segmento por orden de capítulo.
   Cada segmento: TTS → WAV por párrafo → AAC con silencios → `audio_track`. En cuanto termina
   un segmento se puede escuchar; el reproductor lo añade a la cola sin cortar.
6. **Escuchar:** ExoPlayer con la lista de segmentos disponibles; capítulos como agrupación.

Pausar, reanudar, cancelar y reintentar operan sobre `processing_jobs` y WorkManager. Si el
sistema mata el proceso, WorkManager retoma donde quedó: lo ya generado no se repite.

## 4. Riesgos técnicos y decisiones

| Riesgo | Decisión |
|---|---|
| El motor TTS del sistema varía según fabricante | Abstracción `TtsProvider`; Android TTS por defecto; errores claros si no hay motor |
| Android no codifica MP3 | Exportación en M4A (nativo). OGG/Opus requiere Android 10+. MP3 queda para un proveedor futuro |
| Detección de capítulos en PDF arbitrarios | Cascada de 4 estrategias + edición manual en la revisión |
| Libros enormes | Proceso por página, texto en archivos, PdfBox con memoria mixta |
| Límites de servicios en primer plano (Android 14/15) | WorkManager + tipo `mediaProcessing` (Android 15+) o `dataSync` |
| OCR | ML Kit en el dispositivo (gratuito, sin red; licencia de Google, no es código abierto). Modelo latino incluido en la app |
| Voces por personaje | Modelo preparado (`speaker` por párrafo/segmento, mapa de voces); detección en P2 |

## 5. Fases

1. Sistema de diseño + navegación + Home + Biblioteca + Detalle + Reproductor
2. Importación y análisis del PDF
3. Procesamiento incremental (capítulos y segmentos)
4. TTS + audio
5. Segundo plano + notificaciones
6. OCR
7. Ajustes y personalización
8. Pulido: animaciones, accesibilidad y rendimiento
