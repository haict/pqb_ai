"""Lập chỉ mục tài liệu hướng dẫn cho trợ lý (ngoại tuyến, docs/14 mục 5.5).

    python -m tools.build_rag_index --source ../docs/13-huong-dan-su-dung.docx --out <RAG_INDEX_DIR> --exclude 4,13

Mặc định bỏ chương 4 (Việc chờ duyệt, ủy quyền duyệt: đã bỏ theo D-61) và chương 13 (chức năng chưa có), theo quyết định
2026-10-28. Cập nhật tài liệu thì chạy lại lệnh rồi khởi động lại AI Service.
"""
import argparse
import sys
from pathlib import Path

from app.chat.guide import build_chunks, read_blocks, save_index

DEFAULT_EXCLUDED = "4,13"
DEFAULT_MAX_CHARS = 1500


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source", type=Path, required=True, help="file .docx tài liệu hướng dẫn đã duyệt")
    parser.add_argument("--out", type=Path, required=True, help="thư mục chỉ mục (RAG_INDEX_DIR)")
    parser.add_argument("--exclude", default=DEFAULT_EXCLUDED, help="số chương bỏ qua, cách nhau dấu phẩy")
    parser.add_argument("--max-chars", type=int, default=DEFAULT_MAX_CHARS, help="độ dài tối đa một đoạn")
    args = parser.parse_args(argv)
    excluded = {part.strip() for part in args.exclude.split(",") if part.strip()}
    chunks = build_chunks(read_blocks(args.source), excluded, args.max_chars)
    if not chunks:
        print("Không tìm thấy nội dung trong tài liệu.", file=sys.stderr)
        return 1
    target = save_index(args.out, args.source.name, chunks, excluded)
    sys.stdout.reconfigure(encoding="utf-8")
    print(f"Đã lập chỉ mục {len(chunks)} đoạn ({len({c.title for c in chunks})} mục), bỏ chương "
          f"{', '.join(sorted(excluded)) or '-'}: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
