"""Bộ trích xuất trên token OCR lưu sẵn của bộ chứng từ giả lập (không cần model)."""
import pytest

from app.ocr.extractors import SPECS
from app.ocr.extractors.base import TABLE_NOT_FOUND, extract
from app.ocr.fields import TEXT_DIACRITICS_UNRELIABLE, FieldPolicy
from app.schemas import DocumentType, FieldSource, FieldStatus
from tests.conftest import FIXTURES, fixture_tokens
from tools.common import document_type, load_index, read_json
from tools.scoring import score_document

POLICY = FieldPolicy(min_confidence=0.85, text_diacritics_reliable=False)
DOCUMENTS = load_index(FIXTURES)
# Trường mà sai lệch dẫn tới sai số tiền / sai đối tác: phải đúng tuyệt đối trên chứng từ PDF, PNG.
CRITICAL = {"partnerTaxCode", "documentDate", "invoiceSeries", "lineCount", "code", "quantity", "unitPrice",
            "discountPercent", "taxRate", "amount", "goods", "total"}


def run(key: str, doc_type: DocumentType):
    return extract(fixture_tokens(key), SPECS[doc_type], POLICY)


def expected(key: str) -> dict:
    return read_json(FIXTURES / "ground-truth" / f"{key}.json")


@pytest.mark.parametrize("row", [r for r in DOCUMENTS if r["kind"] != "PHOTO_JPG"], ids=lambda r: r["key"])
def test_critical_fields_exact_on_clean_documents(row):
    failed = [c for c in score_document(expected(row["key"]), run(row["key"], document_type(row)))
              if c.field in CRITICAL and not c.ok]
    assert failed == []


def test_overall_accuracy_including_photos():
    checks = [c for r in DOCUMENTS for c in score_document(expected(r["key"]), run(r["key"], document_type(r)))]
    assert sum(c.ok for c in checks) / len(checks) >= 0.98


def test_blank_page_has_no_table():
    result = run("HC01", DocumentType.PURCHASE_INVOICE)
    assert result.lines == []
    assert result.warnings == [TABLE_NOT_FOUND]


def test_multi_page_invoice_keeps_lines_across_pages():
    result = run("INV05", DocumentType.PURCHASE_INVOICE)
    assert len(result.lines) == 30
    assert result.lines[29]["quantity"].value == "6"
    assert result.lines[29]["quantity"].page == 2
    assert result.totals["total"].value == "1507674000"


def test_wrapped_name_goes_to_name_not_code():
    line = run("PO01", DocumentType.PURCHASE_ORDER).lines[1]
    assert line["code"].value == "GLM612"
    assert line["name"].value.endswith("(Glenbio/Anh)")


def test_printed_amount_kept_as_read_when_it_mismatches():
    # INV06 dòng 3 in thành tiền lệch 100.000: OCR giữ nguyên, kiểm tra số học là việc của Spring (MISMATCH).
    line = run("INV06", DocumentType.PURCHASE_INVOICE).lines[2]
    assert (line["quantity"].value, line["unitPrice"].value, line["amount"].value) == ("6", "6863000", "41278000")


def test_invoice_without_tax_column_uses_document_tax_rate():
    lines = run("INV01", DocumentType.PURCHASE_INVOICE).lines
    assert {line["taxRate"].value for line in lines} == {"10"}


def test_sales_order_without_prices_leaves_price_unknown():
    line = run("SO02", DocumentType.SALES_ORDER).lines[0]
    assert line["unitPrice"].value is None
    assert line["unitPrice"].source == FieldSource.UNKNOWN
    assert line["unitPrice"].status == FieldStatus.UNKNOWN


def test_partner_is_seller_on_invoice_and_issuer_on_customer_order():
    assert run("INV01", DocumentType.PURCHASE_INVOICE).header["partnerTaxCode"].value == "0300000001"
    assert run("PO01", DocumentType.PURCHASE_ORDER).header["partnerTaxCode"].value == "0300000001"
    assert run("SO01", DocumentType.SALES_ORDER).header["partnerTaxCode"].value == expected("SO01")["header"]["partnerTaxCode"]


def test_customer_order_without_tax_code_returns_unknown():
    assert run("SO04", DocumentType.SALES_ORDER).header["partnerTaxCode"].status == FieldStatus.UNKNOWN


def test_text_fields_flagged_low_confidence_with_match_key():
    result = run("INV01", DocumentType.PURCHASE_INVOICE)
    name = result.header["partnerName"]
    assert name.status == FieldStatus.LOW_CONFIDENCE
    assert TEXT_DIACRITICS_UNRELIABLE in name.warnings
    assert name.matchKey == "nha phan phi thit b mu"
    assert result.lines[0]["unit"].matchKey == "hp"


def test_numeric_field_contract():
    field = run("INV01", DocumentType.PURCHASE_INVOICE).lines[0]["amount"]
    assert field.value == "28160000"
    assert field.rawText == "28.160.000"
    assert field.source == FieldSource.OCR
    assert field.status == FieldStatus.OK
    assert field.page == 1 and len(field.box) == 4
    assert field.matchKey is None
