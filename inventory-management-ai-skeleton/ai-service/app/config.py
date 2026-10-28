import os
from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Cấu hình đọc từ biến môi trường (docs/14 mục 10.2); không có giá trị bí mật mặc định."""

    model_config = SettingsConfigDict(extra="ignore")

    ai_service_host: str = "127.0.0.1"
    ai_service_port: int = 8090
    ai_service_api_key: SecretStr = Field(min_length=16)
    ai_service_enable_docs: bool = False

    ocr_model_dir: Path
    ocr_det_model: str = "PP-OCRv5_mobile_det"
    ocr_rec_model: str = "latin_PP-OCRv5_mobile_rec"
    ocr_cpu_threads: int = Field(default=4, ge=1)
    ocr_max_file_size: int = Field(default=10 * 1024 * 1024, ge=1)
    ocr_max_pdf_pages: int = Field(default=5, ge=1)
    ocr_max_image_pixels: int = Field(default=40_000_000, ge=1)
    ocr_max_image_side: int = Field(default=2500, ge=500)
    ocr_pdf_dpi: int = Field(default=144, ge=72, le=300)
    ocr_min_confidence: float = Field(default=0.85, ge=0, le=1)
    # Model hiện chọn thiếu nhiều chữ tiếng Việt có dấu (docs/14 mục 9.4): trường văn bản luôn cần người dùng kiểm tra.
    ocr_text_diacritics_reliable: bool = False

    # Trợ lý L1 (AI-3, docs/14 mục 5.5): LLM nội bộ trên Ollama + tìm đoạn tài liệu hướng dẫn bằng BM25.
    ollama_base_url: str = "http://127.0.0.1:11434"
    chat_model: str = "qwen2.5:3b"
    # Ngắn hơn ai.chat.timeout của Spring (90 s) để Spring nhận lỗi rõ ràng thay vì tự hết giờ.
    chat_timeout_seconds: float = Field(default=80, gt=0)
    chat_max_answer_tokens: int = Field(default=400, ge=50)
    chat_temperature: float = Field(default=0.2, ge=0, le=1)
    chat_context_tokens: int = Field(default=4096, ge=1024)
    chat_max_question_chars: int = Field(default=2000, ge=100)
    rag_index_dir: Path | None = None
    rag_top_k: int = Field(default=3, ge=1, le=10)
    rag_min_score: float = Field(default=5.0, ge=0)
    rag_max_context_chars: int = Field(default=6000, ge=500)

    log_level: str = "INFO"


@lru_cache
def get_settings() -> Settings:
    env_file = os.environ.get("AI_SERVICE_ENV_FILE")
    return Settings(_env_file=env_file) if env_file else Settings()
