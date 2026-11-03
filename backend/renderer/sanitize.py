"""Parser desechable sin secretos: reconstruye páginas y OCR, sin copiar objetos PDF."""
import json
import os
from pathlib import Path
import resource
import sys
import zipfile
import io

# Límites del proceso que abre el documento, antes de importar parsers nativos.
resource.setrlimit(resource.RLIMIT_CPU, (900, 900))
resource.setrlimit(resource.RLIMIT_AS, (1536 * 1024**2, 1536 * 1024**2))
resource.setrlimit(resource.RLIMIT_FSIZE, (250 * 1024**2, 250 * 1024**2))
resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
os.environ['OMP_THREAD_LIMIT'] = '1'

import pikepdf
import pymupdf
from PIL import Image, ImageChops

# /OpenAction y /URI son comunes (página inicial, enlaces) y no sobreviven a la reconstrucción;
# lo que cuelgue de ellos se sigue revisando al recorrer el árbol.
FORBIDDEN_KEYS = {'/JS', '/JavaScript', '/AA', '/EmbeddedFiles', '/EF', '/XFA', '/RichMediaContent'}
FORBIDDEN_ACTIONS = {'/JavaScript', '/Launch', '/GoToR', '/GoToE', '/SubmitForm', '/ImportData', '/Rendition', '/Movie', '/Sound'}

def inspect(source):
    with pikepdf.open(source, attempt_recovery=False) as pdf:
        if pdf.is_encrypted or not 1 <= len(pdf.pages) <= 500:
            raise ValueError('PDF protegido o fuera de límites')
        if pdf.check_pdf_syntax():
            raise ValueError('Estructura PDF no válida')
        queue = list(pdf.objects)
        if len(queue) > 200_000:
            raise ValueError('Demasiados objetos')
        seen = set()
        visited = 0
        while queue:
            value = queue.pop()
            visited += 1
            if visited > 400_000:
                raise ValueError('Estructura demasiado compleja')
            if isinstance(value, (pikepdf.Dictionary, pikepdf.Stream, pikepdf.Array)):
                if value.is_indirect:
                    if value.objgen in seen:
                        continue
                    seen.add(value.objgen)
                if isinstance(value, (pikepdf.Dictionary, pikepdf.Stream)):
                    if FORBIDDEN_KEYS.intersection(value.keys()) or str(value.get('/S', '')) in FORBIDDEN_ACTIONS:
                        raise ValueError('Contenido activo no permitido')
                    queue.extend(value.values())
                else:
                    queue.extend(value)

def sanitize(source, output):
    inspect(source)
    work = output.parent
    final = pymupdf.open()
    total_text = 0
    total_bytes = 0
    with pymupdf.open(source) as original:
        for index, page in enumerate(original):
            rect = page.rect
            if not (0 < rect.width <= 2000 and 0 < rect.height <= 2000) or rect.width * rect.height * 4 > 10_000_000:
                raise ValueError('Página demasiado grande')
            # Todo lo que se distribuye nace de píxeles; nunca se copian fuentes,
            # acciones, adjuntos, formularios, metadatos ni objetos del original.
            pix = page.get_pixmap(dpi=144, alpha=False, annots=True)
            ocr_bytes = pix.pdfocr_tobytes(compress=True, language='spa+eng')
            with pymupdf.open(stream=ocr_bytes, filetype='pdf') as ocr:
                text = ocr[0].get_text(sort=True)
                if len(text) > 50_000:
                    raise ValueError('Texto demasiado grande')
                total_text += len(text.strip())
                image = Image.frombytes('RGB', (pix.width, pix.height), pix.samples)
                image.thumbnail((round(pix.width * 120 / 144), round(pix.height * 120 / 144)))
                jpeg = io.BytesIO()
                image.save(jpeg, format='JPEG', quality=65, optimize=True)
                compressed = jpeg.getvalue()
                compact = compressed
                # El texto y los escaneos grises suelen ocupar menos con PNG que
                # con JPEG. Comparar candidatos evita pagar el coste de color falso.
                red, green, blue = image.split()
                if max(ImageChops.difference(red, green).getextrema()[1], ImageChops.difference(red, blue).getextrema()[1]) <= 4:
                    gray = io.BytesIO()
                    image.convert('L').save(gray, format='PNG', optimize=True)
                    if len(gray.getvalue()) < len(compact):
                        compact = gray.getvalue()
                # El OCR generado de nuevo se mantiene; solo se recomprime su imagen.
                images = ocr[0].get_images(full=True)
                if len(images) != 1:
                    raise ValueError('Resultado OCR inesperado')
                ocr[0].replace_image(images[0][0], stream=compact)
                final.insert_pdf(ocr)
            (work / f'page-{index}.txt').write_text(text, encoding='utf8')
            (work / f'page-{index}.jpg').write_bytes(compressed)
            total_bytes += len(compressed) + len(text.encode('utf8'))
            if total_bytes > 120 * 1024**2:
                raise ValueError('Resultado demasiado grande')
    if total_text < 30:
        raise ValueError('No se pudo reconocer texto para crear un audiolibro')
    final.set_metadata({})
    final.save(work / 'book.pdf', garbage=4, deflate=True, use_objstms=1)
    count = len(final)
    final.close()
    if (work / 'book.pdf').stat().st_size > 100 * 1024**2:
        raise ValueError('Resultado demasiado grande')
    (work / 'manifest.json').write_text(json.dumps({'pages': count}), encoding='utf8')
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_STORED) as archive:
        for name in ['book.pdf', 'manifest.json'] + [f'page-{i}.{ext}' for i in range(count) for ext in ['jpg','txt']]:
            archive.write(work / name, name)

if __name__ == '__main__':
    try:
        sanitize(Path(sys.argv[1]), Path(sys.argv[2]))
    except (ValueError, pikepdf.PdfError, pikepdf.PasswordError):
        sys.exit(42)
