"""Tìm bảng hàng hóa theo tiêu đề cột và tách dòng hàng (docs/14 mục 4.3 bước 3).

Nhãn được so trên khóa đã bỏ dấu và cho phép thiếu ký tự, vì model OCR hiện tại làm rơi nhiều chữ có dấu
(ví dụ "Số lượng" đọc thành "S lưrng").
"""
import math
import re
from dataclasses import dataclass, field, replace

from app.ocr.layout import Cell, Row
from app.ocr.normalize import is_integer, parse_number

LINE_COLUMNS = ("code", "name", "unit", "quantity", "unitPrice", "discountPercent", "taxRate", "amount", "lotNo",
                "expiryDate")
# Cột có trên chứng từ nhưng form không dùng: đơn giá sau thuế, số lượng quy đổi, kho xuất ("Từ").
IGNORED = "ignored"
_NUMERIC = frozenset(("quantity", "unitPrice", "discountPercent", "taxRate", "amount"))

# Thứ tự kiểm tra quan trọng: "Thành tiền chưa thuế" phải vào amount trước khi xét taxRate.
_COLUMN_RULES = (
    ("stt", re.compile(r"^stt")),
    (IGNORED, re.compile(r"gia sau thu|^sl quy|^tu$")),
    ("amount", re.compile(r"^thanh\b|^ti\w*n$")),
    ("lotNo", re.compile(r"^(m\w?|s\w?) (lo|l0|16)\b|^lo\b")),
    ("expiryDate", re.compile(r"^h\w*n (sd|s\w* d|d\w*ng)|^hsd\b")),
    ("code", re.compile(r"^m\w? hang")),
    ("name", re.compile(r"^t\w*n hang|hang hoa|d\w*ch v|^di\w*n g\w*i|^s\w*n ph")),
    ("unit", re.compile(r"^dvt|^d\w*n v\w* tinh|^tinh$|^d\w*n v\w*$")),
    ("quantity", re.compile(r"^s\w?$|^so\b|^s\w? l\w*ng|^l\w*ng$|\bl\w*ng$")),
    ("unitPrice", re.compile(r"gia\b|^d\w* g\w*a$")),
    ("discountPercent", re.compile(r"^ck\b|^chi\w*t kh|^k$")),
    ("taxRate", re.compile(r"^thu")),
)

_END_OF_TABLE = re.compile(r"^(c\w*ng t\w*n|t\w*ng c\w*ng|t\w*ng t\w*n|c\w*ng\b|s\w* t\w*n vi\w*t"
                           r"|ng\w*i (l\w*p|mua|ban|nh\w*n|giao)"
                           r"|d\w*a ch\w* giao|giao h\w*ng|thu\w* su\w*t)")


@dataclass
class Column:
    name: str | None
    lo: float
    hi: float
    header_center: float


@dataclass
class TableLine:
    cells: dict[str, list[Cell]] = field(default_factory=dict)

    def add(self, column: str, cell: Cell) -> None:
        self.cells.setdefault(column, []).append(cell)


@dataclass
class Table:
    first_row: int
    end_row: int
    lines: list[TableLine]


def find_table(rows: list[Row]) -> Table | None:
    anchor = next((i for i, r in enumerate(rows) if any(c.key.startswith("stt") for c in r.cells)), None)
    if anchor is None:
        anchor = next((i for i, r in enumerate(rows) if _is_header_without_stt(r)), None)
    if anchor is None:
        return None
    header_rows = [i for i in range(max(anchor - 1, 0), min(anchor + 2, len(rows))) if _is_header_row(rows, anchor, i)]
    columns = _columns([c for i in header_rows for c in rows[i].cells])
    if not any(col.name == "quantity" for col in columns):
        return None
    stt = next((col for col in columns if col.name == "stt"), None)
    lines: list[TableLine] = []
    end = len(rows)
    for i in range(max(header_rows) + 1, len(rows)):
        row = rows[i]
        cells = [part for cell in row.cells for part in _split_leading_columns(cell, columns)]
        if _is_end(cells, stt):
            end = i
            break
        if not stt:
            cells = [part for cell in cells for part in _split_number_prefix(cell)]
        assigned = [(_column_of(columns, cell), cell) for cell in cells]
        if stt:
            starts_line = any(name == "stt" for name, _ in assigned)
        else:
            # Bảng không có STT: dòng hàng mới bắt đầu ở dòng có số lượng.
            starts_line = any(name == "quantity" and parse_number(cell.text) is not None for name, cell in assigned)
        if starts_line:
            lines.append(TableLine())
        if not lines:
            continue
        for name, cell in assigned:
            if not starts_line:
                name = _continuation_column(name, cell, lines[-1])
            if name and name not in ("stt", IGNORED):
                lines[-1].add(name, cell)
    return Table(first_row=min(header_rows), end_row=end, lines=lines)


def _continuation_column(name: str | None, cell: Cell, line: TableLine) -> str | None:
    """Dòng không có STT là phần xuống dòng của dòng hàng trước: chữ thuộc tên hàng (mã hàng không xuống dòng),
    số chỉ nhận khi cột đó của dòng hàng còn trống (ảnh chụp nghiêng làm số lệch dòng)."""
    if name in _NUMERIC and parse_number(cell.text) is not None:
        return name if name not in line.cells else None
    if name == IGNORED or name in _NUMERIC:
        # Chữ trong cột bỏ qua hoặc cột số (số lô / hạn dùng viết tay, "Test (10 Hộp)") không thuộc tên hàng.
        return None
    return "name"


def _is_header_without_stt(row: Row) -> bool:
    names = {_classify(cell.key) for cell in row.cells}
    return {"name", "quantity"} <= names and len(names - {None, IGNORED}) >= 3


def _split_number_prefix(cell: Cell) -> list[Cell]:
    """OCR có thể gộp số lượng với ô bên phải ("500,00 K031/Tồn"): tách số đầu thành ô riêng theo vị trí ký tự."""
    head, sep, tail = cell.text.partition(" ")
    if not sep or parse_number(head) is None or parse_number(tail) is not None:
        return [cell]
    split = cell.x1 + (cell.x2 - cell.x1) * (len(head) + 0.5) / len(cell.text)
    return [replace(cell, x1=cell.x1, x2=split, token=replace(cell.token, text=head)),
            replace(cell, x1=split, x2=cell.x2, token=replace(cell.token, text=tail))]


def _split_leading_columns(cell: Cell, columns: list[Column]) -> list[Cell]:
    """OCR có thể gộp STT, mã hàng với đầu tên hàng ("3 155-000003 Bơm tiêm..."): tách các từ đầu nằm ở cột STT / mã
    hàng (mỗi cột một từ) thành ô riêng theo vị trí ký tự, để dòng không bị coi là hết bảng và mã hàng không lẫn vào tên."""
    parts: list[Cell] = []
    taken: set[str] = set()
    rest = cell
    while True:
        head, sep, tail = rest.text.partition(" ")
        if not sep:
            break
        split = rest.x1 + (rest.x2 - rest.x1) * (len(head) + 0.5) / len(rest.text)
        column = _column_at(columns, (rest.x1 + split) / 2)
        head_column = column.name if column else None
        tail_column = _column_at(columns, (split + rest.x2) / 2)
        # Ô phải bắt đầu trước giữa tiêu đề cột: tên hàng căn trái nằm sát cột mã không bị tách.
        if (head_column not in ("stt", "code") or head_column in taken or rest.x1 >= column.header_center
                or tail_column is None or tail_column.name == head_column):
            break
        # STT là số nguyên; mã hàng có chữ số (tên hàng căn trái có thể lấn sang vùng cột mã).
        if not (is_integer(head) if head_column == "stt" else any(ch.isdigit() for ch in head)):
            break
        taken.add(head_column)
        parts.append(replace(rest, x2=split, token=replace(rest.token, text=head)))
        rest = replace(rest, x1=split, token=replace(rest.token, text=tail.strip()))
    return parts + [rest]


def _column_at(columns: list[Column], x: float) -> Column | None:
    return next((col for col in columns if col.lo <= x < col.hi), None)


def _is_header_row(rows: list[Row], anchor: int, i: int) -> bool:
    row, base = rows[i], rows[anchor]
    if i == anchor:
        return True
    if row.page != base.page or abs(row.cy - base.cy) > 1.3 * base.height:
        return False
    # Dòng tiêu đề thứ hai không có số và không có nhãn "Nhãn: giá trị".
    return not any(parse_number(c.text) is not None or ":" in c.text for c in row.cells)


def _columns(cells: list[Cell]) -> list[Column]:
    """Gom ô tiêu đề chồng nhau theo chiều ngang (tiêu đề hai dòng: "Số" / "lượng") thành một cột."""
    clusters: list[list[Cell]] = []
    for cell in sorted(cells, key=lambda c: c.x1):
        if clusters and cell.x1 < max(c.x2 for c in clusters[-1]):
            clusters[-1].append(cell)
        else:
            clusters.append([cell])
    spans = []
    for cluster in clusters:
        key = " ".join(c.key for c in sorted(cluster, key=lambda c: c.cy))
        spans += _split_merged(key, min(c.x1 for c in cluster), max(c.x2 for c in cluster))
    columns = []
    for i, (name, x1, x2) in enumerate(spans):
        lo = -math.inf if i == 0 else (spans[i - 1][2] + x1) / 2
        hi = math.inf if i == len(spans) - 1 else (x2 + spans[i + 1][1]) / 2
        columns.append(Column(name=name, lo=lo, hi=hi, header_center=(x1 + x2) / 2))
    return columns


def _split_merged(key: str, x1: float, x2: float) -> list[tuple[str | None, float, float]]:
    """OCR có thể đọc hai tiêu đề sát nhau thành một đoạn ("Đơn vị tính Số lượng"): tách theo vị trí ký tự."""
    words = key.split()
    for i in range(1, len(words)):
        left, right = " ".join(words[:i]), " ".join(words[i:])
        a, b = _classify(left), _classify(right)
        if a and b and a != b and _classify(key) in (a, None):
            split = x1 + (x2 - x1) * (len(left) + 0.5) / len(key)
            return [(a, x1, split), (b, split, x2)]
    return [(_classify(key), x1, x2)]


def _classify(key: str) -> str | None:
    return next((name for name, pattern in _COLUMN_RULES if pattern.search(key)), None)


def _column_of(columns: list[Column], cell: Cell) -> str | None:
    for i, col in enumerate(columns):
        if col.lo <= cell.cx < col.hi:
            # Chữ tràn sang vùng STT (tên hàng căn trái, ngắn) thuộc cột kế tiếp; STT chỉ nhận số thứ tự.
            if col.name == "stt" and not is_integer(cell.text):
                return columns[i + 1].name if i + 1 < len(columns) else None
            return col.name
    return None


def _is_end(cells: list[Cell], stt: Column | None) -> bool:
    if any(_END_OF_TABLE.match(c.key) for c in cells):
        return True
    first = cells[0]
    # Dòng chữ bắt đầu trước giữa tiêu đề STT mà không phải số thứ tự: đã ra khỏi bảng (phần tổng, ghi chú).
    return stt is not None and first.x1 < stt.header_center and not is_integer(first.text)
