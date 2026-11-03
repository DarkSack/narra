# Catálogo compartido: alcance y seguridad

## Flujo

1. Cuenta por correo/contraseña (mínimo 12 caracteres), verificación con código y recuperación.
2. Consentimiento explícito para compartir el PDF, revisión por terceros y declaración de derechos.
3. API valida autenticación, extensión, MIME, firma `%PDF-`, metadatos y bytes reales.
4. Reserva atómica de cuota: 5 envíos/día, 3 revisiones simultáneas y 100 publicaciones activas por
   cuenta. Una subida que no terminó no gasta cupo; las rechazadas sí cuentan en el día.
   Tope inicial global: 10 GiB de archivos/reservas y 100.000 registros, ajustable por migración.
5. Original a Storage privado `quarantine`; solo metadatos en Postgres. Nunca se aceptan audios.
6. Worker con reclamación atómica `SKIP LOCKED`: ClamAV con firmas de hasta 72 horas, revisión
   estructural, rechazo de cifrado/scripts/acciones/adjuntos, reconstrucción de todas las páginas
   y OCR. El contenedor del parser no tiene credenciales, volúmenes del host ni salida a Internet.
7. Moderación de metadatos y de **cada** página (imagen + todo su texto, sin muestreo) por OpenRouter.
8. Antivirus de la copia reconstruida, SHA-256 y almacenamiento privado `books`.
9. Estado `review`. Un operador debe revisar la copia completa y aprobarla; se vuelve a escanear.
10. La API solo sirve PDF cuyo registro siga publicado y cuyo tamaño y hash coincidan. Android
    vuelve a comprobar firma, tamaño y SHA-256; copia el PDF a su biblioteca y abre la revisión
    habitual para elegir voz y crear audio local.

Los clientes no pueden escribir tablas del catálogo, modificar estados, reclamar trabajos ni
acceder a claves de Storage. No se generan enlaces públicos o firmados para clientes; una retirada
revoca nuevas descargas. No puede retirar las copias ya descargadas.
Puedes denunciar contenido o bloquear a quien lo compartió. Con 3 denunciantes distintos desde la
última aprobación el libro pasa a `review`; cada cuenta envía hasta 10 denuncias al día y no puede
denunciar sus propios libros. Las denuncias por debajo de ese umbral quedan en `catalog_reports`
para el operador. Bloquear oculta los libros de esa persona y rechaza sus descargas para tu cuenta;
los bloqueos se gestionan desde Tu cuenta.

## Compresión y límites

Compartir: 100 MiB, 500 páginas, dimensiones <= 2.000 puntos, <= 10 millones de píxeles/página.
Uso local: 100 MiB y 1.500 páginas. Las entradas malformadas o demasiado complejas se rechazan.
Los enlaces y la acción de apertura (`/URI`, `/OpenAction`) se admiten: la copia se reconstruye
desde imágenes. Scripts, adjuntos, formularios XFA y acciones externas impiden compartir, pero no
la conversión local: PdfBox no ejecuta acciones.
El parser trabaja con límites de CPU, memoria, disco, procesos y tiempo.

La copia nace de píxeles y OCR en español/inglés; no se copian objetos, fuentes ni metadatos del
original. Imágenes a 120 ppp/JPEG 65; en páginas grises se compara con PNG y se elige el menor.
Se muestran tamaño final y ahorro real; nunca se promete
«máxima compresión». Los documentos vectoriales pueden crecer y el reconocimiento puede introducir
errores o perder idiomas, capítulos, formatos o índices.

## Fallos y retención

Una moderación ausente, incompleta, ambigua o truncada bloquea la publicación. Antivirus caído,
firmas caducadas, OCR fallido o timeout también bloquean. No hay modo de omitir controles.

Los originales se eliminan al terminar o fallar el worker. El mantenimiento cada 5 minutos bloquea
subidas >1 hora, procesos >2 horas y colas >24 horas; limpia originales y copias de registros
bloqueados/rechazados. Un fallo de Storage se reintenta en el próximo mantenimiento. Los metadatos
se conservan para explicar estados y controlar cuota, hasta eliminar la cuenta.

## Límites de la protección

Antivirus, reglas estructurales, aislamiento y clasificación reducen riesgos; no demuestran
ausencia absoluta de amenazas ni de contenido inapropiado. El clasificador puede sufrir evasiones
o errores. La aprobación humana es obligatoria. Actualizar parsers, contenedores, firmas y
dependencias sigue siendo una tarea operativa continua.

El análisis remoto solo cubre libros enviados al catálogo. La importación local tiene límites y
rechaza contenido activo, pero **no incluye antivirus ni moderación de todo PDF personal**.

Referencias: [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html),
[protocolo ClamD](https://docs.clamav.net/manual/Usage/ClamdProtocol.html).
