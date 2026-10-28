import time

from fastapi import APIRouter, Depends, File, Form, Request, UploadFile

from app.errors import AiServiceError, file_too_large, no_text
from app.logging_setup import log
from app.ocr.extractors import SPECS
from app.ocr.extractors.base import extract
from app.ocr.fields import FieldPolicy
from app.ocr.preprocess import load_pages
from app.schemas import DocumentType, ExtractResponse
from app.security import require_service_key

router = APIRouter(prefix="/internal/v1/ocr", dependencies=[Depends(require_service_key)])

_READ_CHUNK = 1024 * 1024


@router.post("/extract", response_model=ExtractResponse)
def extract_document(request: Request, file: UploadFile = File(...),
                     documentType: DocumentType = Form(...)) -> ExtractResponse:
    settings = request.app.state.settings
    start = time.perf_counter()
    size, pages, error = 0, 0, None
    try:
        data = _read_limited(file, settings.ocr_max_file_size)
        size = len(data)
        images = load_pages(data, settings)
        pages = len(images)
        tokens = request.app.state.ocr_runner.run(images)
        if not tokens:
            raise no_text()
        policy = FieldPolicy(settings.ocr_min_confidence, settings.ocr_text_diacritics_reliable)
        result = extract(tokens, SPECS[documentType], policy)
        return ExtractResponse(
            documentType=documentType, pageCount=pages, header=result.header, lines=result.lines,
            totals=result.totals, warnings=result.warnings, durationMs=int((time.perf_counter() - start) * 1000),
        )
    except AiServiceError as exc:
        error = exc.code
        raise
    finally:
        # Chỉ metadata (BR-AI-10): không ghi tên file, nội dung OCR, MST, số tiền.
        log.info("OCR documentType=%s size=%d pages=%d error=%s durationMs=%d", documentType.value, size, pages,
                 error or "-", (time.perf_counter() - start) * 1000)


def _read_limited(file: UploadFile, limit: int) -> bytes:
    chunks, total = [], 0
    while chunk := file.file.read(_READ_CHUNK):
        total += len(chunk)
        if total > limit:
            raise file_too_large()
        chunks.append(chunk)
    return b"".join(chunks)
