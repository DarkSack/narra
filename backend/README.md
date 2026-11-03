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
compartidos; el rate limiter actual vive en memoria de una única instancia de API. En el proxy
limita también tamaño de petición a 100 MiB y tiempo a 180 segundos.

Para el VPS, una vez elegido el dominio y apuntado su DNS, añade `NARRA_DOMAIN` al `.env` privado.
`compose.production.yaml` incorpora Caddy para HTTPS automático. Después de revisar puertos,
firewall y que el acceso SSH se conserva, el comando preparado es:

```sh
docker compose -f compose.yaml -f compose.production.yaml up -d --build
```

Solo deben ser públicos SSH, 80 y 443. Los puertos de renderer y ClamAV quedan en redes internas;
la API se publica mediante Caddy. No pongas claves en argumentos de shell ni publiques `.env`.
No ejecutes el comando de producción hasta disponer del VPS y dominio correctos.

Para infraestructura en InsForge, usa su CLI `compute` y sus reglas de redes privadas y secretos.
Si el alojamiento no permite mantener el renderer sin salida a Internet y aislado del API,
conserva desactivadas las subidas.

## Moderación humana

El worker deja los libros en `review`, nunca `published`. Un operador debe descargar la copia
privada desde el panel administrativo, revisar **todas** las páginas y su texto, comprobar derechos
y contenido, y ejecutar desde el entorno privado del worker:

```sh
npm run review -- approve UUID --reviewed
npm run review -- reject UUID --reviewed
```

La aprobación comprueba hash y tamaño y vuelve a pasar antivirus. Tres denunciantes distintos
desde la última aprobación pasan el libro publicado a `review` hasta resolución; las denuncias
anteriores al umbral se consultan en `catalog_reports`. Los originales nunca se distribuyen.

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
