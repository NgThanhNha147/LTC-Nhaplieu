FROM python:3.12-slim-trixie

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# Vietnamese OCR data is installed explicitly. OCRmyPDF creates a searchable
# PDF while preserving the original scanned page as the visible image.
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends \
       tesseract-ocr \
       tesseract-ocr-vie \
       tesseract-ocr-eng \
       ocrmypdf \
       poppler-utils \
       ghostscript \
       qpdf \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY app.py .

RUN useradd --system --uid 10001 ocruser \
    && mkdir -p /tmp/ocr \
    && chown -R ocruser:ocruser /app /tmp/ocr
USER ocruser

EXPOSE 8090
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090"]
