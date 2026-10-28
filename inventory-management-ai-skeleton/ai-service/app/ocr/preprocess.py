"""Kiểm tra loại file thật và chuyển file thành ảnh trang (docs/14 mục 4.2, 4.3). Xử lý hoàn toàn trong bộ nhớ."""
import io
from enum import Enum

import numpy as np
import pypdfium2 as pdfium
from PIL import Image, ImageOps

from app.config import Settings
from app.errors import file_invalid, unsupported_type


class FileKind(str, Enum):
    JPEG = "JPEG"
    PNG = "PNG"
    PDF = "PDF"


def detect_kind(data: bytes) -> FileKind:
    """Theo chữ ký byte đầu file, không tin phần mở rộng hay Content-Type."""
    if data.startswith(b"\xff\xd8\xff"):
        return FileKind.JPEG
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return FileKind.PNG
    if data.startswith(b"%PDF-"):
        return FileKind.PDF
    raise unsupported_type()


def load_pages(data: bytes, settings: Settings) -> list[np.ndarray]:
    """Trả danh sách ảnh trang dạng BGR (định dạng PaddleOCR nhận)."""
    kind = detect_kind(data)
    images = _pdf_pages(data, settings) if kind is FileKind.PDF else [_image(data, settings)]
    return [_to_bgr(img, settings) for img in images]


def _image(data: bytes, settings: Settings) -> Image.Image:
    try:
        img = Image.open(io.BytesIO(data))
        # Kiểm tra kích thước đọc từ header trước khi giải mã, chặn ảnh "bom giải nén".
        _check_pixels(img.width, img.height, settings)
        img.load()
        return ImageOps.exif_transpose(img)
    except (OSError, ValueError, Image.DecompressionBombError) as exc:
        raise file_invalid() from exc


def _pdf_pages(data: bytes, settings: Settings) -> list[Image.Image]:
    try:
        pdf = pdfium.PdfDocument(data)
    except pdfium.PdfiumError as exc:
        raise file_invalid() from exc
    try:
        if len(pdf) == 0:
            raise file_invalid()
        if len(pdf) > settings.ocr_max_pdf_pages:
            raise file_invalid(f"PDF vượt quá {settings.ocr_max_pdf_pages} trang.")
        scale = settings.ocr_pdf_dpi / 72
        pages = []
        for page in pdf:
            width, height = page.get_size()
            _check_pixels(int(width * scale), int(height * scale), settings)
            pages.append(page.render(scale=scale).to_pil())
        return pages
    except pdfium.PdfiumError as exc:
        raise file_invalid() from exc
    finally:
        pdf.close()


def _check_pixels(width: int, height: int, settings: Settings) -> None:
    if width <= 0 or height <= 0 or width * height > settings.ocr_max_image_pixels:
        raise file_invalid("Ảnh vượt kích thước cho phép.")


def _to_bgr(img: Image.Image, settings: Settings) -> np.ndarray:
    img = img.convert("RGB")
    longest = max(img.size)
    if longest > settings.ocr_max_image_side:
        ratio = settings.ocr_max_image_side / longest
        img = img.resize((round(img.width * ratio), round(img.height * ratio)), Image.Resampling.LANCZOS)
    return np.ascontiguousarray(np.asarray(img)[:, :, ::-1])
