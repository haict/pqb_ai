"""Chấm bộ trích xuất so với đáp án của bộ chứng từ mẫu (docs/14 mục 9.4).

    python -m tools.evaluate <thư mục bộ mẫu> [--tokens <thư mục token JSON>]
Không có --tokens thì chạy OCR thật (cần OCR_MODEL_DIR). Kết quả: tỷ lệ đúng theo trường và loại file.
"""
import argparse
import sys
import time
from collections import defaultdict
from pathlib import Path

from app.config import get_settings
from app.ocr.engine import OcrRunner, PaddleOcrEngine
from app.ocr.extractors import SPECS
from app.ocr.extractors.base import extract
from app.ocr.fields import FieldPolicy
from app.ocr.preprocess import load_pages
from tools.common import document_type, load_index, read_json, tokens_from_json
from tools.scoring import score_document


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("samples", type=Path)
    parser.add_argument("--tokens", type=Path)
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    settings = get_settings()
    policy = FieldPolicy(settings.ocr_min_confidence, settings.ocr_text_diacritics_reliable)
    runner = None if args.tokens else OcrRunner(PaddleOcrEngine(settings))
    by_field, by_kind = defaultdict(lambda: [0, 0]), defaultdict(lambda: [0, 0])
    for row in load_index(args.samples):
        start = time.perf_counter()
        if args.tokens:
            tokens = tokens_from_json(read_json(args.tokens / f"{row['key']}.json"))
        else:
            tokens = runner.run(load_pages((args.samples / row["file"]).read_bytes(), settings))
        result = extract(tokens, SPECS[document_type(row)], policy)
        seconds = time.perf_counter() - start
        expected = read_json(args.samples / "ground-truth" / f"{row['key']}.json")
        checks = score_document(expected, result)
        ok = sum(1 for c in checks if c.ok)
        print(f"{row['key']:6} {row['kind']:9} {seconds:5.1f}s đúng {ok}/{len(checks)}")
        for c in checks:
            by_field[c.field][0] += c.ok
            by_field[c.field][1] += 1
            by_kind[row["kind"]][0] += c.ok
            by_kind[row["kind"]][1] += 1
            if not c.ok:
                print(f"    sai {c.field}: mong đợi {c.expected!r}, nhận {c.actual!r}")
    print("Theo trường:")
    for name, (ok, n) in sorted(by_field.items()):
        print(f"  {name:18} {ok:4}/{n:<4} {ok / n:6.1%}")
    print("Theo loại file:")
    for name, (ok, n) in sorted(by_kind.items()):
        print(f"  {name:10} {ok:4}/{n:<4} {ok / n:6.1%}")
    total_ok = sum(v[0] for v in by_field.values())
    total = sum(v[1] for v in by_field.values())
    print(f"Tổng {total_ok}/{total} {total_ok / total:.1%}")


if __name__ == "__main__":
    main()
