"""Hóa đơn GTGT của nhà cung cấp: đối tác là người bán, từ "Đơn vị bán hàng" đến phần người mua."""
import re

from app.ocr.extractors.base import DocumentSpec

SPEC = DocumentSpec(
    partner_start=re.compile(r"^d\w*n v\w* ban h\w*ng"),
    partner_end=re.compile(r"^h\w* t\w*n ng|^t\w*n d\w*n v|ng\w*i mua"),
    name_from_label=True,
    has_series=True,
)
