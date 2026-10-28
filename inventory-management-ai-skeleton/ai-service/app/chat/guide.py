"""Đọc tài liệu hướng dẫn sử dụng (.docx) thành các đoạn để tìm kiếm (docs/14 mục 5.5).

Chỉ dùng thư viện chuẩn (zipfile + XML), không thêm python-docx. Cắt theo mục Heading 2 (mục không có Heading 2 thì theo
Heading 1); mục dài cắt tiếp theo đoạn văn để vừa ngữ cảnh LLM.
"""
import json
import re
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from xml.etree import ElementTree

_W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
_CHAPTER = re.compile(r"^(\d+)\.")
INDEX_FILE = "guide-index.json"


@dataclass(frozen=True)
class Chunk:
    id: int
    chapter: str
    title: str
    text: str


def read_blocks(path: Path) -> list[tuple[str, str]]:
    """(kiểu, văn bản) theo thứ tự trong tài liệu; kiểu là tên style Heading1 / Heading2 hoặc '' cho đoạn, 'table'."""
    with zipfile.ZipFile(path) as archive:
        root = ElementTree.fromstring(archive.read("word/document.xml"))
    body = root.find(f"{_W}body")
    blocks: list[tuple[str, str]] = []
    for element in body if body is not None else []:
        if element.tag == f"{_W}p":
            text = _text(element)
            if text:
                style = element.find(f"{_W}pPr/{_W}pStyle")
                blocks.append((style.get(f"{_W}val", "") if style is not None else "", text))
        elif element.tag == f"{_W}tbl":
            rows = [" | ".join(_text(cell) for cell in row.iter(f"{_W}tc")) for row in element.iter(f"{_W}tr")]
            text = "\n".join(row for row in rows if row.strip(" |"))
            if text:
                blocks.append(("table", text))
    return blocks


def _text(element: ElementTree.Element) -> str:
    return "".join(node.text or "" for node in element.iter(f"{_W}t")).strip()


def build_chunks(blocks: list[tuple[str, str]], excluded_chapters: set[str], max_chars: int) -> list[Chunk]:
    sections: list[tuple[str, str, list[str]]] = []
    chapter, chapter_title = "", ""
    for style, text in blocks:
        if style == "Heading1":
            match = _CHAPTER.match(text)
            chapter, chapter_title = (match.group(1) if match else ""), text
            sections.append((chapter, text, []))
        elif style == "Heading2":
            sections.append((chapter, f"{chapter_title} > {text}" if chapter_title else text, []))
        elif sections:
            sections[-1][2].append(text)
    chunks: list[Chunk] = []
    for section_chapter, title, paragraphs in sections:
        if section_chapter in excluded_chapters or not paragraphs:
            continue
        for part in _split(paragraphs, max_chars):
            chunks.append(Chunk(len(chunks) + 1, section_chapter, title, part))
    return chunks


def _split(paragraphs: list[str], max_chars: int) -> list[str]:
    parts, current = [], ""
    for paragraph in paragraphs:
        if current and len(current) + len(paragraph) + 1 > max_chars:
            parts.append(current)
            current = ""
        current = f"{current}\n{paragraph}" if current else paragraph
    if current:
        parts.append(current)
    return parts


def save_index(directory: Path, source: str, chunks: list[Chunk], excluded_chapters: set[str]) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / INDEX_FILE
    payload = {"source": source, "excludedChapters": sorted(excluded_chapters), "chunks": [asdict(c) for c in chunks]}
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
    return target


def load_index(directory: Path) -> tuple[str, list[Chunk]]:
    payload = json.loads((directory / INDEX_FILE).read_text(encoding="utf-8"))
    return payload["source"], [Chunk(**chunk) for chunk in payload["chunks"]]
