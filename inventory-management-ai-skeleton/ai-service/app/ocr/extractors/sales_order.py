"""Phiếu đặt hàng của khách: đối tác là bên phát hành ở đầu trang, trước phần "Kính gửi" (doanh nghiệp)."""
import re

from app.ocr.extractors.base import DocumentSpec

SPEC = DocumentSpec(
    partner_start=None,
    partner_end=re.compile(r"^kinh g"),
    name_from_label=False,
    has_delivery_address=True,
)
