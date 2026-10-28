"""So kết quả trích xuất với đáp án. Trường văn bản so bằng độ giống của khóa bỏ dấu vì model làm rơi chữ có dấu."""
from dataclasses import dataclass
from decimal import Decimal
from difflib import SequenceMatcher

from app.ocr.extractors.base import Extraction
from app.ocr.normalize import match_key

TEXT_SIMILARITY = 0.85
# Chữ có dấu không có trong từ điển của latin_PP-OCRv5_mobile_rec (đo 2026-10-27): model không thể đọc ra,
# nên bỏ khỏi đáp án trước khi so để điểm phản ánh lỗi trích xuất chứ không phải giới hạn model.
_MODEL_MISSING = set("ảạằắẳẵặầấẩẫậẻẽẹềếểễệỉịỏọồốổỗộờớởỡợủụừứửữựỳỷỹỵ"
                     "ẢẠẰẮẲẴẶẦẤẨẪẬẺẼẸỀẾỂỄỆỈỊỎỌỒỐỔỖỘỜỚỞỠỢỦỤỪỨỬỮỰỲỶỸỴ")
_TEXT_FIELDS = {"partnerName", "partnerAddress", "deliveryAddress", "name", "unit"}
_NUMBER_FIELDS = {"quantity", "unitPrice", "discountPercent", "taxRate", "amount", "goods", "vat", "total"}
_LINE_FIELDS = {"code": "code", "name": "name", "unit": "unit", "quantity": "quantity", "unitPrice": "unitPrice",
                "discountPercent": "discountPercent", "taxRate": "taxRate", "printedAmount": "amount"}


@dataclass(frozen=True)
class Check:
    field: str
    expected: str
    actual: str | None
    ok: bool


def same(field: str, expected: str, actual: str | None) -> bool:
    if actual is None:
        return False
    if field in _NUMBER_FIELDS:
        try:
            return Decimal(expected) == Decimal(actual)
        except ArithmeticError:
            return False
    if field in _TEXT_FIELDS:
        return SequenceMatcher(None, match_key(_visible(expected)), match_key(actual)).ratio() >= TEXT_SIMILARITY
    return expected.strip() == actual.strip()


def _visible(text: str) -> str:
    return "".join(c for c in text if c not in _MODEL_MISSING)


def score_document(expected: dict, result: Extraction) -> list[Check]:
    checks = []
    for name, value in (expected.get("header") or {}).items():
        if value is not None and name in result.header:
            checks.append(_check(name, value, result.header[name].value))
    lines = expected.get("lines") or []
    checks.append(Check("lineCount", str(len(lines)), str(len(result.lines)), len(lines) == len(result.lines)))
    for i, line in enumerate(lines):
        actual = result.lines[i] if i < len(result.lines) else {}
        for gt_name, name in _LINE_FIELDS.items():
            if line.get(gt_name) is not None:
                field = actual.get(name)
                checks.append(_check(name, line[gt_name], field.value if field else None))
    for name, value in (expected.get("totals") or {}).items():
        if value is not None:
            checks.append(_check(name, value, result.totals[name].value))
    return checks


def _check(field: str, expected: str, actual: str | None) -> Check:
    return Check(field, expected, actual, same(field, expected, actual))
