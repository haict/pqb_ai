# AI Service (D-64)

Dịch vụ Python nội bộ của ERP: nhận dạng chứng từ (OCR) bằng PaddleOCR và trợ lý hỏi đáp cách dùng ERP (LLM trên Ollama + tìm đoạn tài liệu hướng dẫn). **Chỉ Spring Boot gọi**, lắng nghe `127.0.0.1`, xác thực bằng header `X-AI-Service-Key`. Không lưu file, không lưu nội dung nhận dạng, không ghi nội dung chứng từ vào log. Thiết kế: [docs/14-ai-assistant-ocr.md](../docs/14-ai-assistant-ocr.md).

Đợt AI-1a (2026-10-27): OCR. Đợt AI-3 (2026-10-28): `/internal/v1/chat` trả lời từ tài liệu hướng dẫn đã duyệt; chưa có công cụ tra cứu dữ liệu (AI-4).

## Yêu cầu
- Python 3.11 (đã đo với 3.11.9), CPU, không cần GPU.
- Model PaddleOCR cài sẵn trong một thư mục, mỗi model một thư mục con theo tên:
  - `PP-OCRv5_mobile_det` (phát hiện vùng chữ)
  - `latin_PP-OCRv5_mobile_rec` (nhận dạng chữ)

  Dịch vụ không tự tải model (`PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK`); thiếu model thì dừng khi khởi động.
- Trợ lý: Ollama chạy ở `127.0.0.1:11434` đã cài model `qwen2.5:3b` (dịch vụ không tự tải model chat); chỉ mục tài liệu hướng dẫn lập bằng `tools.build_rag_index`. Thiếu chỉ mục hoặc Ollama thì trợ lý báo không khả dụng, OCR vẫn chạy.

## Cài đặt (máy dev)
```powershell
cd ai-service
py -3.11 -m venv .venv
.venv\Scripts\python -m pip install -r requirements-dev.txt
```
Linux: `python3.11 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt`.

Model: lần đầu tải bằng PaddleOCR trên máy có Internet (mặc định về `~/.paddlex/official_models`), rồi sao chép thư mục model sang máy chủ. Ghi phiên bản và checksum khi triển khai (docs/14 mục 12).

## Cấu hình
Biến môi trường, xem [.env.example](.env.example) và `docs/08-environment.md` mục 6. Máy dev: chép `.env.example` ra `~/.erp/ai-service.env`, điền `AI_SERVICE_API_KEY` (ngẫu nhiên, ≥ 16 ký tự) và `OCR_MODEL_DIR`, rồi:
```powershell
$env:AI_SERVICE_ENV_FILE = "$HOME\.erp\ai-service.env"
.venv\Scripts\python -m app          # http://127.0.0.1:8090
```
`AI_SERVICE_ENABLE_DOCS=true` bật `/docs` (Swagger) chỉ ở dev; tắt ở `uat`, `prod`.

Chỉ mục tài liệu hướng dẫn cho trợ lý (chạy lại khi tài liệu thay đổi, rồi khởi động lại dịch vụ; đặt `RAG_INDEX_DIR` trỏ tới thư mục `--out`):
```powershell
.venv\Scripts\python -m tools.build_rag_index --source ..\docs\13-huong-dan-su-dung.docx --out $HOME\.erp\ai-rag --exclude 4,13
```

## API nội bộ
| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/internal/v1/health` | `status`; `ocr` (model đang dùng, `textDiacriticsReliable`); `chat` (`ready`, `indexReady`, `llmReady`, `model`) |
| POST | `/internal/v1/ocr/extract` | `multipart/form-data`: `file` (JPG / PNG / PDF), `documentType` (`PURCHASE_ORDER`, `PURCHASE_INVOICE`, `SALES_ORDER`). Trả `header`, `lines`, `totals` theo hợp đồng trường docs/14 mục 4.4 |
| POST | `/internal/v1/chat` | JSON `{question, history[]}`; trả `{answer, citations[], grounded, durationMs}` (docs/14 mục 6.3). Không có đoạn tài liệu phù hợp thì trả câu cố định, không gọi LLM |

Lỗi: `{ "success": false, "code", "message", "requestId" }`.

| HTTP | Mã | Khi nào |
|---|---|---|
| 400 | `OCR_FILE_INVALID` | File hỏng, PDF quá số trang, ảnh quá số điểm ảnh |
| 400 | `VALIDATION_ERROR` | Thiếu file hoặc `documentType` sai |
| 401 | `UNAUTHORIZED` | Thiếu / sai `X-AI-Service-Key` |
| 413 | `OCR_FILE_TOO_LARGE` | Vượt `OCR_MAX_FILE_SIZE` |
| 415 | `OCR_UNSUPPORTED_TYPE` | Chữ ký byte đầu file không phải JPG / PNG / PDF |
| 422 | `OCR_NO_TEXT` | Không nhận dạng được chữ |
| 429 | `AI_BUSY` | Đang có một yêu cầu OCR / chat khác (mỗi loại xử lý một yêu cầu một lúc) |
| 503 | `AI_SERVICE_UNAVAILABLE` | Chat: chưa có chỉ mục tài liệu hoặc Ollama không phản hồi |
| 504 | `AI_TIMEOUT` | Chat: Ollama trả lời quá `CHAT_TIMEOUT_SECONDS` |
| 500 | `INTERNAL_ERROR` | Lỗi khác, không kèm chi tiết |

## Kiểm thử
```powershell
.venv\Scripts\python -m pytest                     # gồm test model thật nếu có biến môi trường dưới
$env:OCR_MODEL_DIR = "$HOME\.paddlex\official_models"
$env:AI_SAMPLES_DIR = "$HOME\.erp\ai-samples\synthetic"
```
- Test bộ trích xuất dùng token OCR lưu sẵn ở `tests/fixtures` (dữ liệu giả lập), không cần model.
- `tests/test_paddle_integration.py` (đánh dấu `slow`) chạy model thật; thiếu `OCR_MODEL_DIR` / `AI_SAMPLES_DIR` thì bị bỏ qua và không tính là đạt.

Đo độ chính xác trên bộ chứng từ mẫu:
```powershell
.venv\Scripts\python -m tools.evaluate $HOME\.erp\ai-samples\synthetic            # chạy OCR thật
.venv\Scripts\python -m tools.dump_tokens $HOME\.erp\ai-samples\synthetic tests\fixtures\tokens   # sinh lại token khi đổi model
```

## Cấu trúc
```
app/
├── main.py, __main__.py   FastAPI, nạp model một lần khi khởi động, một worker
├── config.py              biến môi trường (pydantic-settings)
├── security.py            kiểm tra X-AI-Service-Key
├── errors.py              mã lỗi, xử lý lỗi tập trung
├── logging_setup.py       requestId, log chỉ metadata
├── routers/               health.py, ocr.py, chat.py
├── chat/
│   ├── guide.py           đọc .docx (thư viện chuẩn), cắt đoạn theo mục, lưu / nạp chỉ mục JSON
│   ├── retriever.py       BM25 trên văn bản bỏ dấu (từ đơn + cặp âm tiết)
│   ├── ollama.py          gọi Ollama /api/chat, kiểm tra model đã cài
│   └── service.py         lời dặn hệ thống, ghép đoạn tài liệu, một câu hỏi một lúc
└── ocr/
    ├── preprocess.py      kiểm tra loại file thật, giới hạn điểm ảnh / trang, PDF -> ảnh
    ├── engine.py          PaddleOCR, khóa một tác vụ một lúc
    ├── layout.py          gom chữ thành dòng, bù góc nghiêng ảnh chụp
    ├── table.py           tìm bảng hàng hóa theo tiêu đề cột, tách dòng hàng
    ├── normalize.py       số kiểu VN, ngày, MST, khóa so khớp bỏ dấu
    ├── fields.py          hợp đồng một trường
    └── extractors/        base.py (trích chung) + vùng đối tác của từng loại chứng từ
tools/                     dump_tokens.py, evaluate.py, scoring.py, build_rag_index.py
tests/
```

## Hạn chế đã biết
- Model nhận dạng thiếu 45/67 chữ thường tiếng Việt có dấu (ệ, ộ, ị, ạ...): tên, địa chỉ, ĐVT đọc thiếu dấu ("Hộp" → "Hp"). Các trường này luôn `LOW_CONFIDENCE` + cảnh báo `TEXT_DIACRITICS_UNRELIABLE`, kèm `matchKey` bỏ dấu để Spring so danh mục. Số, mã, MST không bị ảnh hưởng.
- Bộ quy tắc trích xuất mới thử trên chứng từ giả lập (docs/14 mục 9.4); cần chỉnh khi có mẫu chứng từ thật.
- Trợ lý trả lời đúng theo tài liệu hướng dẫn; tài liệu hiện còn mô tả quy trình trước D-61, D-62 nên câu trả lời có thể lỗi thời cho đến khi tài liệu được cập nhật và lập chỉ mục lại. Mỗi câu trả lời khoảng 15–45 giây trên CPU (docs/14 mục 9.5).
- Chưa đăng ký dịch vụ hệ thống (Windows service / systemd); làm khi lập `docs/10-deployment.md`.
