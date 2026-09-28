# Vietnamese OCR service

The service is intentionally private to the Docker network. The Spring Boot
backend calls it at `http://ocr:8090`.

Endpoints:

- `GET /health` - checks the service and the `vie` Tesseract language pack.
- `POST /ocr/pdf` (`multipart/form-data`, field `file`) - returns a searchable
  PDF. The original scanned page remains visible and OCR text is embedded as
  a selectable layer.
- `POST /ocr/text` (`multipart/form-data`, field `file`) - returns extracted
  text as JSON for auto-fill/search.

The default language is `vie+eng`; configure it with `OCR_LANGUAGES`. The
default upload limit is 40 MB and timeout is 180 seconds. `OCR_WORKERS=2`
allows two documents to be processed concurrently; increase it only when the
host has enough CPU/RAM.

Run without touching existing PostgreSQL data:

```powershell
docker compose build ocr
docker compose up -d ocr
docker compose ps ocr
```
