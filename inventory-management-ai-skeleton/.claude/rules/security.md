# Quy tắc bảo mật

## 1. Xác thực
- Spring Security. Cơ chế xác thực phải được ghi rõ trong `docs/07-authorization.md` trước khi làm (D-38).
- Đã chốt (D-38): JWT access token ngắn hạn + refresh token xoay vòng lưu phía server (thu hồi được khi đăng xuất, khóa tài khoản, đổi mật khẩu).
- Mật khẩu băm bằng thuật toán mạnh (BCrypt/Argon2 qua `PasswordEncoder`); không lưu, không log mật khẩu dạng rõ.
- Khóa tạm tài khoản khi sai mật khẩu nhiều lần; chính sách độ mạnh mật khẩu; phiên hết hạn khi không thao tác.
- Ghi nhật ký đăng nhập thành công, thất bại, đăng xuất.

## 2. Phân quyền
- RBAC: Người dùng → Vai trò → Quyền (`<chức năng>:<hành động>`, ví dụ `sales-order:approve`).
- **Mọi API phải được bảo vệ**: mặc định từ chối, mở rõ ràng từng endpoint. Dùng `@PreAuthorize` hoặc kiểm tra trong application service.
- **Phạm vi dữ liệu**: mọi truy vấn và lệnh lọc theo công ty, chi nhánh, kho được phép của người dùng; nhân viên kinh doanh chỉ thấy dữ liệu của mình/nhóm khi cấu hình.
- Truy cập theo ID phải kiểm tra phạm vi (chống IDOR): trả `404` hoặc `403` thống nhất khi dữ liệu ngoài phạm vi.
- Tách biệt nhiệm vụ: người lập không tự duyệt khi cấu hình bật.
- Bắt buộc bảo vệ: thay đổi danh mục, thao tác thay đổi tồn, duyệt, ghi sổ, hủy, đảo, điều chỉnh, khóa/mở kỳ, phân quyền, cấu hình.

## 3. Không tin frontend
- Không nhận `userId`, `role`, `branchId` phạm vi, giá vốn, trạng thái từ client như căn cứ phân quyền.
- Danh tính và phạm vi lấy từ security context phía server.
- Giá, chiết khấu, tổng tiền được tính lại phía server.

## 4. Dữ liệu đầu vào và file
- Validate mọi đầu vào; dùng tham số hóa truy vấn (JPA/`@Query` có tham số); không nối chuỗi SQL.
- Cột sắp xếp/lọc động phải theo danh sách cho phép.
- File tải lên:
  - Giới hạn dung lượng (`spring.servlet.multipart.max-file-size`) và danh sách loại cho phép (D-41).
  - Kiểm tra loại thật của file, không chỉ phần mở rộng.
  - Đặt lại tên file khi lưu; lưu ngoài thư mục web.
  - Tải xuống phải kiểm tra quyền.

## 5. Secret và cấu hình
- Không commit mật khẩu, API key, thông tin DB, private key, token, file `.env` thật.
- Secret qua biến môi trường hoặc secret store theo môi trường; có file mẫu `.env.example` không chứa giá trị thật.
- Swagger UI tắt hoặc yêu cầu đăng nhập ở `prod`.

## 6. Logging
Không bao giờ log: mật khẩu, header `Authorization`, access/refresh token, private key, toàn bộ payload nhạy cảm. Che (mask) dữ liệu khi cần log định danh.

## 7. API và web
- CORS chỉ cho domain frontend được cấu hình.
- Header bảo mật cơ bản (HSTS khi HTTPS, `X-Content-Type-Options`, chống clickjacking).
- Lỗi bảo mật trả thông báo chung, không tiết lộ tài khoản tồn tại hay không.
- Giới hạn tần suất cho đăng nhập.

## 8. Audit
- Ghi audit cho thao tác quan trọng: người, thời điểm, hành động, thực thể, ID, số chứng từ, lý do, dữ liệu trước/sau, request ID, IP.
- Audit không sửa/xóa được qua ứng dụng.

## 9. Database
Tài khoản DB tối thiểu quyền; không dùng tài khoản quản trị cho ứng dụng.

## 10. Sao lưu
Có chính sách backup/restore đã kiểm thử, ghi ở `docs/09-backup-restore.md`; bản sao lưu được bảo vệ như dữ liệu production.

## 11. Kiểm thử bảo mật
Bắt buộc có test:
- Gọi API không đăng nhập (401).
- Thiếu quyền (403).
- Truy cập dữ liệu chi nhánh/kho khác theo ID.
- Tự duyệt chứng từ của mình khi bị cấm.
- Tải file sai loại hoặc quá dung lượng.
