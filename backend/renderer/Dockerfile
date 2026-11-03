FROM python:3.12-slim-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-spa tesseract-ocr-eng && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip==26.2.2 && pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home renderer
COPY app.py sanitize.py ./
USER 10001
ENV OMP_THREAD_LIMIT=1 PYTHONDONTWRITEBYTECODE=1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1", "--no-access-log"]
