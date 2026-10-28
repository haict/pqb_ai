import io
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.config import Settings
from app.main import create_app
from app.ocr.engine import Token
from tools.common import read_json, tokens_from_json

FIXTURES = Path(__file__).parent / "fixtures"
API_KEY = "test-key-0123456789abcdef"


def fixture_tokens(key: str) -> list[Token]:
    return tokens_from_json(read_json(FIXTURES / "tokens" / f"{key}.json"))


class FakeEngine:
    """Trả token lưu sẵn thay cho PaddleOCR; ghi lại số trang được gọi."""

    def __init__(self, tokens: list[Token]):
        self.tokens = tokens
        self.pages: list[int] = []

    def recognize(self, image: np.ndarray, page: int) -> list[Token]:
        self.pages.append(page)
        return [t for t in self.tokens if t.page == page] if page == 1 else []


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    return Settings(ai_service_api_key=API_KEY, ocr_model_dir=tmp_path, ocr_max_file_size=2 * 1024 * 1024,
                    ocr_max_image_pixels=4_000_000, ocr_max_pdf_pages=2)


@pytest.fixture
def engine() -> FakeEngine:
    return FakeEngine(fixture_tokens("INV01"))


class FakeChatModel:
    """Thay Ollama: ghi lại tin nhắn gửi LLM, trả câu trả lời cố định."""

    def __init__(self, answer: str = "1. Mở màn hình Đơn mua hàng [1]."):
        self.answer = answer
        self.calls: list[list[dict[str, str]]] = []
        self.available = True

    def chat(self, messages: list[dict[str, str]]) -> str:
        self.calls.append(messages)
        return self.answer

    def ready(self) -> bool:
        return self.available


@pytest.fixture
def chat_model() -> FakeChatModel:
    return FakeChatModel()


@pytest.fixture
def client(settings: Settings, engine: FakeEngine, chat_model: FakeChatModel):
    app = create_app(settings, engine_factory=lambda _: engine, chat_model_factory=lambda _: chat_model)
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c


def png_bytes(width: int = 64, height: int = 48) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (width, height), "white").save(buf, format="PNG")
    return buf.getvalue()
