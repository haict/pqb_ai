import logging
import threading

from tests.conftest import API_KEY, png_bytes

HEADERS = {"X-AI-Service-Key": API_KEY}


def post(client, data: bytes, document_type: str = "PURCHASE_INVOICE", headers=HEADERS):
    return client.post("/internal/v1/ocr/extract", headers=headers,
                       files={"file": ("chung-tu.png", data, "image/png")}, data={"documentType": document_type})


def test_health_requires_key(client):
    assert client.get("/internal/v1/health").status_code == 401
    assert client.get("/internal/v1/health", headers={"X-AI-Service-Key": "wrong-key-0123456789"}).status_code == 401


def test_health_reports_ocr_ready(client, settings):
    body = client.get("/internal/v1/health", headers=HEADERS).json()
    assert body["status"] == "UP"
    assert body["ocr"] == {"ready": True, "detectionModel": settings.ocr_det_model,
                           "recognitionModel": settings.ocr_rec_model, "textDiacriticsReliable": False}


def test_extract_requires_key(client):
    response = post(client, png_bytes(), headers={})
    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_extract_returns_fields(client):
    response = post(client, png_bytes())
    assert response.status_code == 200
    body = response.json()
    assert body["documentType"] == "PURCHASE_INVOICE"
    assert body["pageCount"] == 1
    assert body["header"]["partnerTaxCode"]["value"] == "0300000001"
    assert len(body["lines"]) == 4
    assert body["lines"][0]["amount"] == {
        "value": "28160000", "rawText": "28.160.000", "matchKey": None, "source": "OCR",
        "confidence": body["lines"][0]["amount"]["confidence"], "status": "OK", "page": 1,
        "box": body["lines"][0]["amount"]["box"], "warnings": []}
    assert body["totals"]["total"]["value"] == "136574900"
    assert response.headers["X-Request-Id"]


def test_request_id_is_propagated(client):
    response = client.get("/internal/v1/health", headers={**HEADERS, "X-Request-Id": "abc-123"})
    assert response.headers["X-Request-Id"] == "abc-123"


def test_disguised_executable_is_415(client):
    response = post(client, b"MZ\x90\x00 this is an exe")
    assert response.status_code == 415
    assert response.json()["code"] == "OCR_UNSUPPORTED_TYPE"


def test_file_too_large_is_413(client, settings):
    response = post(client, png_bytes() + b"\0" * settings.ocr_max_file_size)
    assert response.status_code == 413
    assert response.json()["code"] == "OCR_FILE_TOO_LARGE"


def test_unknown_document_type_is_400(client):
    response = post(client, png_bytes(), document_type="SALES_INVOICE")
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_blank_page_is_422(client, engine):
    engine.tokens = []
    response = post(client, png_bytes())
    assert response.status_code == 422
    assert response.json()["code"] == "OCR_NO_TEXT"


def test_second_concurrent_request_is_429(client, engine):
    started, release = threading.Event(), threading.Event()
    original = engine.recognize

    def slow(image, page):
        started.set()
        release.wait(5)
        return original(image, page)

    engine.recognize = slow
    results = []
    worker = threading.Thread(target=lambda: results.append(post(client, png_bytes()).status_code))
    worker.start()
    assert started.wait(5)
    try:
        second = post(client, png_bytes())
        assert second.status_code == 429
        assert second.json()["code"] == "AI_BUSY"
    finally:
        release.set()
        worker.join(5)
    assert results == [200]


def test_errors_do_not_leak_internals(client, engine):
    def boom(image, page):
        raise RuntimeError("C:\\secret\\path 0300000001")

    engine.recognize = boom
    response = post(client, png_bytes())
    assert response.status_code == 500
    assert response.json()["code"] == "INTERNAL_ERROR"
    assert "secret" not in response.text and "0300000001" not in response.text


def test_logs_contain_no_document_content(client, caplog):
    logger = logging.getLogger("ai_service")
    logger.addHandler(caplog.handler)
    try:
        with caplog.at_level(logging.INFO, logger="ai_service"):
            assert post(client, png_bytes()).status_code == 200
    finally:
        logger.removeHandler(caplog.handler)
    text = caplog.text
    assert "documentType=PURCHASE_INVOICE" in text
    for secret in ("0300000001", "28.160.000", "28160000", "Glenbio", "chung-tu.png", API_KEY):
        assert secret not in text
