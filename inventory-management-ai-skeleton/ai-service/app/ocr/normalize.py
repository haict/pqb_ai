"""Chuẩn hóa số, ngày, MST bằng code, không dùng LLM, không dùng float (docs/14 mục 4.3 bước 5)."""
import re
import unicodedata
from datetime import date

_NON_ALNUM = re.compile(r"[^a-z0-9]+")


def unaccent(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text.replace("đ", "d").replace("Đ", "D"))
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def match_key(text: str) -> str:
    """Khóa so khớp: bỏ dấu, chữ thường, chỉ giữ chữ và số. Dùng cho nhãn trường và để Spring so với danh mục."""
    return _NON_ALNUM.sub(" ", unaccent(text).lower()).strip()


_VN_THOUSANDS = re.compile(r"^\d{1,3}(\.\d{3})+$")
_NUMBER = re.compile(r"^-?\d+(\.\d+)?$")


def parse_number(raw: str) -> str | None:
    """Số kiểu Việt Nam: '.' ngăn hàng nghìn, ',' thập phân. Trả chuỗi thập phân hoặc None nếu không phải số."""
    text = raw.strip().replace(" ", "").rstrip("%").strip()
    if not text:
        return None
    if "," in text and "." in text:
        text = text.replace(".", "").replace(",", ".")
    elif "," in text:
        text = text.replace(",", ".")
    elif _VN_THOUSANDS.match(text):
        text = text.replace(".", "")
    if not _NUMBER.match(text):
        return None
    integer, _, fraction = text.partition(".")
    integer = integer.lstrip("0") or "0"
    fraction = fraction.rstrip("0")
    return f"{integer}.{fraction}" if fraction else integer


_DATE_WORDS = re.compile(r"ng\w*\s+(\d{1,2})\s+th\w*\s+(\d{1,2})\s+n\w*\s+(\d{4})")
_DATE_SLASH = re.compile(r"\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\b")


def parse_date(raw: str) -> str | None:
    """'Ngày 05 tháng 10 năm 2026' hoặc '05/10/2026' -> '2026-10-05'; ngày không hợp lệ trả None."""
    key = match_key(raw)
    m = _DATE_WORDS.search(key) or _DATE_SLASH.search(raw)
    if not m:
        return None
    try:
        return date(int(m.group(3)), int(m.group(2)), int(m.group(1))).isoformat()
    except ValueError:
        return None


_TAX_CODE = re.compile(r"\b(\d{10}(?:-\d{3})?)\b")


def find_tax_code(raw: str) -> str | None:
    """MST 10 hoặc 13 số (0123456789-001). Không tự sửa chữ số đọc sai."""
    m = _TAX_CODE.search(raw.replace(" ", ""))
    return m.group(1) if m else None


def is_integer(raw: str) -> bool:
    return bool(re.fullmatch(r"\d{1,4}\.?", raw.strip()))
