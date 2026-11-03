# Validación de la integración

Fecha de trabajo: 30 de octubre de 2026. El despliegue en el VPS fue aplazado por el usuario.

## Resultado comprobado

- Compilación Android y APK interna de pruebas generados correctamente.
- Pruebas de ambos canales: 110 casos, 0 fallos y 0 errores; 8 omitidos (capturas condicionadas).
- 7 pruebas de backend: autenticación obligatoria, estados no descargables, integridad, límites,
  firma, consentimiento, moderación incompleta, firmas caducadas y limpieza idempotente.
- Reconstrucción real en contenedor sin red: OCR conserva texto para TTS; acciones JavaScript
  y cifrado se rechazan. Probado con Pillow 13.0.0.
- ClamAV real: archivo limpio aceptado y EICAR rechazado. El primer intento se bloqueó por firmas
  caducadas; se actualizaron y recargaron, y la prueba posterior pasó sin desactivar controles.
- Integración real ClamAV → renderer aislado → OCR → moderación de texto/imagen → SHA-256. El
  libro de prueba terminó en `review`, sin publicarse. DB y Storage se simularon en memoria
  en esta prueba para no conservar ni publicar contenido de prueba.
- SDK con InsForge real: consulta, RPC, subida a bucket privado, descarga y borrado comprobados.
- Permisos verificados en Postgres: el rol `authenticated` no puede insertar/actualizar libros,
  consultar `source_key` ni ejecutar `claim_catalog_job`; RLS de Storage activada.
- `npm audit` y `pip-audit` sin vulnerabilidades conocidas con las versiones corregidas.
  Se fijaron Zod 4.6.7, Pillow 13.0.0 y pip 26.2.2. Se notificó a InsForge la dependencia
  transitiva vulnerable de su SDK mediante su CLI (reporte 0acef1d2-1455-405e-96b8-d6e4db9f833d).
- Configuración de Caddy y composición de producción validadas sin iniciar un sitio público (el 3 de noviembre se cambió por el Caddy del sistema del VPS).
- Descripción breve de Play: 71/80 caracteres; completa: 2.306/4.000; notas: 358/500.
- Ficha es-419 guardada como borrador en Play Console, con confirmación visible y captura.

APK local: `dist/catalogo/Narra-catalogo-pruebas.apk` (interna/debug, no subir a Play).
SHA-256: `6A3400F1F0B2EE76E478B980EB43AF18EE96E87D2D5AEB9B55FEE9DEF4F8B832`.

## Producción (3 de noviembre de 2026)

- API, worker, reconstructor y ClamAV en el VPS (`/opt/narra-catalog`), la API solo en
  `127.0.0.1:8080` detrás del Caddy del sistema (`narra.sackito.online`); ver backend/README.md.
- `backend/test/produccion.e2e.mjs` pasó contra producción: cuenta de usar y tirar, subida, ClamAV,
  reconstrucción y moderación reales, libro en `review`, aprobación (desde el mismo día, por el panel
  `/admin`, que vuelve a pasar ClamAV desde la API), listado sin `owner_id`,
  denuncia del propio libro rechazada (422), descarga con SHA-256 correcto y borrado de la cuenta.
- El aviso al operador sale por Resend (el plan gratis de InsForge rechaza correos propios con 403);
  Resend aceptó el correo de prueba.

## Pendientes

Registro del DNS `narra` → VPS (y con él el certificado de Caddy); probar en un teléfono la
verificación de correo, recuperación de contraseña, retirada, bloqueo y borrado de cuenta con la
app; formularios de Play para la 3.3.0 (docs/PLAY_STORE.md, secciones 4 y 5).

No se hizo una auditoría externa ni un pentest. Las pruebas no garantizan ausencia total de
amenazas, evasiones de moderación ni errores de OCR. La aprobación humana sigue siendo obligatoria.
