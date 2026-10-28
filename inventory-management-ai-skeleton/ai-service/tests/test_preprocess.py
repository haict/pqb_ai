import io
import struct
import zlib

import pypdfium2 as pdfium
import pytest

from app.errors import AiServiceError
from app.ocr.preprocess import FileKind, detect_kind, load_pages
from tests.conftest import png_bytes


def pdf_bytes(pages: int) -> bytes:
    pdf = pdfium.PdfDocument.new()
    for _ in range(pages):
        pdf.new_page(595, 842)
    buf = io.BytesIO()
    pdf.save(buf)
    pdf.close()
    return buf.getvalue()


def png_header_only(width: int, height: int) -> bytes:
    """PNG chỉ có IHDR khai báo kích thước lớn: phải bị chặn trước khi giải mã."""
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    chunk = struct.pack(">I", len(ihdr)) + b"IHDR" + ihdr + struct.pack(">I", zlib.crc32(b"IHDR" + ihdr))
    return b"\x89PNG\r\n\x1a\n" + chunk


def test_detect_kind_by_magic_bytes():
    assert detect_kind(png_bytes()) is FileKind.PNG
    assert detect_kind(b"\xff\xd8\xff\xe0rest") is FileKind.JPEG
    assert detect_kind(pdf_bytes(1)) is FileKind.PDF


@pytest.mark.parametrize("data", [b"MZ\x90\x00 executable", b"plain text renamed to .jpg", b""])
def test_unsupported_type_is_415(data):
    with pytest.raises(AiServiceError) as exc:
        detect_kind(data)
    assert (exc.value.status, exc.value.code) == (415, "OCR_UNSUPPORTED_TYPE")


def test_image_converted_to_bgr(settings):
    pages = load_pages(png_bytes(64, 48), settings)
    assert len(pages) == 1
    assert pages[0].shape == (48, 64, 3)


def test_image_over_pixel_limit_rejected_before_decoding(settings):
    with pytest.raises(AiServiceError) as exc:
        load_pages(png_header_only(12000, 9000), settings)
    assert (exc.value.status, exc.value.code) == (400, "OCR_FILE_INVALID")


def test_large_image_is_downscaled(settings):
    page = load_pages(png_bytes(3000, 1000), settings)[0]
    assert max(page.shape[:2]) == settings.ocr_max_image_side


def test_pdf_pages_rendered(settings):
    assert len(load_pages(pdf_bytes(2), settings)) == 2


def test_pdf_over_page_limit_rejected(settings):
    with pytest.raises(AiServiceError) as exc:
        load_pages(pdf_bytes(3), settings)
    assert exc.value.code == "OCR_FILE_INVALID"


@pytest.mark.parametrize("data", [b"%PDF-1.7 broken", b"\x89PNG\r\n\x1a\ncorrupted", b"\xff\xd8\xff\xe0broken"])
def test_corrupted_file_is_invalid(settings, data):
    with pytest.raises(AiServiceError) as exc:
        load_pages(data, settings)
    assert exc.value.code == "OCR_FILE_INVALID"
