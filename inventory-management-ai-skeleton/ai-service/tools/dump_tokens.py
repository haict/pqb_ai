"""Chạy OCR thật trên bộ chứng từ mẫu và lưu token (chữ, độ tin cậy, khung, tứ giác) ra JSON.

Dùng để tạo dữ liệu test cho bộ trích xuất mà không cần model:
    python -m tools.dump_tokens <thư mục bộ mẫu> <thư mục đích> [mã chứng từ ...]
Cần biến môi trường như khi chạy dịch vụ (OCR_MODEL_DIR, AI_SERVICE_API_KEY).
"""
import json
import sys
from pathlib import Path

from app.config import get_settings
from app.ocr.engine import OcrRunner, PaddleOcrEngine
from app.ocr.preprocess import load_pages
from tools.common import load_index, tokens_to_json


def main() -> None:
    samples, out = Path(sys.argv[1]), Path(sys.argv[2])
    only = set(sys.argv[3:])
    out.mkdir(parents=True, exist_ok=True)
    settings = get_settings()
    runner = OcrRunner(PaddleOcrEngine(settings))
    for row in load_index(samples):
        if only and row["key"] not in only:
            continue
        tokens = runner.run(load_pages((samples / row["file"]).read_bytes(), settings))
        (out / f"{row['key']}.json").write_text(json.dumps(tokens_to_json(row["key"], tokens), ensure_ascii=False),
                                                encoding="utf-8")
        print(row["key"], len(tokens), flush=True)


if __name__ == "__main__":
    main()
