"""Gọi Ollama chạy nội bộ (127.0.0.1, docs/14 mục 8). Không ghi prompt hay câu trả lời vào log (BR-AI-10)."""
from typing import Protocol

import httpx

from app.config import Settings
from app.errors import ai_timeout, ai_unavailable
from app.logging_setup import log


class ChatModel(Protocol):
    def chat(self, messages: list[dict[str, str]]) -> str: ...

    def ready(self) -> bool: ...


class OllamaChatModel:
    def __init__(self, settings: Settings):
        self._settings = settings
        self._client = httpx.Client(base_url=settings.ollama_base_url,
                                    timeout=httpx.Timeout(settings.chat_timeout_seconds, connect=2.0))

    def chat(self, messages: list[dict[str, str]]) -> str:
        settings = self._settings
        body = {
            "model": settings.chat_model,
            "messages": messages,
            "stream": False,
            "options": {"temperature": settings.chat_temperature, "num_predict": settings.chat_max_answer_tokens,
                        "num_ctx": settings.chat_context_tokens},
        }
        try:
            response = self._client.post("/api/chat", json=body)
        except httpx.TimeoutException as exc:
            raise ai_timeout() from exc
        except httpx.HTTPError as exc:
            log.warning("Không gọi được Ollama: %s", type(exc).__name__)
            raise ai_unavailable() from exc
        if response.status_code != 200:
            log.warning("Ollama trả HTTP %d", response.status_code)
            raise ai_unavailable()
        return (response.json().get("message") or {}).get("content", "").strip()

    def ready(self) -> bool:
        """Ollama phản hồi và đã có model chat (không tự tải model: cài ngoại tuyến, docs/14 mục 12)."""
        try:
            response = self._client.get("/api/tags", timeout=2.0)
            names = {m.get("name") for m in response.json().get("models", [])} if response.status_code == 200 else set()
        except (httpx.HTTPError, ValueError):
            return False
        return self._settings.chat_model in names

    def close(self) -> None:
        self._client.close()
