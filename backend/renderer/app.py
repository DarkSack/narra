import asyncio
import os
from pathlib import Path
import shutil
import tempfile
from fastapi import FastAPI, Request, HTTPException
from starlette.background import BackgroundTask
from starlette.responses import FileResponse

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
gate = asyncio.Semaphore(1)

@app.post('/render')
async def render(request: Request):
    if request.headers.get('content-type') != 'application/pdf':
        raise HTTPException(415)
    if gate.locked():
        raise HTTPException(503)
    async with gate:
        directory = Path(tempfile.mkdtemp(prefix='narra-'))
        try:
            source = directory / 'source.pdf'
            size = 0
            with source.open('wb') as stream:
                async for chunk in request.stream():
                    size += len(chunk)
                    if size > 100 * 1024**2:
                        raise HTTPException(413)
                    stream.write(chunk)
            with source.open('rb') as stream:
                if stream.read(5) != b'%PDF-':
                    raise HTTPException(422)
            output = directory / 'bundle.zip'
            process = await asyncio.create_subprocess_exec(
                'python', '/app/sanitize.py', str(source), str(output),
                env={'PATH': os.environ['PATH'], 'TESSDATA_PREFIX': '/usr/share/tesseract-ocr/5/tessdata', 'OMP_THREAD_LIMIT': '1'},
                stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL,
                start_new_session=True,
            )
            try:
                code = await asyncio.wait_for(process.wait(), timeout=1800)
            except (TimeoutError, asyncio.CancelledError):
                process.kill()
                await process.wait()
                raise HTTPException(503)
            if code:
                raise HTTPException(422 if code == 42 else 503)
            return FileResponse(output, media_type='application/zip', background=BackgroundTask(shutil.rmtree, directory))
        except BaseException:
            shutil.rmtree(directory, ignore_errors=True)
            raise
