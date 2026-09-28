"""Small Vietnamese OCR service used by the dynamic-form backend.

The service intentionally has no database access. It receives a PDF, runs
OCRmyPDF/Tesseract, and returns a searchable PDF or extracted text. Keeping
this workload in a separate container makes it safe to scale independently.
"""

from __future__ import annotations

import json
import os
import subprocess
import tempfile
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from starlette.background import BackgroundTask

app = FastAPI(title="Dynamic Form Vietnamese OCR", version="0.1.0")

LANGUAGES = os.getenv("OCR_LANGUAGES", "vie+eng")
MAX_FILE_BYTES = int(os.getenv("OCR_MAX_FILE_BYTES", str(40 * 1024 * 1024)))
TIMEOUT_SECONDS = int(os.getenv("OCR_TIMEOUT_SECONDS", "180"))


@app.get("/health")
def health() -> dict[str, str]:
    """Container/readiness probe and a quick check that language data exists."""
    try:
        languages = subprocess.check_output(
            ["tesseract", "--list-langs"], text=True, stderr=subprocess.STDOUT
        )
        ready = "vie" in languages.split()
    except (OSError, subprocess.CalledProcessError):
        ready = False
    return {"status": "UP" if ready else "DEGRADED", "language": LANGUAGES}


async def _save_upload(file: UploadFile, path: Path) -> int:
    if file.content_type not in ("application/pdf", "application/octet-stream"):
        raise HTTPException(status_code=415, detail="OCR chỉ nhận file PDF")
    size = 0
    with path.open("wb") as output:
        while chunk := await file.read(1024 * 1024):
            size += len(chunk)
            if size > MAX_FILE_BYTES:
                raise HTTPException(status_code=413, detail="PDF vượt quá giới hạn OCR")
            output.write(chunk)
    if size == 0:
        raise HTTPException(status_code=400, detail="PDF rỗng")
    return size


def _run_ocr(source: Path, target: Path) -> None:
    command = [
        "ocrmypdf",
        "--language",
        LANGUAGES,
        "--deskew",
        "--rotate-pages",
        "--skip-text",
        "--output-type",
        "pdf",
        str(source),
        str(target),
    ]
    try:
        subprocess.run(
            command,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired as exc:
        raise HTTPException(status_code=504, detail="OCR quá thời gian xử lý") from exc
    except subprocess.CalledProcessError as exc:
        # Do not expose command arguments or the uploaded document content.
        detail = (exc.stderr or "").strip().splitlines()[-1:]
        raise HTTPException(status_code=422, detail=detail[0] if detail else "Không thể OCR PDF") from exc


def _extract_text(pdf: Path) -> str:
    try:
        result = subprocess.run(
            ["pdftotext", "-layout", str(pdf), "-"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=30,
        )
        return result.stdout
    except (OSError, subprocess.SubprocessError) as exc:
        raise HTTPException(status_code=422, detail="Không thể trích xuất text OCR") from exc


def _remove_file(path: Path) -> None:
    try:
        path.unlink(missing_ok=True)
    except OSError:
        pass


@app.post("/ocr/pdf")
async def ocr_pdf(file: Annotated[UploadFile, File(...)]) -> FileResponse:
    """Return a searchable PDF, preserving the original scan visually."""
    with tempfile.TemporaryDirectory(prefix="dynamic-form-ocr-", dir="/tmp/ocr") as directory:
        source = Path(directory) / "source.pdf"
        target = Path(directory) / "searchable.pdf"
        await _save_upload(file, source)
        _run_ocr(source, target)
        if not target.exists():
            raise HTTPException(status_code=422, detail="OCR không tạo được PDF")
        # FileResponse streams before the temporary directory is removed only
        # after the response is sent by Starlette; copy to a stable temp path.
        stable = Path(tempfile.mkstemp(prefix="ocr-result-", suffix=".pdf", dir="/tmp/ocr")[1])
        target.replace(stable)
    return FileResponse(
        stable,
        media_type="application/pdf",
        filename="searchable.pdf",
        background=BackgroundTask(_remove_file, stable),
    )


@app.post("/ocr/text")
async def ocr_text(file: Annotated[UploadFile, File(...)]) -> JSONResponse:
    """Return extracted text for auto-fill/search features."""
    with tempfile.TemporaryDirectory(prefix="dynamic-form-ocr-", dir="/tmp/ocr") as directory:
        source = Path(directory) / "source.pdf"
        target = Path(directory) / "searchable.pdf"
        size = await _save_upload(file, source)
        _run_ocr(source, target)
        text = _extract_text(target)
    return JSONResponse({"language": LANGUAGES, "bytes": size, "text": text})
