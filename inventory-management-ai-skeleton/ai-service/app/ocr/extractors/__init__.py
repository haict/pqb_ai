from app.ocr.extractors import purchase_invoice, purchase_order, sales_order
from app.ocr.extractors.base import DocumentSpec
from app.schemas import DocumentType

SPECS: dict[DocumentType, DocumentSpec] = {
    DocumentType.PURCHASE_INVOICE: purchase_invoice.SPEC,
    DocumentType.PURCHASE_ORDER: purchase_order.SPEC,
    DocumentType.SALES_ORDER: sales_order.SPEC,
}
