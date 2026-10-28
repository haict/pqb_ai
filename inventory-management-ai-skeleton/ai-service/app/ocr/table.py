"""Tìm bảng hàng hóa theo tiêu đề cột và tách dòng hàng (docs/14 mục 4.3 bước 3).

Nhãn được so trên khóa đã bỏ dấu và cho phép thiếu ký tự, vì model OCR hiện tại làm rơi nhiều chữ có dấu
(ví dụ "Số lượng" đọc thành "S lưrng").
"""
import math
import re
from dataclasses import dataclass, field

from app.ocr.layout import Cell, Row
from app.ocr.normalize import is_integer, parse_number

LINE_COLUMNS = ("code", "name", "unit", "quantity", "unitPrice", "discountPercent", "taxRate", "amount")
_NUMERIC = frozenset(("quantity", "unitPrice", "discountPercent", "taxRate", "amount"))

# Thứ tự kiểm tra quan trọng: "Thành tiền chưa thuế" phải vào amount trước khi xét taxRate.
_COLUMN_RULES = (
    ("stt", re.compile(r"^stt")),
    ("amount", re.compile(r"^thanh\b")),
    ("code", re.compile(r"^m\w? hang")),
    ("name", re.compile(r"^t\w*n hang|hang hoa|d\w*ch v")),
    ("unit", re.compile(r"^dvt|^d\w*n v\w* tinh|^tinh$")),
    ("quantity", re.compile(r"^s\w?$|^so\b|^s\w? l\w*ng|^l\w*ng$|\bl\w*ng$")),
    ("unitPrice", re.compile(r"gia\b|^d\w* g\w*a$")),
    ("discountPercent", re.compile(r"^ck\b|^chi\w*t kh|^k$")),
    ("taxRate", re.compile(r"^thu")),
)

_END_OF_TABLE = re.compile(r"^(c\w*ng t\w*n|t\w*ng c\w*ng|t\w*ng t\w*n|c\w*ng\b|s\w* t\w*n vi\w*t|ng\w*i (lap|mua|ban)"
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
        if _is_end(row, stt):
            end = i
            break
        assigned = [(_column_of(columns, cell), cell) for cell in row.cells]
        starts_line = any(name == "stt" for name, _ in assigned)
        if starts_line:
            lines.append(TableLine())
        if not lines:
            continue
        for name, cell in assigned:
            if not starts_line:
                name = _continuation_column(name, cell, lines[-1])
            if name and name != "stt":
                lines[-1].add(name, cell)
    return Table(first_row=min(header_rows), end_row=end, lines=lines)


def _continuation_column(name: str | None, cell: Cell, line: TableLine) -> str | None:
    """Dòng không có STT là phần xuống dòng của dòng hàng trước: chữ thuộc tên hàng (mã hàng không xuống dòng),
    số chỉ nhận khi cột đó của dòng hàng còn trống (ảnh chụp nghiêng làm số lệch dòng)."""
    if name in _NUMERIC and parse_number(cell.text) is not None:
        return name if name not in line.cells else None
    return "name"


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


def _is_end(row: Row, stt: Column | None) -> bool:
    if any(_END_OF_TABLE.match(c.key) for c in row.cells):
        return True
    first = row.cells[0]
    # Dòng chữ bắt đầu trước giữa tiêu đề STT mà không phải số thứ tự: đã ra khỏi bảng (phần tổng, ghi chú).
    return stt is not None and first.x1 < stt.header_center and not is_integer(first.text)
