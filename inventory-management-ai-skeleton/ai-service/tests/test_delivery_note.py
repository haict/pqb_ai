"""Phiếu xuất kho / giao hàng của nhà cung cấp (PURCHASE_DELIVERY_NOTE) trên token OCR thật đã ẩn danh.

DN01: đầu trang bị cắt (không có tên NCC), hai cột đơn giá (sau thuế / chưa thuế), số lô viết tay trong cột giá.
DN02: không có cột STT, mã hàng trong ngoặc vuông ở đầu tên, OCR gộp số lượng với cột "Từ", có số lô / hạn dùng.
DN03: cột "Diễn giải" là tên hàng, "Mã lô", "Hạn SD", "SL quy đổi", "Tiền"; đầu trang có ngày ban hành mẫu (Thông tư).
DN04: cùng mẫu DN03, ảnh chụp cả trang; OCR gộp STT + mã hàng + đầu tên hàng của dòng cuối thành một ô.
"""
from app.ocr.extractors import SPECS
from app.ocr.extractors.base import extract
from app.ocr.fields import FieldPolicy
from app.schemas import DocumentType
from tests.conftest import FIXTURES
from tools.common import read_json, tokens_from_json

POLICY = FieldPolicy(min_confidence=0.85, text_diacritics_reliable=False)


def run(key: str):
    tokens = tokens_from_json(read_json(FIXTURES / "delivery-notes" / f"{key}.json"))
    return extract(tokens, SPECS[DocumentType.PURCHASE_DELIVERY_NOTE], POLICY)


def values(fields: dict) -> dict:
    return {name: field.value for name, field in fields.items() if field.value is not None}


def test_dn01_takes_price_before_tax_and_ignores_handwriting():
    result = run("DN01")
    assert result.header["partnerName"].value is None
    assert result.header["partnerTaxCode"].value is None  # MST in trên phiếu là của người mua: không dùng
    assert result.header["documentNo"].value == "BH0000001"
    assert result.header["documentDate"].value == "2026-11-02"
    assert len(result.lines) == 2
    for line in result.lines:
        assert values(line) | {"name": None} == {
            "code": "HARKL TEFLO", "name": None, "unit": "Cai", "quantity": "1000", "unitPrice": "2476.19",
            "taxRate": "5", "amount": "2476190"}
        assert "L0000" not in line["name"].value
    assert values(result.totals) == {"goods": "4952380", "vat": "247620", "total": "5200000"}


def test_dn02_without_stt_reads_code_in_name_lot_and_expiry():
    result = run("DN02")
    assert result.header["partnerName"].value == "CÔNG TY TNHH THIET BI MAU MOT"
    assert result.header["documentNo"].value == "K001/OUT/00001"
    assert result.header["documentDate"].value == "2026-11-03"
    assert [values(line) | {"name": None} for line in result.lines] == [
        {"code": "R0023S", "name": None, "quantity": "500", "lotNo": "F0001X0C01", "expiryDate": "2028-03-13"},
        {"code": "R0040S", "name": None, "quantity": "650", "lotNo": "F0002X0G01D", "expiryDate": "2028-02-20"},
    ]
    assert result.lines[0]["name"].value.startswith("Que th")
    assert "Test (10" not in result.lines[0]["name"].value  # "Test (10 Hộp)" thuộc cột số lượng, không vào tên hàng


def test_dn03_reads_lot_expiry_and_skips_circular_date():
    result = run("DN03")
    assert result.header["partnerName"].value == "CÔNG TY C PHN VT T MU HAI"
    assert result.header["documentNo"].value == "PX-0000001"
    assert result.header["documentDate"].value == "2026-11-05"  # không lấy ngày ban hành mẫu 27/10/2025
    lines = [values(line) for line in result.lines]
    assert [(l["code"], l["quantity"], l["unitPrice"], l["lotNo"], l["expiryDate"], l["taxRate"], l["amount"])
            for l in lines] == [
        ("155-000001", "6000", "561.9048", "0526", "2031-08-14", "5", "3371429"),
        ("155-000002", "3200", "1552.381", "0426", "2031-09-08", "5", "4967619"),
        ("155-000003", "4800", "847.619", "0426", "2031-08-18", "5", "4068571"),
    ]
    assert values(result.totals) == {"goods": "12407619", "vat": "620381", "total": "13028000"}


def test_dn04_keeps_last_line_when_ocr_merges_stt_code_and_name():
    # Ảnh chụp cả trang: OCR gộp "3 155-000003 Bom tiêm..." thành một ô trải qua cột STT, mã hàng, tên hàng;
    # trước đây dòng này bị coi là hết bảng nên mất dòng hàng cuối.
    result = run("DN04")
    assert result.header["documentNo"].value == "PX-0000001"
    lines = [values(line) for line in result.lines]
    assert [(l["code"], l["quantity"], l["unitPrice"], l["lotNo"], l["expiryDate"], l["amount"]) for l in lines] == [
        ("155-000001", "6000", "561.9048", "0526", "2031-08-14", "3371429"),
        ("155-000002", "3200", "1552.381", "0426", "2031-09-08", "4967619"),
        ("155-000003", "4800", "847.619", "0426", "2031-08-18", "4068571"),
    ]
    assert lines[2]["name"].startswith("Bom tiêm")
    assert values(result.totals) == {"goods": "12407619", "vat": "620381", "total": "13028000"}
