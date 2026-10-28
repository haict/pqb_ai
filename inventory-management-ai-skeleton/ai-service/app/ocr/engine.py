import os
import threading
from dataclasses import dataclass
from typing import Protocol

import numpy as np

from app.config import Settings
from app.errors import busy


@dataclass(frozen=True)
class Token:
    """Một đoạn chữ OCR: khung trục (x1, y1, x2, y2) và tứ giác (4 điểm, theo chiều kim đồng hồ từ góc trên trái)."""

    text: str
    score: float
    box: tuple[int, int, int, int]
    poly: tuple[tuple[int, int], ...]
    page: int


class OcrEngine(Protocol):
    def recognize(self, image: np.ndarray, page: int) -> list[Token]: ...


class PaddleOcrEngine:
    """Nạp model một lần từ thư mục cài sẵn; không tải model từ Internet."""

    def __init__(self, settings: Settings):
        os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
        det_dir = settings.ocr_model_dir / settings.ocr_det_model
        rec_dir = settings.ocr_model_dir / settings.ocr_rec_model
        for d in (det_dir, rec_dir):
            if not (d / "inference.yml").is_file():
                raise RuntimeError(f"Thiếu model OCR tại {d}")
        from paddleocr import PaddleOCR

        self._ocr = PaddleOCR(
            text_detection_model_name=settings.ocr_det_model,
            text_detection_model_dir=str(det_dir),
            text_recognition_model_name=settings.ocr_rec_model,
            text_recognition_model_dir=str(rec_dir),
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            cpu_threads=settings.ocr_cpu_threads,
        )

    def recognize(self, image: np.ndarray, page: int) -> list[Token]:
        result = self._ocr.predict(image)[0]
        tokens = []
        for text, score, box, poly in zip(result["rec_texts"], result["rec_scores"], result["rec_boxes"], result["rec_polys"]):
            if text.strip():
                tokens.append(Token(text=text, score=float(score), box=tuple(int(v) for v in box),
                                    poly=tuple((int(x), int(y)) for x, y in poly), page=page))
        return tokens


class OcrRunner:
    """Chỉ một tác vụ OCR chạy một lúc; yêu cầu thứ hai bị từ chối ngay (Spring tự giới hạn đồng thời và thử lại)."""

    def __init__(self, engine: OcrEngine):
        self._engine = engine
        self._lock = threading.Lock()

    def run(self, pages: list[np.ndarray]) -> list[Token]:
        if not self._lock.acquire(blocking=False):
            raise busy()
        try:
            tokens: list[Token] = []
            for number, image in enumerate(pages, 1):
                tokens += self._engine.recognize(image, number)
            return tokens
        finally:
            self._lock.release()
