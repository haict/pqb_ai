"""Phiếu xuất kho / giao hàng của nhà cung cấp (bên bán phát hành, doanh nghiệp là người mua).

Nhà cung cấp là tên công ty in ở đầu trang, trước tiêu đề phiếu. Phiếu thường không in MST bên bán; MST in trên phiếu là
của người mua (chính doanh nghiệp) nên không dùng. Ngày phiếu tìm sau tiêu đề vì đầu trang có ngày ban hành mẫu theo
Thông tư (ví dụ "ngày 27 tháng 10 năm 2025 của Bộ trưởng Bộ Tài chính").
"""
import re

from app.ocr.extractors.base import DocumentSpec

SPEC = DocumentSpec(
    partner_start=None,
    partner_end=re.compile(r"^phi\w* x|^ng\w*i mua|^kh\w*ch h\w*ng"),
    name_from_label=False,
    partner_name_pattern=re.compile(r"^(c\w*ng ty|cty)\b"),
    date_after_partner=True,
    document_no_pattern=re.compile(r"^[A-Z0-9]{2,}(?:/[A-Z0-9]+){2,}$"),
)
