# Quy tắc kiến trúc

## 1. Modular Monolith
- Một ứng dụng backend, một database, chia **module theo nghiệp vụ** (M01..M10, xem `CLAUDE.md` mục 5).
- Không tách microservices, message broker, hay database riêng cho module khi chưa có quyết định được duyệt.
- Module là ranh giới logic: sở hữu bảng, entity, quy tắc nghiệp vụ của mình.

## 2. Tầng trong module
```
api  ->  application  ->  domain
              |
              v
       infrastructure (repository, client)
```

| Tầng | Được làm | Không được làm |
|---|---|---|
| `api` (Controller) | Nhận HTTP, validate cú pháp (Bean Validation), gọi application, map kết quả sang response DTO | Chứa nghiệp vụ, tính tồn/công nợ, mở transaction, gọi repository, trả entity |
| `application` (Service / use case) | Điều phối use case, `@Transactional`, kiểm tra quyền nghiệp vụ, gọi domain và repository, gọi API công khai của module khác | Xử lý HTTP, trả entity ra ngoài module |
| `domain` (Entity, enum, quy tắc) | Bất biến nghiệp vụ, chuyển trạng thái hợp lệ, tính toán thuần | Phụ thuộc Spring MVC, gọi repository |
| `infrastructure` (Repository, query, client) | Truy cập dữ liệu, truy vấn, projection, tích hợp ngoài | Chứa luồng nghiệp vụ lớn |

### DTO
- Request/response DTO (`record`) đặt ở `<module>/application/dto`: controller nhận/trả trực tiếp, service nhận/trả DTO (cần để lưu kết quả `Idempotency-Key` trong transaction).
- Bean Validation khai báo trên request DTO; controller dùng `@Valid`.
- Entity → DTO chuyển trong `application` (mapper nội bộ module); entity không ra khỏi module.

## 3. Ranh giới module
- Module khác chỉ dùng **API công khai** của module (interface/service trong `application`, DTO nội bộ). Không import entity hay repository của module khác.
- Tham chiếu chéo module bằng ID (ví dụ `productId`), không bằng quan hệ JPA xuyên module.
- Không có phụ thuộc vòng giữa module.
- Bắt buộc đi qua module sở hữu:
  - Biến động tồn kho → `inventory` (ví dụ `InventoryPostingService`).
  - Biến động công nợ, quỹ → `finance`.
  - Bút toán kế toán, giá vốn → `accounting`.
  - Đánh số chứng từ, idempotency, audit → `common` / `system`.
- Kiểm tra ranh giới tự động bằng test kiến trúc ArchUnit (scope test, đã chốt D-42): cấm import entity/repository của module khác, cấm phụ thuộc vòng, cấm Controller gọi Repository.

## 4. Kích thước lớp
- Không có Service khổng lồ. Tách theo use case: `GoodsReceiptPostingService`, `SalesOrderApprovalService`, `TenderContractDeliveryService`...
- Không nhồi nghiệp vụ vào Controller.
- Một lớp vượt khoảng 400 dòng hoặc nhiều trách nhiệm → xem xét tách.

## 5. Use case ghi sổ
Mọi use case ghi sổ tuân theo trình tự ở `CLAUDE.md` mục 9 và `.claude/rules/business-integrity.md`. Transaction đặt ở tầng `application`, bao trọn: chứng từ, sổ phụ, bút toán, số dư, chỉ tiêu nguồn, audit.

## 6. Thành phần xuyên suốt (`common`)
- Định dạng phản hồi và lỗi API, `GlobalExceptionHandler`.
- Phân trang, sắp xếp, lọc.
- Idempotency (lưu `Idempotency-Key` + kết quả).
- Đánh số chứng từ (an toàn đồng thời).
- Audit (người, thời điểm, lý do, trước/sau).
- Ngữ cảnh người dùng và phạm vi dữ liệu (công ty, chi nhánh, kho).

Không đưa nghiệp vụ của module cụ thể vào `common`.

## 7. Tài liệu kiến trúc
Thay đổi kiến trúc, ranh giới module, chiến lược khóa phải cập nhật `docs/02-system-architecture.md` (kèm sơ đồ module và luồng) **trước khi** triển khai, và được duyệt.
