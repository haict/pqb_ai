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
_DOC_TAX_RATE = re.compile(r"^thu\w* su\w*t")
_TOTALS = (
    ("total", re.compile(r"^t\w*ng t\w*n thanh|^t\w*ng c\w*ng")),
    ("vat", re.compile(r"^(t\w*ng )?t\w*n thu")),
    ("goods", re.compile(r"^c\w*ng t\w*n h\w*ng")),
)
_NUMERIC_COLUMNS = ("quantity", "unitPrice", "discountPercent", "taxRate", "amount")
_TEXT_COLUMNS = ("name", "unit")


@dataclass(frozen=True)
class DocumentSpec:
    """Vùng đối tác: từ dòng khớp `partner_start` (None = đầu trang 1) đến trước dòng khớp `partner_end`.

    `name_from_label`: tên đối tác nằm sau dấu ':' của nhãn bắt đầu; nếu False lấy đoạn chữ đầu tiên của vùng.
    """

    partner_start: re.Pattern | None
    partner_end: re.Pattern
    name_from_label: bool
    has_series: bool = False
    has_delivery_address: bool = False


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
    block = _partner_block(rows, spec)
    header = {
        "documentNo": _document_no(rows, policy),
        "documentDate": _document_date(rows, policy),
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


def _partner_block(rows: list[Row], spec: DocumentSpec) -> list[Row]:
    start = 0
    if spec.partner_start is not None:
        start = next((i for i, r in enumerate(rows) if _row_matches(r, spec.partner_start)), len(rows))
    end = next((i for i in range(start + 1, len(rows)) if _row_matches(rows[i], spec.partner_end)), len(rows))
    return rows[start:end]


def _row_matches(row: Row, pattern: re.Pattern) -> bool:
    return any(pattern.search(c.key) for c in row.cells)


def _partner_name(block: list[Row], spec: DocumentSpec, policy: FieldPolicy) -> OcrField:
    if not block:
        return unknown_field()
    if spec.name_from_label:
        return _labelled(block[:1], spec.partner_start, policy, text=True)
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


def _document_no(rows: list[Row], policy: FieldPolicy) -> OcrField:
    for row in rows:
        for cell in row.cells:
            m = _DOC_NO.match(cell.text)
            if m and m.group(1)[0] in "Ss":
                return ocr_field(m.group(2).strip(), [cell], policy)
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
    for row in rows:
        for cell in row.cells:
            if _DOC_TAX_RATE.search(cell.key) and parse_number(after_colon(cell.text) or "") is not None:
                return cell
    return None


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
        if column in _NUMERIC_COLUMNS:
            cell = next((c for c in cells if parse_number(c.text) is not None), None)
            result[column] = ocr_field(parse_number(cell.text), [cell], policy) if cell else unknown_field()
        elif cells:
            value = " ".join(c.text for c in cells)
            result[column] = ocr_field(value, cells, policy, text=column in _TEXT_COLUMNS)
        else:
            result[column] = unknown_field()
    if result["taxRate"].value is None and doc_tax_rate is not None:
        # Hóa đơn ghi một thuế suất chung ở phần tổng: áp cho mọi dòng, vẫn là giá trị đọc từ OCR.
        result["taxRate"] = ocr_field(parse_number(after_colon(doc_tax_rate.text)), [doc_tax_rate], policy)
    return result
