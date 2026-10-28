"""Gom token OCR thành dòng theo tọa độ. Ảnh chụp nghiêng được bù góc trước khi gom (docs/14 mục 4.3 bước 3)."""
import math
from dataclasses import dataclass, field
from statistics import median

from app.ocr.engine import Token
from app.ocr.normalize import match_key

# Chỉ đoạn chữ đủ dài mới cho góc nghiêng tin cậy.
_MIN_WIDTH_FOR_ANGLE = 80
_MIN_ANGLE_DEG = 0.3


@dataclass
class Cell:
    token: Token
    x1: float
    x2: float
    cy: float
    height: float

    @property
    def cx(self) -> float:
        return (self.x1 + self.x2) / 2

    @property
    def text(self) -> str:
        return self.token.text.strip()

    @property
    def key(self) -> str:
        return match_key(self.token.text)


@dataclass
class Row:
    page: int
    cy: float
    height: float
    cells: list[Cell] = field(default_factory=list)

    @property
    def text(self) -> str:
        return " ".join(c.text for c in self.cells)

    @property
    def key(self) -> str:
        return match_key(self.text)


def build_rows(tokens: list[Token]) -> list[Row]:
    rows: list[Row] = []
    for page in sorted({t.page for t in tokens}):
        rows += _page_rows([t for t in tokens if t.page == page], page)
    return rows


def _page_rows(tokens: list[Token], page: int) -> list[Row]:
    angle = _skew_angle(tokens)
    cells = sorted((_cell(t, angle) for t in tokens), key=lambda c: c.cy)
    rows: list[Row] = []
    for cell in cells:
        last = rows[-1] if rows else None
        if last and abs(cell.cy - last.cy) < max(cell.height, last.height) * 0.5:
            last.cells.append(cell)
            last.cy = sum(c.cy for c in last.cells) / len(last.cells)
            last.height = max(last.height, cell.height)
        else:
            rows.append(Row(page=page, cy=cell.cy, height=cell.height, cells=[cell]))
    for row in rows:
        row.cells.sort(key=lambda c: c.x1)
    return rows


def _skew_angle(tokens: list[Token]) -> float:
    angles = []
    for t in tokens:
        (x1, y1), (x2, y2) = t.poly[0], t.poly[1]
        if x2 - x1 >= _MIN_WIDTH_FOR_ANGLE:
            angles.append(math.atan2(y2 - y1, x2 - x1))
    if not angles:
        return 0.0
    angle = median(angles)
    return angle if abs(math.degrees(angle)) >= _MIN_ANGLE_DEG else 0.0


def _cell(t: Token, angle: float) -> Cell:
    xs = [p[0] for p in t.poly]
    ys = [p[1] for p in t.poly]
    cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
    (ax, ay), (bx, by), (_, _), (dx, dy) = t.poly
    width = math.hypot(bx - ax, by - ay)
    height = math.hypot(dx - ax, dy - ay)
    if angle:
        cos, sin = math.cos(-angle), math.sin(-angle)
        cx, cy = cx * cos - cy * sin, cx * sin + cy * cos
    return Cell(token=t, x1=cx - width / 2, x2=cx + width / 2, cy=cy, height=max(height, 1.0))
