import csv
import json
from pathlib import Path

from app.ocr.engine import Token
from app.schemas import DocumentType


def load_index(samples: Path) -> list[dict]:
    with open(samples / "index.csv", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def tokens_to_json(key: str, tokens: list[Token]) -> dict:
    return {"key": key, "tokens": [
        {"text": t.text, "score": round(t.score, 4), "box": list(t.box), "poly": [list(p) for p in t.poly], "page": t.page}
        for t in tokens]}


def tokens_from_json(data: dict) -> list[Token]:
    return [Token(text=t["text"], score=t["score"], box=tuple(t["box"]), poly=tuple(tuple(p) for p in t["poly"]),
                  page=t["page"]) for t in data["tokens"]]


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def document_type(index_row: dict) -> DocumentType:
    return DocumentType(index_row["documentType"])
