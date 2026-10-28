"""Đơn đặt hàng doanh nghiệp gửi nhà cung cấp: đối tác nằm ở phần "Kính gửi", trên đầu là thông tin của chính doanh nghiệp."""
import re

from app.ocr.extractors.base import DocumentSpec

SPEC = DocumentSpec(
    partner_start=re.compile(r"^kinh g"),
    partner_end=re.compile(r"^d\w* ngh|^stt"),
    name_from_label=True,
)
