# Trạng thái tính năng

| Thuộc tính | Giá trị |
|---|---|
| Ngày cập nhật | 2026-10-10 |
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

## Giai đoạn 1C - Kỳ, tham số, số chứng từ
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Tạo kỳ tháng theo năm; xem trạng thái khóa theo chi nhánh | BR-SYS-30 | Hoàn thành | `PeriodLockIntegrationTest` | Năm tài chính = năm dương lịch `[Suy luận]` |
| Khóa/mở kỳ theo chi nhánh × phân hệ, theo thứ tự tháng; kế toán sau kho; mở lại cần quyền + lý do, ghi audit | BR-SYS-30..32, D-45..D-47 | Hoàn thành | `PeriodLockIntegrationTest` (6), `FiscalPeriodsPage.test.tsx` (2) | Điều kiện "đã tính giá vốn" trước khi khóa kỳ kho: điểm mở rộng `PeriodCloseCheck`, module kế toán (GĐ 4) cung cấp; hiện chưa kiểm tra |
| Chặn ghi sổ vào kỳ khóa / kỳ chưa tạo (`PeriodGuard`) | BR-GEN-10, AT-11 | Hoàn thành (phần khung) | `PeriodLockIntegrationTest` | AT-11 đầy đủ (ghi sổ, đảo, sửa chứng từ thật; giá vốn kỳ khóa không đổi) phủ khi có module ghi sổ |
| Tham số hệ thống theo công ty: kiểm tra kiểu/giới hạn, sửa đồng thời, audit, khóa sau khi phát sinh số liệu ghi sổ | BR-SYS-13, D-05 | Hoàn thành | `ParameterIntegrationTest` (4), `ParameterServiceTest` (3) | Mới có 3 tham số số lẻ (D-05), chưa module nào đọc. Phương pháp giá vốn, ngưỡng cảnh báo, ngưỡng duyệt, mẫu in thêm cùng module dùng chúng. Khóa sau ghi sổ qua điểm mở rộng `PostingActivityProbe` (chưa có module ghi sổ nên hiện luôn sửa được) |
| Cấu hình số chứng từ: tiền tố, độ dài, chu kỳ đặt lại; hiệu lực ngay / từ chu kỳ sau; hủy cấu hình đặt trước | BR-SYS-10, D-03, D-48 | Hoàn thành | `DocumentNumberFormatIntegrationTest` (4), `DocumentNumberFormatTest` (5), `documentNumber.test.ts` (2) | Chưa có danh mục loại chứng từ: người dùng nhập mã loại; module nghiệp vụ công bố mã loại khi triển khai |
| Màn hình Kỳ kế toán/kỳ kho, Tham số hệ thống, Số chứng từ | BR-UI | Hoàn thành | Lint, TypeScript, build; test giao diện màn kỳ | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giai đoạn 1D - Phê duyệt, thông báo, giới hạn đăng nhập
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Chế độ duyệt theo loại chứng từ; luồng duyệt nhiều bước theo chi nhánh, ngưỡng; chụp bước khi gửi | BR-SYS-11, D-04, D-52 | Hoàn thành | `ApprovalIntegrationTest` (9) | Ngưỡng chiết khấu dùng khi có đơn bán |
| Tách biệt nhiệm vụ: người lập không tự duyệt, một người không duyệt hai bước | BR-SYS-14 | Hoàn thành (tự duyệt có test); đã code (hai bước cùng người) | `ApprovalIntegrationTest` | Chưa có test riêng cho trường hợp một người duyệt hai bước |
| Ủy quyền duyệt có thời hạn, thu hồi | BR-SYS-11 | Hoàn thành | `ApprovalIntegrationTest` | |
| Thông báo trong ứng dụng (chờ duyệt, kết quả duyệt), chống trùng theo `event_key` | BR-SYS-25, D-49 | Hoàn thành | `ApprovalIntegrationTest` | Chưa gửi email |
| Gán vai trò vượt quyền có kiểm soát (`access:grant-any`); cấm sửa quyền vai trò mình đang giữ | BR-SYS-03 | Hoàn thành | `AccessGrantIntegrationTest` (2) | |
| Giới hạn đăng nhập sai theo IP | security.md mục 7 | Hoàn thành | `LoginRateLimiterTest` | Bộ đếm trong bộ nhớ một tiến trình |
| Màn hình Việc chờ duyệt, Ủy quyền, Loại chứng từ, Luồng duyệt; chuông thông báo | BR-UI | Hoàn thành | Lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giai đoạn 2 - Danh mục
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Hàng hóa: loại, cờ lô / hạn / serial (khóa khi đã có giao dịch kho), quy đổi ĐVT theo hiệu lực, định mức tồn | BR-MD-01..05, 20, 21 | Hoàn thành | `MasterDataIntegrationTest` (8) | |
| Đơn vị, nhãn hiệu, NSX, nhóm đối tác, quốc gia, danh mục tham chiếu, thuế suất, nhóm hàng (chặn vòng cha con) | BR-MD-03, 10..14, 45, 46 | Hoàn thành (danh mục mã-tên, quốc gia, nhóm hàng có test); đã code (thuế suất, danh mục tham chiếu) | `MasterDataIntegrationTest` | Thuế suất, danh mục tham chiếu chưa có test riêng. Tiền tệ, tỷ giá: chưa làm |
| Đối tác nhiều vai trò, liên hệ, địa chỉ; nhân viên kinh doanh chỉ thấy khách của mình khi `own_data_only` | BR-MD-30..33, BR-SYS-04 | Hoàn thành (phạm vi, lọc vai trò có test); đã code (liên hệ, địa chỉ) | `MasterDataIntegrationTest` | Liên hệ, địa chỉ chưa có test riêng |
| Kho, vị trí (vị trí mặc định), kho ảo; phạm vi theo kho | BR-MD-40, 41 | Hoàn thành | `MasterDataIntegrationTest` | |
| Bảng giá, giá sàn; ẩn giá mua tham khảo khi thiếu quyền | BR-MD-42 | Hoàn thành | `MasterDataIntegrationTest` | Áp giá vào đơn bán ở Giai đoạn 6 |
| Hồ sơ, giấy phép có hạn; gắn với hàng, đối tác, lô, serial | BR-MD-43, BR-INV-05 | Hoàn thành | `MasterDataIntegrationTest` | Chưa đính kèm file (D-41, chờ `/attachments`) |
| Nhập hàng hóa, đối tác từ Excel (tất cả hoặc không, lỗi theo dòng, chống nhập trùng file) | BR-UX-05, BR-MIG-01, D-50 | Hoàn thành (hàng hóa); đã code (đối tác) | `MasterDataIntegrationTest` | Nhập đối tác chưa có test riêng |
| 7 màn hình danh mục | BR-UI | Hoàn thành | Lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giai đoạn 3 - Lõi kho
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Phiếu nhập kho theo lô, hạn dùng, serial, có đơn giá; một lô một hạn dùng; serial không tồn hai nơi | AT-02, BR-INV-01, 02, D-51 | Hoàn thành | `InventoryIntegrationTest.lotsSerialsAndFefo` | Nhập từ mua hàng ở Giai đoạn 5 |
| Xuất nội bộ, xuất hủy; FEFO; chọn lô khác FEFO phải có lý do; lô hết hạn / khóa / không ĐẠT không xuất bán | AT-03 (phần kho), BR-INV-04, 21, 23, 33 | Hoàn thành | `InventoryIntegrationTest.lotsSerialsAndFefo` | Xuất bán theo đơn ở Giai đoạn 6. FIFO cấu hình được: chưa làm |
| Xuất đồng thời không âm, không vượt tồn; số dư = sổ kho | AT-04, BR-INV-20 | Hoàn thành | `InventoryIntegrationTest.concurrentIssues` (5 luồng, 10 tồn, 5×3 xuất: đúng 3 thành công) | Test này phát hiện và đã sửa deadlock do nâng khóa (docs/02 mục 6) |
| Đảo chứng từ kho: dòng ngược đúng từng dòng sổ gốc; chặn khi có chứng từ phụ thuộc, liệt kê số chứng từ | AT-09 (phần kho), BR-GEN-21 | Hoàn thành | `InventoryIntegrationTest.reversal` | |
| Khóa kỳ chặn ghi sổ, không để dữ liệu dở dang | AT-11 (phần kho) | Hoàn thành | `InventoryIntegrationTest.lockedPeriod` | |
| Chống xử lý trùng tạo / ghi sổ; phân quyền, phạm vi kho | AT-13, AT-10 (phần kho) | Hoàn thành | `InventoryIntegrationTest.idempotencyAndAccess` | |
| Chuyển kho cùng chi nhánh; khác chi nhánh qua kho đang vận chuyển, kho đích xác nhận nhận | BR-INV-30 | Hoàn thành | `InventoryIntegrationTest.crossBranchTransfer` | Chuyển cùng chi nhánh: chỉ phủ gián tiếp (cùng mã) |
| Kiểm kê: chặn nhập xuất khi đang đếm, duyệt, sinh phiếu điều chỉnh chênh lệch | BR-INV-31, 32, D-11 | Hoàn thành | `InventoryIntegrationTest.stockCount` | |
| Giữ chỗ giảm tồn khả dụng; chính sách xuất vượt tồn có hạn mức, đã duyệt | BR mục 5.4, BR-GEN-31 | Hoàn thành | `InventoryIntegrationTest.reservationAndPolicy` (gồm D-53: chính sách thứ hai lần bị từ chối, không hủy được sau khi dùng) | Giữ chỗ chưa có màn hình / endpoint (phân hệ bán hàng dùng) |
| Chuyển trạng thái chất lượng; khóa / mở khóa lô, serial; thu hồi (khóa khi duyệt, theo dõi khách) | BR-INV-03, 23, 42 | Đã code, chưa có test tự động | Biên dịch, ArchUnit | Thiếu test tích hợp riêng cho PCL, thu hồi, khóa lô qua API |
| Cảnh báo sắp hết hạn, dưới định mức; đề xuất FEFO | BR-INV-40, 21 | Hoàn thành (đề xuất FEFO), đã code chưa có test (cảnh báo) | `lotsSerialsAndFefo` (FEFO) | Chưa có cảnh báo hàng chậm luân chuyển |
| Điều kiện khóa kỳ kho: không còn chứng từ kho đã duyệt / đang vận chuyển chưa ghi sổ | BR-SYS-31 | Đã code, chưa có test riêng | | |
| Màn hình Tồn kho, Chứng từ kho (lập, sửa, thao tác theo trạng thái), Lô / serial, Kiểm kê, Thu hồi, Chính sách xuất vượt tồn | BR-UI, BR-INV | Hoàn thành, test giao diện một phần | `DocumentActions.test.tsx` (2), `documentFormValues.test.ts` (1), `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện. Chọn vị trí kho trên dòng chứng từ: chưa có trên giao diện (dùng vị trí mặc định) |

## Giả định đã dùng
- Giai đoạn 3 (chốt 2026-10-10): ghi sổ kho ở READ COMMITTED với thứ tự khóa cố định (ADR-08); API chung `/inventory-documents` (ADR-10); chế độ duyệt mặc định của loại chứng từ kho và ngưỡng cảnh báo hạn dùng 90 ngày (D-52); mỗi chính sách xuất vượt tồn một chứng từ (D-53). Luồng duyệt mẫu chỉ là dữ liệu dev/test.
- Giai đoạn 3: phiếu điều chỉnh sinh từ kiểm kê đã duyệt không qua luồng duyệt riêng (phiếu kiểm kê đã được duyệt).
- Không có profile mặc định; thiếu `SPRING_PROFILES_ACTIVE` thì chạy với cấu hình gốc an toàn cho production.
- Dữ liệu mẫu dev dùng migration repeatable `R__dev_*.sql`; mật khẩu mẫu `Erp@2026` chỉ cho dev/test.
- DTO request/response đặt ở `application/dto` (cập nhật `.claude/rules/architecture.md`).
- Mã lỗi mới ngoài docs/04 bản 0.2: `MALFORMED_REQUEST`, `METHOD_NOT_ALLOWED`, `REQUEST_IN_PROGRESS`, `DATA_INTEGRITY_VIOLATION` (đã cập nhật docs/04).
