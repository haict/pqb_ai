"""Tìm đoạn tài liệu bằng BM25 trên văn bản bỏ dấu (Q-07, chủ dự án chọn 2026-10-28: không dùng model embedding).

Tiếng Việt viết theo âm tiết nên ngoài từ đơn còn dùng cặp âm tiết liền nhau ("don mua", "ghi so") để phân biệt cụm từ.
"""
import math
from collections import Counter
from dataclasses import dataclass

from app.chat.guide import Chunk
from app.ocr.normalize import match_key

# Hư từ thường gặp trong câu hỏi; bỏ để điểm tập trung vào thuật ngữ nghiệp vụ.
_STOPWORDS = frozenset(
    "la cua va cac nhung nhu the nao khi toi minh co khong duoc de cho trong mot nay thi o voi ve lam gi sao "
    "bao nhieu a oi nhe a ban hay can phai muon xin hoi giup em anh chi tai vi neu ma da dang se rang".split()
)
_K1 = 1.5
_B = 0.75


def terms(text: str) -> list[str]:
    words = [w for w in match_key(text).split() if w not in _STOPWORDS]
    return words + [f"{a}_{b}" for a, b in zip(words, words[1:])]


@dataclass(frozen=True)
class Hit:
    chunk: Chunk
    score: float


class Bm25Retriever:
    def __init__(self, chunks: list[Chunk]):
        self.chunks = chunks
        # Tiêu đề mục gộp vào nội dung: câu hỏi thường dùng đúng tên chức năng.
        self._docs = [Counter(terms(f"{c.title}\n{c.text}")) for c in chunks]
        self._lengths = [sum(doc.values()) for doc in self._docs]
        self._average = sum(self._lengths) / len(self._lengths) if chunks else 0.0
        frequency: Counter[str] = Counter()
        for doc in self._docs:
            frequency.update(doc.keys())
        total = len(chunks)
        self._idf = {term: math.log(1 + (total - n + 0.5) / (n + 0.5)) for term, n in frequency.items()}

    def search(self, query: str, top_k: int, min_score: float) -> list[Hit]:
        query_terms = set(terms(query))
        hits = []
        for chunk, doc, length in zip(self.chunks, self._docs, self._lengths):
            score = 0.0
            for term in query_terms & doc.keys():
                tf = doc[term]
                score += self._idf[term] * tf * (_K1 + 1) / (tf + _K1 * (1 - _B + _B * length / self._average))
            if score >= min_score:
                hits.append(Hit(chunk, score))
        hits.sort(key=lambda hit: hit.score, reverse=True)
        return hits[:top_k]
