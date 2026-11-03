"""Prueba real del parser en el mismo contenedor, sin antivirus ni IA simulados como aprobados."""
import subprocess
import tempfile
from pathlib import Path
import json
import zipfile
import pymupdf
import pikepdf

with tempfile.TemporaryDirectory() as directory:
    work=Path(directory)
    source=work/'source.pdf'
    doc=pymupdf.open()
    page=doc.new_page()
    page.insert_text((72,90),'Un libro para escuchar',fontsize=22)
    page.insert_text((72,140),'Este texto debe conservarse para crear el audiolibro en el telefono.',fontsize=12)
    doc.save(source)
    result=subprocess.run(['python','/app/sanitize.py',str(source),str(work/'bundle.zip')],env={'PATH':'/usr/local/bin:/usr/bin','TESSDATA_PREFIX':'/usr/share/tesseract-ocr/5/tessdata'},capture_output=True)
    assert result.returncode==0,result.stderr.decode()
    with zipfile.ZipFile(work/'bundle.zip') as archive:
        assert set(archive.namelist())=={'book.pdf','manifest.json','page-0.txt','page-0.jpg'}
        assert json.loads(archive.read('manifest.json'))['pages']==1
        assert 'escuchar' in archive.read('page-0.txt').decode().lower()
        with pymupdf.open(stream=archive.read('book.pdf'),filetype='pdf') as safe:
            assert 'audiolibro' in safe[0].get_text().lower()
            assert not safe.embfile_count()
    # Enlaces y página inicial, habituales en libros, se aceptan y no sobreviven a la reconstrucción.
    linked=work/'linked.pdf'
    with pikepdf.open(source) as pdf:
        pdf.Root['/OpenAction']=pikepdf.Array([pdf.pages[0].obj,pikepdf.Name('/Fit')])
        link=pikepdf.Dictionary(Type=pikepdf.Name('/Annot'),Subtype=pikepdf.Name('/Link'),Rect=[72,72,200,90],A=pikepdf.Dictionary(S=pikepdf.Name('/URI'),URI='https://example.com'))
        pdf.pages[0].obj['/Annots']=pikepdf.Array([pdf.make_indirect(link)])
        pdf.save(linked)
    result=subprocess.run(['python','/app/sanitize.py',str(linked),str(work/'linked.zip')],env={'PATH':'/usr/local/bin:/usr/bin','TESSDATA_PREFIX':'/usr/share/tesseract-ocr/5/tessdata'},capture_output=True)
    assert result.returncode==0,result.stderr.decode()
    with zipfile.ZipFile(work/'linked.zip') as archive, pymupdf.open(stream=archive.read('book.pdf'),filetype='pdf') as safe:
        assert not safe[0].get_links()
    # Los nombres PDF escapados se resuelven antes de evaluar acciones.
    unsafe=work/'active.pdf'
    with pikepdf.open(source) as pdf:
        pdf.Root['/OpenAction']=pikepdf.Dictionary(S=pikepdf.Name('/JavaScript'),JS='app.alert(1)')
        pdf.save(unsafe)
    assert subprocess.run(['python','/app/sanitize.py',str(unsafe),str(work/'bad.zip')],capture_output=True).returncode==42
    encrypted=work/'encrypted.pdf'
    with pikepdf.open(source) as pdf:
        pdf.save(encrypted,encryption=pikepdf.Encryption(owner='secret',user='secret'))
    assert subprocess.run(['python','/app/sanitize.py',str(encrypted),str(work/'bad.zip')],capture_output=True).returncode==42
print('PASS: PDF reconstruido con OCR; contenido activo y cifrado rechazados')
