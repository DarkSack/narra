# Publicar Narra en Google Play

Todo lo que pide Play Console, listo para copiar. Lo que solo puede hacer el dueño de la cuenta
(crear la app, cobrar, grabar videos, invitar testers, alojar la política) está marcado con **Tú**.

## 1. El paquete firmado

| | |
|---|---|
| Archivo para Play | `E:\Dev\pdf-audiobooks\app\build\outputs\bundle\playRelease\app-play-release.aab` (canal `play`; se sube este, no un APK) |
| Generarlo | `./gradlew :app:bundlePlayRelease` |
| Paquete y versión | `app.narra`, versionName 3.1.0, versionCode 4 |
| SDK | mínimo 26, objetivo 36 (Play exige 36 desde el 31 de agosto de 2026) |
| Páginas de 16 KB | ✅ todas las bibliotecas nativas, incluida la de ML Kit (≈ 11 MB por arquitectura) |
| Llave de subida | `C:\Users\Sack\.android-keys\narra-upload.jks`; contraseña en `keystore.properties` (fuera de git) |
| Huella SHA-256 | `A3:EE:25:55:F6:C4:76:71:A3:6C:60:29:C2:91:35:96:0E:30:67:22:FB:39:7F:37:49:76:22:9B:6A:40:0F:34` |

**Tú**: respalda el `.jks` y `keystore.properties` fuera de esta computadora.

El canal `interna` (APK de GitHub con autoactualizador y permiso para instalar paquetes) nunca va a
Play: Play prohíbe que una app se actualice por fuera de la tienda.

### Notas de la versión 3.1.0

Se pegan tal cual en «Notas de la versión» al crear la versión. `es-419` es español (Latinoamérica), el idioma
predeterminado de la ficha; Play admite 500 caracteres por idioma y estas tienen 489.

```
<es-419>
Primera versión en Google Play.
• Convierte tus PDF en audiolibros por capítulos con las voces de tu teléfono, sin subirlos a ningún lado.
• Detecta título, autor, portada y capítulos, y quita encabezados y números de página.
• Escucha el primer capítulo mientras se crean los demás.
• Reproductor con velocidad, temporizador para dormir y vista horizontal.
• Widgets de Inicio para seguir escuchando y adelantar o atrasar.
• Narra Plus: libros sin límite, exportar a M4A y PDF escaneados.
</es-419>
```

## 2. Ficha de Play Store (español de México)

**Nombre** (30 máx.): `Narra: PDF a audiolibro`

**Descripción breve** (80 máx.):

> Convierte tus PDF en audiolibros con las voces de tu teléfono, sin subirlos.

**Descripción completa:**

> Narra convierte tus PDF en audiolibros organizados por capítulos, en tu propio teléfono.
>
> IMPORTA UN PDF
> • Narra detecta título, autor, portada, idioma y capítulos (con el índice del PDF, el índice
> impreso o los títulos).
> • Limpia encabezados, pies de página, números de página y notas al pie para que no se lean en voz alta.
>
> REVÍSALO
> • Renombra, reordena o excluye capítulos y lee el texto limpio antes de crear el audio.
> • Elige voz, velocidad y tono entre las voces instaladas en tu teléfono.
>
> ESCUCHA MIENTRAS SE CREA
> • El audio se genera por capítulos en segundo plano: empieza a escuchar el primero mientras se
> crean los demás.
> • Reproductor completo: velocidad, temporizador para dormir, saltos configurables, pantalla de
> bloqueo y progreso guardado en cada libro.
>
> NARRA PLUS (compra única)
> • Libros sin límite (sin Plus caben 3 a la vez).
> • Exporta el audiolibro a la carpeta que elijas: un archivo M4A por capítulo y la portada.
> • Reconoce el texto de PDF escaneados, en el propio teléfono.
>
> PRIVACIDAD
> • Tus PDF y el audio se quedan en tu teléfono: Narra no los sube a ningún servidor.
> • Sin cuenta y sin anuncios.

**Categoría:** Libros y obras de consulta. **Etiquetas:** audiolibros, lector de PDF, texto a voz.
**Correo de contacto:** donsackito@protonmail.com, el mismo de la política de privacidad (si lo cambias, cámbialo en las dos).

**Gráficos** (en `play/`):

| Pide Play | Archivo |
|---|---|
| Ícono, 512 × 512 | `play/icono-512.png` |
| Gráfico destacado, 1024 × 500 | `play/grafico-destacado.png` |
| Capturas de teléfono (2 a 8) | `play/capturas/1-inicio.png` … `4-reproductor.png`, 1080 × 2160 |

Las capturas son las pantallas reales dibujadas por las pruebas, con una biblioteca inventada de
clásicos de dominio público y las portadas que Narra genera cuando un PDF no trae imagen. Para
volver a sacarlas:

```bash
./gradlew :app:testPlayDebugUnitTest --tests "app.narra.store.StoreScreenshots" -Pcapturas=E:/Dev/pdf-audiobooks/play/capturas --rerun
```

## 3. Narra Plus

Los pasos para crear el producto están en el [README](../README.md#compilar): perfil de pagos,
subir este bundle a un canal de prueba y crear el producto único **`narra_plus`** con su precio.
**Tú** decides el precio.

## 4. Contenido de la app (Política y programas → Contenido de la app)

| Sección | Respuesta |
|---|---|
| Política de privacidad | https://shop.sackito.online/privacidad/narra (copia de [PRIVACIDAD.md](PRIVACIDAD.md) en `lib/privacidad-apps.ts` de rpgroll-shop: si cambia una, cambiar la otra) |
| Anuncios | No contiene anuncios |
| Acceso a la app | Toda la funcionalidad está disponible sin restricciones (no hay cuenta; Plus es una compra, no un acceso) |
| Clasificación de contenido | Cuestionario IARC, categoría «Utilidades, productividad, comunicación u otros». Compras digitales: **Sí**. Lo demás «No»: sin violencia, sexo, lenguaje, drogas, apuestas, interacción entre usuarios ni ubicación |
| Público objetivo | 13 años o más. «¿Puede atraer a niños?»: No |
| Funciones financieras | «Mi app no ofrece funciones financieras» (vender Plus con Google Play no cuenta) |
| Apps gubernamentales, salud, noticias | No |
| Permisos de servicio en primer plano | Ver la sección 6 |
| Seguridad de los datos | Ver la sección 5 |

## 5. Seguridad de los datos

Narra no tiene servidor ni código de red propio en el canal `play`. Aun así, Play cuenta lo que
envían las bibliotecas que trae. ML Kit, el reconocimiento de texto, manda a Google datos de
diagnóstico cada vez que se usa
([lo que declara Google](https://developers.google.com/ml-kit/android-data-disclosure)).

| Pregunta | Respuesta |
|---|---|
| ¿Recopila o comparte alguno de los tipos de datos? | **Sí** |
| ¿Todos los datos se cifran en tránsito? | Sí (ML Kit usa HTTPS) |
| ¿Ofreces una forma de pedir que se borren los datos? | No (no hay cuentas; los diagnósticos no identifican a la persona) |

Tipos de datos que se marcan:

| Tipo | Recopilado | Compartido | Efímero | Obligatorio | Para qué |
|---|---|---|---|---|---|
| Información y rendimiento de la app → Diagnóstico | Sí | No | No | Obligatorio | Estadísticas |
| Dispositivo u otros identificadores → ID del dispositivo u otros IDs | Sí | No | No | Obligatorio | Estadísticas |

Lo que **no** se marca, y por qué:

- **Archivos y documentos** (los PDF), **audio** y el texto de los libros: no salen del teléfono.
- **Historial de compras**: Play Billing habla con la app de Play Store dentro del teléfono; Narra solo
  consulta si Plus está comprado y no lo envía a ningún lado. Los datos de pago los recoge Google
  directamente.
- **Actualizaciones de Play**: igual, por la app de Play Store.
- **El texto que se lee en voz alta** va al motor de voz que la persona eligió (otra app del teléfono)
  porque ella lo pide al crear el audiolibro. Si ese motor usa voces en línea, lo envía su proveedor
  bajo su propia política. Narra avisa y respeta «solo con Wi-Fi».

Si cambias de versión de ML Kit o agregas una biblioteca con red, revisa este formulario antes de
subir la versión.

## 6. Servicios en primer plano

Play pide, por cada tipo, una descripción, el efecto si el sistema lo interrumpe y **un video**
Ya están grabados (tableta, 2026-10-26) y publicados en el bucket público `play-store` del Supabase de la tienda;
el montaje completo está en `play/video-servicios-primer-plano.mp4`:

- `mediaPlayback`: https://lsngehykuqbpfjcdsrpu.supabase.co/storage/v1/object/public/play-store/narra/fgs-media-playback-v2.mp4
- `mediaProcessing`: https://lsngehykuqbpfjcdsrpu.supabase.co/storage/v1/object/public/play-store/narra/fgs-media-processing-v2.mp4
- `dataSync`: https://lsngehykuqbpfjcdsrpu.supabase.co/storage/v1/object/public/play-store/narra/fgs-data-sync-v2.mp4

Un video por permiso: cada uno abre con el nombre de su permiso y sus rótulos hablan solo de él.

| Tipo | Descripción para Play | Si se interrumpe | El video muestra |
|---|---|---|---|
| `mediaPlayback` | Reproduce el audiolibro con la app cerrada o la pantalla bloqueada, con controles en la notificación y en la pantalla de bloqueo | Se corta la escucha | Empezar a escuchar, salir de la app y bloquear la pantalla; sigue sonando y se controla desde ahí |
| `mediaProcessing` (Android 15+) | Crea el audio del libro cuando la persona pulsa «Crear audiolibro»: convierte el texto a voz y codifica el audio a M4A. Puede tardar horas | La creación se detiene y se retoma después, pero el libro tarda más | Pulsar «Crear audiolibro», salir de la app y ver la notificación de progreso avanzar |
| `dataSync` (Android 14 y anteriores) | El mismo trabajo, que antes de Android 15 no tiene `mediaProcessing`: proceso local de archivos que inicia la persona | Igual | El mismo video sirve |

## 7. Pruebas y lanzamiento (**Tú**)

1. Crear la app en Play Console: «Narra», idioma predeterminado español (Latinoamérica) o (México),
   app, gratis (con compras dentro).
2. **Prueba interna**: subir el `.aab`, crear `narra_plus`, añadirte en «Pruebas de licencia» y probar
   la compra sin pagar.
3. **Prueba cerrada**: las cuentas personales nuevas necesitan 12 testers durante 14 días seguidos
   antes de pedir producción.
4. **Producción**: después, desde «Panel» → «Solicitar acceso a producción».

## Pendiente y riesgos

- La compra real de Plus y la actualización dentro de la app solo funcionan con la app instalada
  desde Play: no se han probado.
- Que la app cumpla estos puntos no garantiza que Google la apruebe: eso solo lo dice su revisión.
