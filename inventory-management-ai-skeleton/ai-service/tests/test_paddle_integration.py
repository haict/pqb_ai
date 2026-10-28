"""Chạy model PaddleOCR thật qua API. Cần OCR_MODEL_DIR (model cài sẵn) và AI_SAMPLES_DIR (bộ chứng từ giả lập).

Thiếu một trong hai thì bị bỏ qua; báo cáo kiểm thử phải ghi rõ số test bỏ qua (không tính là đạt).
"""
import os
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from tests.conftest import API_KEY

MODEL_DIR = Path(os.environ.get("OCR_MODEL_DIR", ""))
SAMPLES_DIR = Path(os.environ.get("AI_SAMPLES_DIR", ""))

pytestmark = [
    pytest.mark.slow,
    pytest.mark.skipif(not (os.environ.get("OCR_MODEL_DIR") and (MODEL_DIR / "PP-OCRv5_mobile_det").is_dir()),
                       reason="Chưa có OCR_MODEL_DIR với model PP-OCRv5_mobile_det"),
    pytest.mark.skipif(not (os.environ.get("AI_SAMPLES_DIR") and (SAMPLES_DIR / "docs").is_dir()),
                       reason="Chưa có AI_SAMPLES_DIR (bộ chứng từ giả lập)"),
]


@pytest.fixture(scope="module")
def real_client():
    settings = Settings(ai_service_api_key=API_KEY, ocr_model_dir=MODEL_DIR)
    with TestClient(create_app(settings)) as client:
        yield client


def extract(client, file_name: str, document_type: str) -> dict:
    data = (SAMPLES_DIR / "docs" / file_name).read_bytes()
    response = client.post("/internal/v1/ocr/extract", headers={"X-AI-Service-Key": API_KEY},
                           files={"file": (file_name, data)}, data={"documentType": document_type})
    assert response.status_code == 200, response.text
    return response.json()


def test_pdf_invoice(real_client):
    body = extract(real_client, "INV01.pdf", "PURCHASE_INVOICE")
    assert body["header"]["partnerTaxCode"]["value"] == "0300000001"
    assert body["header"]["invoiceSeries"]["value"] == "1C26TAA"
    assert [line["amount"]["value"] for line in body["lines"]] == ["28160000", "8148000", "803000", "87048000"]
    assert body["totals"]["total"]["value"] == "136574900"


def test_phone_photo_customer_order(real_client):
    body = extract(real_client, "SO06.jpg", "SALES_ORDER")
    assert body["header"]["partnerTaxCode"]["value"] == "0100000002"
    assert [line["quantity"]["value"] for line in body["lines"]] == ["10", "20", "3", "2", "5"]


def test_blank_page_is_422(real_client):
    data = (SAMPLES_DIR / "docs" / "HC01.png").read_bytes()
    response = real_client.post("/internal/v1/ocr/extract", headers={"X-AI-Service-Key": API_KEY},
                                files={"file": ("HC01.png", data)}, data={"documentType": "PURCHASE_INVOICE"})
    assert response.status_code == 422
