# Quy tắc kiểm thử

## 1. Công cụ
- Backend: JUnit 5, Mockito, AssertJ, Spring Boot Test (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`), Spring Security Test.
- Integration test chạy trên **MySQL 8 thật** (schema hậu tố `_test` trên máy local, cấu hình ở `~/.erp/application-local.yml`; CI dùng `ERP_TEST_DB_URL`) để kiểm tra Flyway, khóa dữ liệu, ràng buộc. Không dùng H2 thay MySQL cho test nghiệp vụ ghi sổ.
- Test tích hợp bị bỏ qua (không có DB test) **không được tính là đạt**; báo cáo rõ số test bị bỏ qua.
- Frontend: Vitest/Jest + React Testing Library cho component và luồng chính.

## 2. Phạm vi bắt buộc cho mỗi use case quan trọng
- Luồng thành công.
- Validation sai.
- Không tìm thấy.
- Trùng (khóa nghiệp vụ, gửi lại yêu cầu).
- Chưa đăng nhập / không đủ quyền / ngoài phạm vi dữ liệu.
- Sai trạng thái chứng từ.
- Kỳ đã khóa.
- Rollback khi lỗi giữa chừng (không còn dữ liệu dở dang).

## 3. Kịch bản nghiệm thu bắt buộc (BR mục 18)
| Mã | Kịch bản |
|---|---|
| AT-01 | Đơn mua, nhận hàng nhiều đợt, kiểm tra tồn và hàng đang về |
| AT-02 | Nhận hàng theo lô, hạn dùng, serial |
| AT-03 | Đơn bán, xuất kho theo lô FEFO |
| AT-04 | Hai người đồng thời xuất hàng không làm tồn sai/âm |
| AT-05 | Giao hàng nhiều đợt |
| AT-06 | Trả hàng và điều chỉnh tồn có truy vết |
| AT-07 | Hợp đồng trúng thầu, chặn giao vượt số còn lại |
| AT-08 | Thu tiền một phần, đối chiếu công nợ |
| AT-09 | Hủy/đảo chứng từ đã ghi sổ xử lý đúng ảnh hưởng liên quan |
| AT-10 | Không có quyền thì không duyệt, không truy cập dữ liệu trái phép |
| AT-11 | Khóa kỳ, chặn chứng từ thuộc kỳ khóa |
| AT-12 | Dashboard, báo cáo khớp dữ liệu nguồn |
| AT-13 | Gửi lại yêu cầu không tạo dữ liệu trùng |

Tên test hoặc `@DisplayName` ghi mã AT/BR để truy vết.

## 4. Kiểm thử đồng thời
- Dùng nhiều luồng (`ExecutorService` + `CountDownLatch`) gọi use case ghi sổ trên **cùng dòng số dư** với MySQL thật.
- Khẳng định: tổng số lượng xuất thành công ≤ tồn ban đầu, tồn không âm, số dư khớp tổng sổ nhật ký.
- Tương tự cho phân bổ thu tiền một hóa đơn và giao trên một dòng hợp đồng.

## 5. Kiểm thử tính nhất quán
Sau mỗi kịch bản ghi sổ/đảo, khẳng định:
- `inventory_balances` = tổng `inventory_transactions`.
- Số dư công nợ = tổng sổ chi tiết.
- Bút toán cân Nợ = Có.
- Chỉ tiêu chứng từ nguồn (đã nhận, đã giao, còn lại) đúng.

## 6. Thiết kế test
- Unit test quy tắc nghiệp vụ không cần database khi có thể.
- Integration test kiểm tra JPA, Flyway, khóa, ràng buộc.
- Test xác định (deterministic): không phụ thuộc giờ hệ thống (dùng `Clock` tiêm vào), thứ tự chạy, dữ liệu ngẫu nhiên không cố định seed.
- Tránh test giòn gắn vào chi tiết cài đặt.
- Dữ liệu test tạo trong test hoặc từ `db/dev-data`, không phụ thuộc dữ liệu thủ công.

## 7. Lệnh chạy trước khi báo hoàn thành
```
cd backend  && mvn clean verify
cd frontend && npm run lint && npm test && npm run build
```
Thay đổi DB: chạy Flyway trên database trống (test tích hợp tự xóa sạch schema `_test` rồi migrate lại).

## 8. Báo cáo kết quả
**Không tuyên bố hoàn thành khi chỉ biên dịch thành công.** Báo cáo phải nêu:
- Lệnh đã chạy.
- Tổng số test, số đạt, số lỗi, số bỏ qua.
- Kịch bản AT đã phủ / chưa phủ.
- Lỗi còn tồn tại.
- Chức năng chưa triển khai.

Kết quả nghiệm thu tổng hợp ở `docs/11-test-plan.md`.
