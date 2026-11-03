# Backend del catálogo de Narra

InsForge aloja autenticación, metadatos y archivos privados de Narra. Este directorio contiene la
API HTTP y los procesos de análisis. No guarda audios.

## Estado y despliegue

Las tablas, RLS, autenticación y buckets privados están aplicados en InsForge. La API de
procesamiento necesita un servidor de contenedores con HTTPS y recursos para ClamAV y OCR.
`compose.yaml` describe la topología y aislamiento; no equivale a un despliegue público.
Android habilita subidas/descargas al compilar con `-Pnarra.catalogUrl=https://HOST`. Sin esa
propiedad muestra que el catálogo está en preparación; la cuenta funciona con InsForge.

Configura `.env.example` en el gestor de secretos del servidor. No copies las credenciales a
Gradle, Android ni al repositorio. El `.env` local está ignorado por git. API: `npm start`.
Consumidor: `npm run worker`. En contenedores usa `compose.yaml`: API, worker, renderer y ClamAV.
Expón únicamente la API mediante proxy HTTPS; nunca publiques ClamAV o el renderer. El renderer
solo se comunica con el worker en red interna, sin claves ni salida a Internet. ClamAV usa UTC.

El host necesita unos 6 GB de RAM y espacio temporal. El worker es único; su mantenimiento limpia
trabajos interrumpidos. Para escalar, coordina el mantenimiento y usa límites de peticiones
compartidos; el rate limiter actual vive en memoria de una única instancia de API. El proxy limita
la petición a 100 MiB; la API corta una transferencia por debajo de 64 KiB/s tras el primer minuto
(con tope de 30 min), así una conexión lenta de verdad termina y una que gotea no retiene su hueco.

Producción (desde el 2026-11-03): el VPS de Sack en `/opt/narra-catalog` (copia de este
directorio sin `node_modules`, con su `.env` en modo 600) y `docker compose up -d --build`. La API
solo escucha en `127.0.0.1:8080`; el Caddy del sistema la publica en `https://narra.sackito.online`
con este bloque de `/etc/caddy/Caddyfile`:

```
narra.sackito.online {
	reverse_proxy 127.0.0.1:8080
	request_body {
		max_size 100MiB
	}
	header -Server
}
```

Para actualizar, vuelve a copiar el directorio y ejecuta `docker compose up -d --build api worker`.
Prueba de humo contra producción (crea una cuenta de usar y tirar, sube, aprueba por el panel,
descarga y borra todo; manda un aviso real a `REVIEW_EMAIL`):

```sh
docker run --rm --env-file .env --network narra-catalog_edge \
  -v /opt/narra-catalog/test/produccion.e2e.mjs:/app/test/produccion.e2e.mjs:ro \
  narra-catalog-worker node test/produccion.e2e.mjs
```

Solo deben ser públicos SSH, 80 y 443. Los puertos de renderer y ClamAV quedan en redes internas.
No pongas claves en argumentos de shell ni publiques `.env`.

Para infraestructura en InsForge, usa su CLI `compute` y sus reglas de redes privadas y secretos.
Si el alojamiento no permite mantener el renderer sin salida a Internet y aislado del API,
conserva desactivadas las subidas.

## Moderación humana

El worker deja los libros en `review`, nunca `published`. Un operador abre la copia revisada,
revisa **todas** las páginas y su texto, comprueba derechos y contenido, y aprueba o rechaza.

**Panel web:** `https://narra.sackito.online/admin/`, con la contraseña `ADMIN_PASSWORD` del `.env`
(HTTP Basic; el usuario da igual). Sin esa variable el panel no existe (404). Muestra pendientes y
publicados con denuncias, abre el PDF y pide marcar «Revisé todas las páginas» antes de aprobar.
Los POST exigen la cabecera `X-Narra-Admin` (contra CSRF) y la API admite 10 contraseñas malas
cada 15 minutos por IP. Para cambiarla, edita el `.env` y ejecuta `docker compose up -d api`.

**Desde la terminal**, en el entorno privado del worker:

```sh
npm run review -- list
npm run review -- approve UUID --reviewed
npm run review -- reject UUID --reviewed
```

En el VPS: `docker compose exec worker node src/review.mjs list` (y `approve`/`reject` igual).
Con `RESEND_API_KEY`, `EMAIL_FROM` y `REVIEW_EMAIL` llega un correo cada vez que un libro entra
en `review`; si el correo falla, el log del worker dice `catalog_notice_failed` y el libro espera
igual en `list`.

La aprobación comprueba hash y tamaño y vuelve a pasar antivirus. Tres denunciantes distintos
con cuentas de al menos 7 días, desde la última aprobación, pasan el libro publicado a `review`
hasta resolución. `list` muestra los pendientes y los publicados con denuncias por debajo del
umbral o de cuentas nuevas. Los originales nunca se distribuyen.

## Validación

```sh
npm ci
npm test
npm audit --omit=dev --audit-level=high
docker build -t narra-renderer:local renderer
docker run --rm --network none --read-only --tmpfs /tmp:size=512m \
  --cap-drop ALL --security-opt no-new-privileges \
  --mount type=bind,source=RUTA_ABSOLUTA/test_renderer.py,target=/app/test_renderer.py,readonly \
  narra-renderer:local python /app/test_renderer.py
```

`test_renderer.py` está en `renderer/`. Comprueba reconstrucción real con OCR y rechazo de acciones
y cifrado. Las pruebas unitarias verifican bytes reales, firma fragmentada, consentimiento y
bloqueo ante moderación incompleta o firmas caducadas.

Zod se fija en 4.6.7 con `overrides` para corregir GHSA-mrhx-c7xc-m3w5. El SDK 1.5.2 se comprobó
con operaciones reales de consulta, RPC, subida, descarga y eliminación. Repite esas comprobaciones
al cambiar el SDK. Consulta [CATALOGO.md](../docs/CATALOGO.md) y [privacidad](../docs/PRIVACIDAD.md).

Prueba de integración opcional con ClamAV, renderer y moderación reales (Storage/DB en memoria,
sin publicar ni conservar el libro de prueba): monta `test/` en `/tests` y ejecuta
`node /tests/pipeline.integration.mjs` en el worker con `src/` actualizado y los servicios activos.
