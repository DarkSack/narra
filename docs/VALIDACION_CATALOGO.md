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
- Configuración de Caddy y composición de producción validadas sin iniciar un sitio público.
- Descripción breve de Play: 71/80 caracteres; completa: 2.306/4.000; notas: 358/500.
- Ficha es-419 guardada como borrador en Play Console, con confirmación visible y captura.

APK local: `dist/catalogo/Narra-catalogo-pruebas.apk` (interna/debug, no subir a Play).
SHA-256: `6A3400F1F0B2EE76E478B980EB43AF18EE96E87D2D5AEB9B55FEE9DEF4F8B832`.

## Pendientes intencionales

Conectar VPS, dominio, HTTPS y secretos; probar registro/verificación/recuperación con un usuario
de prueba autorizado; probar carga, descarga, retirada, denuncias, bloqueo y borrado de cuenta
contra la API desplegada; activar `narra.catalogUrl`; actualizar la política pública y los
formularios de Play para la versión distribuida. El borrador de ficha no se envió a revisión.

No se hizo una auditoría externa ni un pentest. Las pruebas no garantizan ausencia total de
amenazas, evasiones de moderación ni errores de OCR. La aprobación humana sigue siendo obligatoria.
