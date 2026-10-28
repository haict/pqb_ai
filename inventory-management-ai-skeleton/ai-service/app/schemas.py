from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field


class DocumentType(str, Enum):
    PURCHASE_ORDER = "PURCHASE_ORDER"
    PURCHASE_INVOICE = "PURCHASE_INVOICE"
    SALES_ORDER = "SALES_ORDER"


class FieldSource(str, Enum):
    OCR = "OCR"
    CALCULATED = "CALCULATED"
    UNKNOWN = "UNKNOWN"


class FieldStatus(str, Enum):
    OK = "OK"
    LOW_CONFIDENCE = "LOW_CONFIDENCE"
    UNKNOWN = "UNKNOWN"


class OcrField(BaseModel):
    """Hợp đồng một trường (docs/14 mục 4.4). CATALOG, AMBIGUOUS, NOT_FOUND, MISMATCH do Spring Boot gán."""

    value: str | None = None
    rawText: str | None = None
    matchKey: str | None = None
    source: FieldSource = FieldSource.UNKNOWN
    confidence: float | None = None
    status: FieldStatus = FieldStatus.UNKNOWN
    page: int | None = None
    box: list[int] | None = None
    warnings: list[str] = Field(default_factory=list)


class ExtractResponse(BaseModel):
    documentType: DocumentType
    pageCount: int
    header: dict[str, OcrField]
    lines: list[dict[str, OcrField]]
    totals: dict[str, OcrField]
    warnings: list[str]
    durationMs: int


class OcrHealth(BaseModel):
    ready: bool
    detectionModel: str
    recognitionModel: str
    textDiacriticsReliable: bool


class ChatHealth(BaseModel):
    """ready: có chỉ mục tài liệu và Ollama đã có model chat."""

    ready: bool
    indexReady: bool
    llmReady: bool
    model: str


class HealthResponse(BaseModel):
    status: str
    ocr: OcrHealth
    chat: ChatHealth


class ChatTurnIn(BaseModel):
    role: Literal["USER", "ASSISTANT"]
    content: str = Field(min_length=1, max_length=8000)


class ChatRequest(BaseModel):
    """Lịch sử do Spring Boot cắt theo ai.chat.max-history-turns / max-context-chars (docs/14 mục 5.4)."""

    question: str = Field(min_length=1)
    history: list[ChatTurnIn] = Field(default_factory=list, max_length=40)


class ChatResponse(BaseModel):
    answer: str
    citations: list[str]
    grounded: bool
    durationMs: int
