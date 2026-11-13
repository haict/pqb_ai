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


def _token(text: str, x1: int, x2: int, y: int):
    from app.ocr.engine import Token
    return Token(text=text, score=0.99, box=(x1, y, x2, y + 30), poly=((x1, y), (x2, y), (x2, y + 30), (x1, y + 30)),
                 page=1)


def test_english_lot_and_date_headers_without_stt():
    # Bảng NCC nước ngoài (Glenbio): không có STT, tiêu đề "LOT" / "date" thay cho "Số lô" / "Hạn dùng".
    columns = ((375, 660), (666, 1620), (1620, 1800), (1800, 2079), (2079, 2355))
    rows = (("Mã hàng", "Tên hàng", "số lượng", "LOT", "date"),
            ("GLAU2204", "HbA1c 4x14ml, 4x6ml, 4x9ml, 4x32ml", "5", "A058", "31/10/2027"),
            ("GLM406", "CK MB 3x35ml 2x10ml", "2", "9991", "31/12/2027"))
    tokens = [_token(text, x1, x2, 300 + 40 * r) for r, row in enumerate(rows) for text, (x1, x2) in zip(row, columns)]
    lines = extract(tokens, SPECS[DocumentType.PURCHASE_ORDER], POLICY).lines
    assert [(l["code"].value, l["quantity"].value, l["lotNo"].value, l["expiryDate"].value) for l in lines] == [
        ("GLAU2204", "5", "A058", "2027-10-31"), ("GLM406", "2", "9991", "2027-12-31")]


def test_short_headers_ma_ten_with_warehouse_column_on_sales_order():
    # Bảng kê xuất (Glenbio): tiêu đề chỉ "Mã" / "Tên", có cột "KHO XUẤT", "ĐVT chính" hai dòng, không STT.
    columns = ((30, 165), (170, 990), (995, 1270), (1275, 1410), (1415, 1580), (1585, 1675), (1680, 1770))
    header = ("Mã", "Tên", "KHO XUẤT", "LOT", "DATE", None, "Số lượng")
    rows = (("GDOA9613", "ALCOHOL CALIBRATOR Hộp/ R1: 1x5ml, R2: 1x5ml (Glenbio/Anh)", "SINH HÓA", "9813",
             "30/05/2027", "Hộp", "2.0"),
            ("H031111 N", "BloodTrol 3D (N) Bộ/3 lọ x 3ml (SFRI SAS/ Pháp)", "SINH HÓA", None, "ĐANG BAY", "Lọ",
             "8.0"),
            ("GL8S304", "CRP (6x20ml 6x4ml)", "SINH HÓA", None, None, "Hộp", "2.0"))
    tokens = [_token(text, x1, x2, 60) for text, (x1, x2) in zip(header, columns) if text]
    tokens += [_token("ĐVT", 1590, 1650, 42), _token("chính", 1590, 1660, 78)]
    tokens += [_token(text, x1, x2, 120 + 40 * r) for r, row in enumerate(rows)
               for text, (x1, x2) in zip(row, columns) if text]
    result = extract(tokens, SPECS[DocumentType.SALES_ORDER], POLICY)
    assert [(l["code"].value, l["unit"].value, l["quantity"].value, l["lotNo"].value, l["expiryDate"].value)
            for l in result.lines] == [("GDOA9613", "Hộp", "2", "9813", "2027-05-30"),
                                        ("H031111 N", "Lọ", "8", None, None),
                                        ("GL8S304", "Hộp", "2", None, None)]
    assert result.lines[0]["name"].value.startswith("ALCOHOL CALIBRATOR")
    # Tiêu đề cột và hạn dùng trong bảng không bị lấy làm khách hàng / ngày đơn.
    assert result.header["partnerName"].value is None
    assert result.header["documentDate"].value is None
    assert result.warnings == []
