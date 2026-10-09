# Trạng thái tính năng

| Thuộc tính | Giá trị |
|---|---|
| Ngày cập nhật | 2026-10-09 |
| Quy tắc | `.claude/rules/documentation.md` mục 5 |

Trạng thái: **Hoàn thành** (test đạt) / **Đã code, chưa kiểm chứng** (test chưa chạy được) / **Đang làm** / **Chưa làm** / **Dời giai đoạn**.

## Giai đoạn 0 - Nền tảng
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Backend Maven WAR, Java 17, Spring Boot 3.5.16 | D-39 | Hoàn thành | `mvn clean verify`: 34 test, 34 đạt, 0 bỏ qua; tạo `target/erp.war` | Chưa triển khai thử lên Tomcat 10.1 ngoài |
| Profile `dev/test/uat/prod`, Logback có `requestId` | BR-NF | Hoàn thành | Cấu hình; `RequestIdFilterTest` | |
| Phản hồi API, lỗi tập trung, mã lỗi | docs/04 mục 1 | Hoàn thành | `GlobalExceptionHandlerTest` (6) | |
| Bảo mật nền: mặc định từ chối, 401/403 JSON, CORS | BR-SEC | Hoàn thành | `SecurityIntegrationTest` (3) | Đăng nhập JWT ở Giai đoạn 1 |
| Đánh số chứng từ an toàn đồng thời | BR-SYS-10, D-03 | Hoàn thành | `DocumentNumberFormatTest` (3), `DocumentNumberServiceIntegrationTest` (4, gồm 8 luồng × 10 số đồng thời: không trùng, không nhảy số) | |
| Chống xử lý trùng `Idempotency-Key` | BR-GEN-50, AT-13 | Hoàn thành (dịch vụ) | `IdempotentCommandTest` (2), `IdempotencyServiceIntegrationTest` (4) | Chưa gắn vào endpoint nào (chưa có endpoint nghiệp vụ) |
| Nhật ký audit | BR-GEN-07, BR-SYS-20 | Hoàn thành | `AuditLogServiceIntegrationTest` (3) | |
| Flyway V1 (`document_sequences`, `sys_idempotency_keys`, `sys_audit_logs`) | docs/03 mục 4.13–4.15 | Hoàn thành | Chạy trên DB trống `inventory_db_test` mỗi lần test; đã migrate `inventory_db` (dev) | FK tới `companies`/`branches` thêm ở Giai đoạn 1 |
| springdoc-openapi | D-42 | Hoàn thành | `/erp/v3/api-docs`, `/erp/swagger-ui/index.html` trả 200 (chạy thử profile dev) | |
| Kiểm tra ranh giới module ArchUnit | D-42 | Hoàn thành | `ArchitectureTest` (7) | |
| Frontend Vite + React + TS + Ant Design + TanStack Query | BR-UI | Hoàn thành | `npm run lint`, `npm test` (6), `npm run build` | Chỉ có trang chủ và trạng thái máy chủ |
| Môi trường dev: MySQL local, context `/erp`, cấu hình `~/.erp/application-local.yml` | D-44 | Hoàn thành | Chạy thử `erp.war` profile dev: health UP, frontend proxy tới `/erp` thành công | Chưa chạy thử trực tiếp trong Eclipse + Tomcat 10.1 ngoài |
| CI GitHub Actions (MySQL service, `ERP_TEST_DB_URL`) | | Đã code, chưa kiểm chứng | | Chưa có remote/CI chạy thật |

## Giai đoạn 1A - Xác thực, tổ chức, phân quyền (backend)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Đăng nhập JWT, refresh token xoay vòng (cookie httpOnly), đăng xuất, `/auth/me` | D-38, BR-SYS-02 | Hoàn thành (backend) | `AuthIntegrationTest` (8) | Giao diện ở 1B |
| Khóa tạm sau 5 lần sai, chính sách mật khẩu, bắt buộc đổi mật khẩu | BR-SYS-23 | Hoàn thành | `AuthIntegrationTest`, `UserTest`, `PasswordPolicyTest` | Chưa giới hạn tần suất theo IP |
| Nhật ký đăng nhập | BR-SYS-20 | Hoàn thành | `AuthIntegrationTest` | |
| Công ty, chi nhánh, phòng ban (CRUD, ngừng hoạt động, audit, chống trùng, sửa đồng thời) | BR-SYS-01 | Hoàn thành (backend) | `AuthorizationIntegrationTest` | Kho thuộc M02 (Giai đoạn 2) |
| Người dùng, vai trò, quyền; chống leo thang quyền | BR-SYS-02, 03 | Hoàn thành (backend) | `AuthorizationIntegrationTest` | Ma trận quyền nghiệp vụ bổ sung theo module |
| Phạm vi dữ liệu công ty / chi nhánh, chặn truy cập theo ID (404) | BR-SYS-04, 06, AT-10 | Hoàn thành (backend) | `AuthorizationIntegrationTest` | Phạm vi kho: Giai đoạn 2; `own_data_only` áp dụng ở module nghiệp vụ |
| Quản trị viên đầu tiên cho uat/prod | BR-SYS-23 | Hoàn thành | `AdminBootstrapTest` (3, unit) | Chưa chạy thử trên DB trống không có dữ liệu mẫu |

## Giai đoạn 1B - Giao diện xác thực và quản trị
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Đăng nhập, khôi phục phiên khi tải lại trang, đăng xuất, đổi mật khẩu (bắt buộc lần đầu) | D-38, BR-SYS-23 | Hoàn thành | `LoginPage.test.tsx` (3), `RouteGuards.test.tsx` (4); kiểm tra đầu-cuối qua proxy Vite với backend thật | Access token chỉ trong bộ nhớ |
| Tự làm mới access token, gộp nhiều lần làm mới thành một, hết phiên thì về trang đăng nhập | D-38 | Hoàn thành | `httpClient.test.ts` (3) | |
| Menu và trang theo quyền; trang 403 khi thiếu quyền | BR-SYS-03, BR-UI | Hoàn thành | `navigation.test.ts` (2), `RouteGuards.test.tsx` | Server vẫn kiểm tra quyền |
| Chi nhánh làm việc trên header (gửi `X-Branch-Id`) | BR-GEN-60 | Hoàn thành (giao diện) | `httpClient.test.ts` | Backend dùng header này từ module nghiệp vụ đầu tiên |
| Màn hình công ty, chi nhánh, phòng ban, người dùng (vai trò, phạm vi, đặt lại mật khẩu, khóa/mở), vai trò (ma trận quyền), nhật ký đăng nhập | BR-SYS-01..04, 20 | Hoàn thành, chưa có test giao diện cho từng màn | Lint, kiểu TypeScript, build; API của các màn đã có test tích hợp backend | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giả định đã dùng
- Không có profile mặc định; thiếu `SPRING_PROFILES_ACTIVE` thì chạy với cấu hình gốc an toàn cho production.
- Dữ liệu mẫu dev dùng migration repeatable `R__dev_*.sql`; mật khẩu mẫu `Erp@2026` chỉ cho dev/test.
- DTO request/response đặt ở `application/dto` (cập nhật `.claude/rules/architecture.md`).
- Mã lỗi mới ngoài docs/04 bản 0.2: `MALFORMED_REQUEST`, `METHOD_NOT_ALLOWED`, `REQUEST_IN_PROGRESS`, `DATA_INTEGRITY_VIOLATION` (đã cập nhật docs/04).
