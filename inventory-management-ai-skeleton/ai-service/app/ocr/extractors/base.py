"""Trích trường chung cho mọi loại chứng từ; mỗi loại chỉ khai báo vùng thông tin đối tác (DocumentSpec)."""
import re
from dataclasses import dataclass

from app.ocr.engine import Token
from app.ocr.fields import FieldPolicy, after_colon, ocr_field, unknown_field
from app.ocr.layout import Cell, Row, build_rows
from app.ocr.normalize import find_tax_code, parse_date, parse_number
from app.ocr.table import LINE_COLUMNS, Table, TableLine, find_table
from app.schemas import OcrField

TABLE_NOT_FOUND = "TABLE_NOT_FOUND"

_TAX_CODE_LABEL = re.compile(r"^m\w* s\w* thu")
_ADDRESS_LABEL = re.compile(r"^d\w*a ch\w*\b(?! giao)")
_DELIVERY_LABEL = re.compile(r"^d\w*a ch\w* giao h")
_SERIES_LABEL = re.compile(r"^k\w* hi\w*\b")
_DOC_NO = re.compile(r"^\s*(S\S{0,2})\s*:\s*(\S.*)$")
_CURRENCY = re.compile(r"\b(VND|USD|EUR|JPY|CNY|GBP)\b")
_DOC_TAX_RATE = re.compile(r"^thu\w* [sx]u\w*t")
_TOTALS = (
    ("total", re.compile(r"^t\w*ng t\w*n thanh|^t\w*ng c\w*ng")),
    ("vat", re.compile(r"^(t\w*ng )?t\w*n thu")),
    ("goods", re.compile(r"^c\w*ng t\w*n h\w*ng")),
)
_NUMERIC_COLUMNS = ("quantity", "unitPrice", "discountPercent", "taxRate", "amount")
_TEXT_COLUMNS = ("name", "unit")
# Mã hàng in trong ngoặc vuông ở đầu tên hàng: "[R0023S] Que thử ...".
_CODE_IN_NAME = re.compile(r"^\[([A-Za-z0-9._-]+)\]\s*(.+)$")


@dataclass(frozen=True)
class DocumentSpec:
    """Vùng đối tác: từ dòng khớp `partner_start` (None = đầu trang 1) đến trước dòng khớp `partner_end`.

    `name_from_label`: tên đối tác nằm sau dấu ':' của nhãn bắt đầu; nếu False lấy đoạn chữ đầu tiên của vùng.
    `partner_name_pattern`: tên đối tác là đoạn chữ đầu tiên của vùng khớp mẫu (ví dụ "Công ty ..."); không có thì để trống.
    `date_after_partner`: ngày chứng từ chỉ tìm sau vùng đối tác (đầu trang có ngày ban hành mẫu theo Thông tư).
    `document_no_pattern`: số chứng từ in không có nhãn "Số:" (ví dụ "K031/OUT/17558"), tìm sau vùng đối tác.
    """

    partner_start: re.Pattern | None
    partner_end: re.Pattern
    name_from_label: bool
    has_series: bool = False
    has_delivery_address: bool = False
    partner_name_pattern: re.Pattern | None = None
    date_after_partner: bool = False
    document_no_pattern: re.Pattern | None = None


@dataclass
class Extraction:
    header: dict[str, OcrField]
    lines: list[dict[str, OcrField]]
    totals: dict[str, OcrField]
    warnings: list[str]


def extract(tokens: list[Token], spec: DocumentSpec, policy: FieldPolicy) -> Extraction:
    rows = build_rows(tokens)
    table = find_table(rows)
    outside = [r for i, r in enumerate(rows) if table is None or not table.first_row <= i < table.end_row]
    after_table = rows[table.end_row:] if table else rows
    doc_tax_rate = _doc_tax_rate(after_table)
    lines = [_line(line, doc_tax_rate, policy) for line in table.lines] if table else []
    return Extraction(
        header=_header(outside, spec, policy),
        lines=lines,
        totals=_totals(after_table, policy),
        warnings=[] if table and table.lines else [TABLE_NOT_FOUND],
    )


def _header(rows: list[Row], spec: DocumentSpec, policy: FieldPolicy) -> dict[str, OcrField]:
    start, end = _partner_range(rows, spec)
    block = rows[start:end]
    after_partner = rows[end:]
    header = {
        "documentNo": _document_no(rows, policy, after_partner, spec.document_no_pattern),
        "documentDate": _document_date(after_partner if spec.date_after_partner else rows, policy),
        "partnerName": _partner_name(block, spec, policy),
        "partnerTaxCode": _labelled(block, _TAX_CODE_LABEL, policy, parse=find_tax_code),
        "partnerAddress": _labelled(block, _ADDRESS_LABEL, policy, text=True),
        "currency": _currency(rows, policy),
    }
    if spec.has_series:
        header["invoiceSeries"] = _labelled(rows, _SERIES_LABEL, policy)
    if spec.has_delivery_address:
        header["deliveryAddress"] = _labelled(rows, _DELIVERY_LABEL, policy, text=True)
    return header


def _partner_range(rows: list[Row], spec: DocumentSpec) -> tuple[int, int]:
    start = 0
    if spec.partner_start is not None:
        start = next((i for i, r in enumerate(rows) if _row_matches(r, spec.partner_start)), len(rows))
    end = next((i for i in range(start + 1, len(rows)) if _row_matches(rows[i], spec.partner_end)), len(rows))
    return start, end


def _row_matches(row: Row, pattern: re.Pattern) -> bool:
    return any(pattern.search(c.key) for c in row.cells)


def _partner_name(block: list[Row], spec: DocumentSpec, policy: FieldPolicy) -> OcrField:
    if not block:
        return unknown_field()
    if spec.name_from_label:
        return _labelled(block[:1], spec.partner_start, policy, text=True)
    if spec.partner_name_pattern is not None:
        cell = next((c for r in block for c in r.cells if spec.partner_name_pattern.search(c.key)), None)
        return ocr_field(cell.text, [cell], policy, text=True) if cell else unknown_field()
    first = block[0].cells[0]
    if ":" in first.text:
        return unknown_field()
    return ocr_field(first.text, [first], policy, text=True)


def _labelled(rows: list[Row], label: re.Pattern, policy: FieldPolicy, *, parse=None, text=False) -> OcrField:
    for row in rows:
        for i, cell in enumerate(row.cells):
            if not label.search(cell.key):
                continue
            raw = after_colon(cell.text)
            cells = [cell]
            if raw is None and i + 1 < len(row.cells):
                cells.append(row.cells[i + 1])
                raw = row.cells[i + 1].text
            value = parse(raw) if parse and raw else raw
            if value:
                return ocr_field(value, cells, policy, raw=" ".join(c.text for c in cells), text=text)
    return unknown_field()


def _document_no(rows: list[Row], policy: FieldPolicy, after_partner: list[Row],
                 unlabelled: re.Pattern | None) -> OcrField:
    for row in rows:
        for cell in row.cells:
            m = _DOC_NO.match(cell.text)
            if m and m.group(1)[0] in "Ss":
                return ocr_field(m.group(2).strip(), [cell], policy)
    if unlabelled is not None:
        cell = next((c for r in after_partner for c in r.cells if unlabelled.match(c.text)), None)
        if cell is not None:
            return ocr_field(cell.text, [cell], policy)
    return unknown_field()


def _document_date(rows: list[Row], policy: FieldPolicy) -> OcrField:
    for row in rows:
        for cell in row.cells:
            value = parse_date(cell.text)
            if value:
                return ocr_field(value, [cell], policy)
    return unknown_field()


def _currency(rows: list[Row], policy: FieldPolicy) -> OcrField:
    for row in rows:
        for cell in row.cells:
            m = _CURRENCY.search(cell.text.upper())
            if m:
                return ocr_field(m.group(1), [cell], policy)
    return unknown_field()


def _doc_tax_rate(rows: list[Row]) -> Cell | None:
    """Ô chứa thuế suất chung: sau dấu ':' của nhãn, hoặc ô kế tiếp ("Thuế suất GTGT:" | "5 %")."""
    for row in rows:
        for i, cell in enumerate(row.cells):
            if not _DOC_TAX_RATE.search(cell.key):
                continue
            if parse_number(after_colon(cell.text) or "") is not None:
                return cell
            if i + 1 < len(row.cells) and parse_number(row.cells[i + 1].text) is not None:
                return row.cells[i + 1]
    return None


def _rate_text(cell: Cell) -> str:
    return after_colon(cell.text) or cell.text


def _totals(rows: list[Row], policy: FieldPolicy) -> dict[str, OcrField]:
    totals = {name: unknown_field() for name, _ in _TOTALS}
    for row in rows:
        for i, cell in enumerate(row.cells):
            name = next((n for n, p in _TOTALS if p.search(cell.key)), None)
            if name is None or totals[name].value is not None:
                continue
            amount = next((c for c in reversed(row.cells[i + 1:]) if parse_number(c.text) is not None), None)
            if amount is not None:
                totals[name] = ocr_field(parse_number(amount.text), [amount], policy)
    return totals


def _line(line: TableLine, doc_tax_rate: Cell | None, policy: FieldPolicy) -> dict[str, OcrField]:
    result = {}
    for column in LINE_COLUMNS:
        cells = line.cells.get(column, [])
        if column == "expiryDate":
            value = parse_date(" ".join(c.text for c in cells)) if cells else None
            result[column] = ocr_field(value, cells, policy)
        elif column in _NUMERIC_COLUMNS:
            cell = next((c for c in cells if parse_number(c.text) is not None), None)
            result[column] = ocr_field(parse_number(cell.text), [cell], policy) if cell else unknown_field()
        elif cells:
            value = " ".join(c.text for c in cells)
            result[column] = ocr_field(value, cells, policy, text=column in _TEXT_COLUMNS)
        else:
            result[column] = unknown_field()
    if result["taxRate"].value is None and doc_tax_rate is not None:
        # Hóa đơn ghi một thuế suất chung ở phần tổng: áp cho mọi dòng, vẫn là giá trị đọc từ OCR.
        result["taxRate"] = ocr_field(parse_number(_rate_text(doc_tax_rate)), [doc_tax_rate], policy)
    _split_code_from_name(result, line, policy)
    return result


def _split_code_from_name(result: dict[str, OcrField], line: TableLine, policy: FieldPolicy) -> None:
    m = _CODE_IN_NAME.match(result["name"].value or "")
    if result["code"].value is None and m:
        cells = line.cells["name"]
        result["code"] = ocr_field(m.group(1), cells[:1], policy)
        result["name"] = ocr_field(m.group(2), cells, policy, text=True)
