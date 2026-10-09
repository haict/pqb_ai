# Quy tắc cơ sở dữ liệu

## 1. Nền tảng
- MySQL 8.x, InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`.
- Thiết kế **ERD trước khi tạo bảng**; cập nhật ERD và data dictionary ở `docs/03-database-design.md` cùng lúc với migration.

## 2. Migration (Flyway)
- Flyway là nguồn duy nhất của schema; `spring.jpa.hibernate.ddl-auto=validate` (không dùng `update`/`create`).
- **Không sửa migration đã chạy**; thay đổi bằng migration mới.
- Tên: `V<phiên bản>__<mo_ta>.sql`, ví dụ `V3__create_inventory_tables.sql`.
- Vị trí:
  - `db/migration` — schema và dữ liệu tham chiếu bắt buộc (danh mục trạng thái, quyền hệ thống). Chạy ở mọi môi trường.
  - `db/dev-data` — **dữ liệu mẫu**, chỉ thêm vào `spring.flyway.locations` ở profile `dev`, `test`. **Không bao giờ** cấu hình ở `prod`.
  - File dữ liệu mẫu đặt tên `R__dev_<mo_ta>.sql` (repeatable, chạy sau mọi migration có phiên bản, viết để chạy lại được). Không dùng tiền tố `V` trong `db/dev-data` để không chiếm số phiên bản của schema.
- Kiểm tra migration trên database trống trước khi báo hoàn thành.

## 3. Đặt tên
- Bảng snake_case số nhiều, tiền tố theo nhóm khi cần: `sys_users`, `products`, `purchase_orders`, `purchase_order_lines`.
- Bảng dòng chứng từ: `<chứng_từ>_lines`.
- Khóa ngoại `<bảng_số_ít>_id`; index `idx_<bảng>_<cột>`; unique `uk_<bảng>_<cột>`; FK `fk_<bảng>_<bảng_tham_chiếu>`.
- Danh sách nhóm bảng trong đặc tả là **gợi ý**: chuẩn hóa theo ERD, không tạo bảng không có mục đích nghiệp vụ, không trùng dữ liệu.

## 4. Kiểu dữ liệu
| Dữ liệu | Kiểu |
|---|---|
| ID | `BIGINT` tự tăng |
| Tiền, đơn giá | `DECIMAL(19,4)`; tiền hiển thị làm tròn đến đồng (D-05) |
| Số lượng | `DECIMAL(19,3)` (D-05) |
| Hệ số quy đổi, tỷ giá | `DECIMAL(19,6)` trở lên |
| Ngày chứng từ, ngày hạch toán, hạn dùng | `DATE` |
| Thời điểm tạo/cập nhật/ghi sổ | `DATETIME(3)` lưu UTC |
| Trạng thái, loại | `VARCHAR` ngắn ánh xạ enum (`@Enumerated(STRING)`) |

- **Không dùng `FLOAT`/`DOUBLE`** cho tiền, số lượng, tỷ giá.
- `NOT NULL` khi nghiệp vụ bắt buộc; tránh `VARCHAR` quá dài không lý do.
- Mật khẩu chỉ lưu dạng băm; không lưu secret dạng rõ.

## 5. Ràng buộc
- PK cho mọi bảng; FK cho mọi tham chiếu trong cùng module. Tham chiếu xuyên module vẫn có FK trong DB (cùng một database), nhưng code không dùng quan hệ JPA xuyên module.
- Unique cho khóa nghiệp vụ:
  - Mã hàng theo công ty.
  - Số chứng từ theo (công ty, loại, chu kỳ).
  - (Hàng, số lô).
  - (Hàng, serial).
  - Hóa đơn mua theo (MST người bán, ký hiệu, số).
  - `Idempotency-Key` theo người dùng.
- Không chỉ dựa vào validation ứng dụng cho tính duy nhất và toàn vẹn.

## 6. Cột chuẩn
- `created_by, created_at, updated_by, updated_at` cho danh mục và chứng từ.
- `version` (optimistic locking) cho bảng có thể bị sửa đồng thời.
- `company_id`, `branch_id` cho chứng từ và dữ liệu cần phạm vi.
- Chứng từ: `document_no, document_date, posting_date, status`, người/thời điểm duyệt, ghi sổ, đảo, lý do.
- Bảng sổ phụ (`inventory_transactions`, sổ công nợ, sổ quỹ, `journal_entry_lines`): **chỉ INSERT**, không UPDATE/DELETE; có `reference_type, reference_id, reference_line_id, reference_no`.

## 7. Thời gian
- Thời điểm lưu UTC (`hibernate.jdbc.time_zone=UTC`); hiển thị UTC+7.
- Ngày chứng từ, ngày hạch toán, hạn dùng là `DATE` theo lịch Việt Nam, không chuyển múi giờ.

## 8. Index
Index phải phục vụ truy vấn thực tế; không index mọi cột. Ưu tiên:
- Mã danh mục, số chứng từ, trạng thái + ngày.
- `inventory_balances(product_id, warehouse_id, location_id, lot_id, serial_id, quality_status)` (unique).
- `inventory_transactions(product_id, warehouse_id, posting_date)`, `(reference_type, reference_id)`, `(lot_id)`, `(serial_id)`.
- Công nợ theo `(partner_id, due_date)`, hóa đơn theo `(partner_id, status)`.
- Dòng hợp đồng theo `(contract_id, product_id)`.

Kiểm tra `EXPLAIN` cho truy vấn báo cáo lớn.

## 9. Sổ phụ và số dư
- `inventory_transactions` là sổ nhật ký bất biến; `inventory_balances` là bản đọc nhanh, phải tái tạo được từ sổ nhật ký.
- Tương tự cho công nợ, quỹ, kế toán: số dư phải tái tạo được từ sổ chi tiết.
- Không dùng bảng số dư làm nguồn lịch sử duy nhất.

## 10. Xóa dữ liệu
- Không xóa cứng danh mục đã được tham chiếu; dùng trạng thái ngừng hoạt động.
- Không xóa cứng chứng từ đã gửi duyệt hoặc đã ghi sổ.
- Không xóa nhật ký audit qua ứng dụng.

## 11. Tài khoản database
Tài khoản ứng dụng chỉ có quyền DML trên schema ứng dụng; tài khoản migration tách riêng khi triển khai production (nếu khả thi).
