# Dữ liệu test bộ trích xuất OCR

Toàn bộ là dữ liệu **giả lập** (không phải chứng từ thật), sinh từ bộ mẫu `~/.erp/ai-samples/synthetic/` ngày 2026-10-27:

| Thư mục / file | Nội dung |
|---|---|
| `tokens/<mã>.json` | Token OCR (chữ, độ tin cậy, khung, tứ giác, trang) do `python -m tools.dump_tokens` sinh với `PP-OCRv5_mobile_det` + `latin_PP-OCRv5_mobile_rec` |
| `ground-truth/<mã>.json` | Đáp án của chứng từ giả lập |
| `index.csv` | Danh sách chứng từ, loại chứng từ, loại file |

Giúp test bộ trích xuất chạy nhanh, xác định, không cần model. Đổi model OCR thì sinh lại `tokens/` và chạy lại test.
