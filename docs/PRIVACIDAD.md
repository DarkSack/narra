# Política de privacidad de Narra

Última actualización: 3 de noviembre de 2026.

Narra convierte tus PDF en audiolibros en tu teléfono. Esta política explica qué pasa con tus libros
y con la poca información que sale del teléfono.

## Lo corto

Los libros de uso personal y su audio se quedan en tu teléfono. Las cuentas y el catálogo son
opcionales: solo sube el PDF que elijas compartir explícitamente. El audio se genera en cada
teléfono y nunca se almacena en nuestro servidor. ML Kit puede enviar diagnósticos técnicos.

## Cuenta y libros compartidos

InsForge procesa tu correo, credenciales y sesiones para autenticarte y verificar tu dirección.
El teléfono guarda la sesión cifrada con Android Keystore; no conserva tu contraseña. Tu correo
no aparece en el catálogo. Los metadatos y estados se guardan en Postgres y los PDF en archivos
privados de InsForge, región us-east; nunca guardamos binarios o audio en la base de datos.

Al pulsar «Enviar a revisión» aceptas subir el PDF elegido y sus metadatos. Debes tener derechos
o permiso para distribuirlo. El original pasa por antivirus y reconstrucción en un servicio
aislado. Todas las páginas renderizadas, texto reconocido, título y autor se envían a OpenRouter
y al proveedor de moderación (por defecto Google Gemini) para detectar material sexual explícito
u obsceno. No compartas documentos confidenciales o datos personales. El OCR y la moderación
pueden equivocarse. Un operador revisa el resultado antes de publicarlo (le llega un aviso por
correo, enviado con Resend, con el título del libro); ningún análisis garantiza ausencia absoluta
de riesgos.

Otros usuarios registrados pueden descargar la copia reconstruida aprobada y crear su propio
audio local. Nunca se distribuye el archivo original. Varias denuncias de cuentas distintas
retiran temporalmente un libro del catálogo para revisión.

Los originales y temporales de texto e imágenes se eliminan al terminar o fallar el procesamiento.
El mantenimiento bloquea y limpia los trabajos interrumpidos. Las copias publicadas se conservan
hasta retirarlas o eliminar la cuenta; los metadatos de estados se conservan para mostrar el
resultado y controlar abusos. Los registros técnicos de Narra no incluyen contenido ni tokens.
Los plazos y retenciones independientes de los proveedores se rigen por sus políticas.

## Qué guarda Narra en tu teléfono

- Los PDF que importas, copiados al almacenamiento privado de la app.
- Lo que Narra saca de ellos: título, autor, portada, capítulos y el texto limpio.
- El audio que crea, tu progreso de escucha y tus ajustes.

Otras apps no pueden entrar a ese almacenamiento. El respaldo automático de Android está
desactivado para Narra, así que nada de esto se copia a la nube.

## Qué sale del teléfono

- **Cuenta y catálogo.** Tu correo y los datos necesarios para autenticarte se envían a InsForge.
  Solo los PDF que decidas compartir se suben y revisan por los proveedores descritos arriba.
  Las conexiones de la app a esos servicios usan HTTPS.
- **Diagnóstico de ML Kit.** Cuando Narra reconoce el texto de un PDF escaneado, ML Kit (de Google)
  envía a Google información técnica: modelo y versión de Android del teléfono, versión de la app,
  un identificador por instalación que no identifica a la persona, tiempos de respuesta y códigos de
  error. No envía tus páginas ni su texto. Va cifrado y Google no lo comparte con terceros. Más
  detalle en la [declaración de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure).
- **Compras.** Narra Plus se compra con Google Play. El pago lo procesa Google; Narra solo sabe si
  compraste Plus.
- **Actualizaciones.** Google Play avisa cuando hay una versión nueva.

## Voces

Para leer en voz alta, Narra le pasa el texto al motor de voz del teléfono que elijas (por ejemplo,
los Servicios de voz de Google). La mayoría de las voces funcionan sin conexión. Algunas voces «en
línea» de esos motores envían el texto a su proveedor para crear el audio, según la política de ese
motor; Narra las marca y, por defecto, solo las usa con Wi-Fi.

## Exportar

Si exportas un audiolibro, Narra guarda los archivos en la carpeta que tú elijas. A partir de ahí,
esos archivos son tuyos y quedan fuera de Narra.

## Permisos

- **Notificaciones**: progreso de la creación del audio y controles del reproductor.
- **Servicios en primer plano**: seguir escuchando con la pantalla apagada y crear el audio aunque
  cierres la app.
- **Internet y estado de la red**: los usan la cuenta y el catálogo, las compras, las actualizaciones
  de Google Play y ML Kit, y sirven para saber si hay Wi-Fi antes de usar una voz en línea.

## Borrar tus datos

En la ficha de cada libro, menú ⋮ → «Eliminar libro» (o «Eliminar solo el audio»). Desinstalar Narra
borra la biblioteca local, pero no elimina tu cuenta ni tus publicaciones remotas.

En Catálogo → Mis publicaciones puedes retirar un PDF y eliminar la copia compartida. En Ajustes
→ Tu cuenta → Eliminar cuenta puedes borrar tu cuenta y sus publicaciones. También puedes
solicitarlo por correo a **donsackito@protonmail.com**. Las copias ya descargadas por otros usuarios
permanecen en sus teléfonos. Las denuncias se eliminan al borrar el libro o la cuenta vinculada.

## Menores de edad

Narra no está dirigida a menores de 13 años.

## Cambios

Si una versión futura cambia algo de esta política, se actualizará este texto antes de publicarla.

## Contacto

Johan Jafet Del Valle Santiago · donsackito@protonmail.com
