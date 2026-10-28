import pytest

from app.ocr.normalize import find_tax_code, is_integer, match_key, parse_date, parse_number, unaccent


@pytest.mark.parametrize("raw, expected", [
    ("1.234.567", "1234567"),
    ("1.234.567,50", "1234567.5"),
    ("7.040.000", "7040000"),
    ("12", "12"),
    ("0", "0"),
    ("5%", "5"),
    ("10 %", "10"),
    ("16,5", "16.5"),
    ("16.5", "16.5"),
    ("0012", "12"),
    ("1.101", "1101"),
])
def test_parse_number_vietnamese_format(raw, expected):
    assert parse_number(raw) == expected


@pytest.mark.parametrize("raw", ["", "abc", "•", "-", "1.2.3,4,5", "12a"])
def test_parse_number_rejects_non_numbers(raw):
    assert parse_number(raw) is None


@pytest.mark.parametrize("raw, expected", [
    ("Ngày 05 tháng 10 năm 2026", "2026-10-05"),
    ("Ngày 11 tháng 10 nām 2026", "2026-10-11"),
    ("Ngay 9 thang 1 nam 2026", "2026-01-09"),
    ("05/10/2026", "2026-10-05"),
    ("Ngày 31 tháng 02 năm 2026", None),
    ("không có ngày", None),
])
def test_parse_date(raw, expected):
    assert parse_date(raw) == expected


def test_tax_code_10_and_13_digits():
    assert find_tax_code("Mã s thu: 0300000001") == "0300000001"
    assert find_tax_code("MST: 0123456789-001") == "0123456789-001"
    assert find_tax_code("MST: 03000001") is None


def test_match_key_removes_diacritics_and_punctuation():
    assert unaccent("Đơn vị tính") == "Don vi tinh"
    assert match_key("Tên hàng hóa, dịch vụ") == "ten hang hoa dich vu"
    assert match_key("Hp") == "hp"


def test_is_integer():
    assert is_integer("12")
    assert is_integer("3.")
    assert not is_integer("7.040.000")
    assert not is_integer("STT")
