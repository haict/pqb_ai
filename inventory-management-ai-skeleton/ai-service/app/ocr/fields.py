from dataclasses import dataclass

from app.ocr.layout import Cell
from app.ocr.normalize import match_key
from app.schemas import FieldSource, FieldStatus, OcrField

TEXT_DIACRITICS_UNRELIABLE = "TEXT_DIACRITICS_UNRELIABLE"


@dataclass(frozen=True)
class FieldPolicy:
    min_confidence: float
    text_diacritics_reliable: bool


def unknown_field() -> OcrField:
    return OcrField()


def ocr_field(value: str | None, cells: list[Cell], policy: FieldPolicy, *, raw: str | None = None,
              text: bool = False) -> OcrField:
    """Trường đọc từ OCR; độ tin cậy là mức thấp nhất của các đoạn chữ tạo nên trường."""
    if value is None or not cells:
        return unknown_field()
    confidence = round(min(c.token.score for c in cells), 4)
    status = FieldStatus.OK if confidence >= policy.min_confidence else FieldStatus.LOW_CONFIDENCE
    warnings = []
    if text and not policy.text_diacritics_reliable:
        status = FieldStatus.LOW_CONFIDENCE
        warnings.append(TEXT_DIACRITICS_UNRELIABLE)
    boxes = [c.token.box for c in cells]
    return OcrField(
        value=value,
        rawText=raw if raw is not None else " ".join(c.text for c in cells),
        matchKey=match_key(value) if text else None,
        source=FieldSource.OCR,
        confidence=confidence,
        status=status,
        page=cells[0].token.page,
        box=[min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes)],
        warnings=warnings,
    )


def after_colon(text: str) -> str | None:
    head, sep, tail = text.partition(":")
    return tail.strip() or None if sep else None
