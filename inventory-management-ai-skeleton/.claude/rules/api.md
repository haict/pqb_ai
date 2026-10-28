# Quy tắc API

## 1. Chung
- REST, JSON, tiền tố `/api/v1`.
- Tài liệu OpenAPI tự sinh (springdoc-openapi) tại `/v3/api-docs`, Swagger UI tại `/swagger-ui.html` (tắt hoặc bảo vệ ở `prod`). Mỗi endpoint có mô tả, mã lỗi, quyền yêu cầu.
- `docs/04-api-specification.md` mô tả quy ước và danh sách endpoint theo module; phải cập nhật khi đổi API.
- Thay đổi gây vỡ tương thích phải nêu tác động và được duyệt; ưu tiên thêm trường thay vì đổi/xóa.

## 2. Đặt tên tài nguyên
- Danh từ số nhiều, kebab-case: `/purchase-orders`, `/goods-receipts`, `/sales-orders`, `/tender-packages`.
- Dòng chứng từ là một phần của chứng từ (gửi kèm khi tạo/sửa), không tách API riêng nếu không cần.

| Hành động | Phương thức |
|---|---|
| Tạo | `POST /{resource}` |
| Danh sách | `GET /{resource}?page=&size=&sort=&<bộ lọc>` |
| Chi tiết | `GET /{resource}/{id}` |
| Sửa (chỉ DRAFT/REJECTED) | `PUT /{resource}/{id}` |
| Ngừng hoạt động danh mục | `PATCH /{resource}/{id}/status` |
| Chuyển trạng thái | `POST /{resource}/{id}/submit` · `approve` · `reject` · `post` · `cancel` · `reverse` · `close` |
| Chứng từ liên quan | `GET /{resource}/{id}/related-documents` |
| Lịch sử xử lý | `GET /{resource}/{id}/history` |

Không có endpoint cập nhật trực tiếp số dư tồn kho, công nợ, quỹ.

## 3. DTO
- Request và response tách riêng: `CreateXxxRequest`, `UpdateXxxRequest`, `XxxSearchRequest`, `XxxResponse`, `XxxSummaryResponse`.
- Không dùng entity làm request/response.
- Không nhận trường do server quyết định (`status`, `createdBy`, `documentNo`, tổng tiền, giá vốn).
- Hành động `reject`, `cancel`, `reverse`, `close` bắt buộc trường `reason`.
- Sửa chứng từ gửi kèm `version` để phát hiện sửa đồng thời (trả `409`).

## 4. Phân trang, sắp xếp, lọc (server-side)
- Tham số: `page` (từ 0), `size` (mặc định 20, tối đa 200), `sort=field,asc|desc`.
- Trường được sắp xếp/lọc theo danh sách cho phép.
- Phản hồi danh sách:
```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  },
  "timestamp": "2026-10-09T03:00:00Z"
}
```

## 5. Chống xử lý trùng (idempotency)
- `POST` tạo mới và mọi endpoint hành động nhận header `Idempotency-Key` (UUID do client sinh cho mỗi thao tác người dùng).
- Cùng key + cùng người dùng + cùng nội dung → trả lại kết quả lần đầu, không xử lý lại.
- Cùng key + nội dung khác → `409 IDEMPOTENCY_KEY_REUSED`.
- Lưu key và kết quả trong bảng riêng có thời hạn; ghi trong cùng transaction với nghiệp vụ.
- Ngoài key, server vẫn kiểm tra trạng thái (ghi sổ chứng từ đã POSTED → `409 INVALID_DOCUMENT_STATE`).
- Ngoại lệ (D-64): `POST /ocr/recognitions` không bắt buộc key vì chỉ trả gợi ý, không tạo dữ liệu nghiệp vụ. Gửi tin nhắn trợ lý vẫn bắt buộc key.

## 6. Phản hồi và lỗi
Thành công:
```json
{ "success": true, "data": {}, "message": null, "timestamp": "..." }
```
Lỗi:
```json
{
  "success": false,
  "code": "INSUFFICIENT_STOCK",
  "message": "Tồn khả dụng của lô L2401 không đủ (còn 5, cần 8).",
  "errors": [ { "field": "lines[0].quantity", "message": "Phải lớn hơn 0" } ],
  "requestId": "…",
  "timestamp": "..."
}
```

| HTTP | Khi nào |
|---|---|
| 400 | Dữ liệu sai cú pháp/validation (`VALIDATION_ERROR`) |
| 401 | Chưa xác thực |
| 403 | Không đủ quyền |
| 404 | Không tồn tại hoặc ngoài phạm vi dữ liệu |
| 409 | Xung đột: trùng khóa nghiệp vụ, sai trạng thái, sửa đồng thời, idempotency |
| 422 | Vi phạm quy tắc nghiệp vụ (`INSUFFICIENT_STOCK`, `PERIOD_LOCKED`, `CONTRACT_QUANTITY_EXCEEDED`, `CREDIT_LIMIT_EXCEEDED`) |
| 413 | File vượt dung lượng cho phép (`OCR_FILE_TOO_LARGE`) |
| 415 | Loại file thật không được hỗ trợ (`OCR_UNSUPPORTED_TYPE`) |
| 423 | Hết thời gian chờ khóa dữ liệu (`LOCK_TIMEOUT`), người dùng thử lại |
| 429 | Vượt giới hạn đồng thời / tần suất (`AI_BUSY`), người dùng thử lại |
| 500 | Lỗi hệ thống (thông báo chung, không lộ chi tiết) |
| 503 | Dịch vụ phụ thuộc không khả dụng (`AI_SERVICE_UNAVAILABLE`) |
| 504 | Dịch vụ phụ thuộc quá thời gian (`AI_TIMEOUT`) |

- Mã lỗi ổn định, liệt kê trong `docs/04-api-specification.md`.
- Thông báo tiếng Việt, dễ hiểu; không chứa stack trace, SQL, tên bảng.

## 7. Dữ liệu
- Tiền, số lượng truyền dạng **chuỗi số thập phân** hoặc number nhưng server đọc vào `BigDecimal`; không làm tròn ngầm.
- Ngày: `YYYY-MM-DD`; thời điểm: ISO-8601 UTC.
- Enum truyền dạng chuỗi (`"POSTED"`).

## 8. Bảo mật API
Mọi endpoint khai báo quyền; kiểm tra phạm vi dữ liệu; xem `.claude/rules/security.md`.
