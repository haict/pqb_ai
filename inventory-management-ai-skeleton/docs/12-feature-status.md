# Trạng thái tính năng

| Thuộc tính | Giá trị |
|---|---|
| Ngày cập nhật | 2026-10-14 |
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
| Tồn kho đầu kỳ (menu Kho → Tồn kho đầu kỳ): phiếu nhập PNK loại `OPENING` theo lô, hạn dùng, serial; lô tạm chọn trạng thái Cách ly; duyệt, ghi sổ, đảo như phiếu kho | BR-MIG-02, D-13 | Hoàn thành (nhập tay; nhập Excel 2026-10-17) | `InventoryControlIntegrationTest.openingStock`, `InventoryControlIntegrationTest.openingStockImport` | Nhập Excel (BR-UX-05): file mẫu có danh sách chọn trạng thái chất lượng (trống = Đạt); mỗi kho một phiếu nháp, gửi duyệt / ghi sổ như phiếu lập tay; lỗi theo dòng, có lỗi thì không lập phiếu nào; dùng quyền `inventory-receipt:*`; kiểm thử thủ công trên trình duyệt chưa thực hiện |
| Xuất nội bộ, xuất hủy; FEFO; chọn lô khác FEFO phải có lý do; lô hết hạn / khóa / không ĐẠT không xuất bán | AT-03 (phần kho), BR-INV-04, 21, 23, 33 | Hoàn thành | `InventoryIntegrationTest.lotsSerialsAndFefo` | Xuất bán theo đơn ở Giai đoạn 6. FIFO cấu hình được: chưa làm |
| Xuất đồng thời không âm, không vượt tồn; số dư = sổ kho | AT-04, BR-INV-20 | Hoàn thành | `InventoryIntegrationTest.concurrentIssues` (5 luồng, 10 tồn, 5×3 xuất: đúng 3 thành công) | Test này phát hiện và đã sửa deadlock do nâng khóa (docs/02 mục 6) |
| Đảo chứng từ kho: dòng ngược đúng từng dòng sổ gốc; chặn khi có chứng từ phụ thuộc, liệt kê số chứng từ | AT-09 (phần kho), BR-GEN-21 | Hoàn thành | `InventoryIntegrationTest.reversal` | |
| Khóa kỳ chặn ghi sổ, không để dữ liệu dở dang | AT-11 (phần kho) | Hoàn thành | `InventoryIntegrationTest.lockedPeriod` | |
| Chống xử lý trùng tạo / ghi sổ; phân quyền, phạm vi kho | AT-13, AT-10 (phần kho) | Hoàn thành | `InventoryIntegrationTest.idempotencyAndAccess` | |
| Chuyển kho cùng chi nhánh; khác chi nhánh qua kho đang vận chuyển, kho đích xác nhận nhận | BR-INV-30 | Hoàn thành | `InventoryIntegrationTest.crossBranchTransfer`, `InventoryControlIntegrationTest.sameBranchTransfer` (gồm đảo) | |
| Kiểm kê: chặn nhập xuất khi đang đếm, duyệt, sinh phiếu điều chỉnh chênh lệch | BR-INV-31, 32, D-11 | Hoàn thành | `InventoryIntegrationTest.stockCount` | |
| Giữ chỗ giảm tồn khả dụng; chính sách xuất vượt tồn có hạn mức, đã duyệt | BR mục 5.4, BR-GEN-31 | Hoàn thành | `InventoryIntegrationTest.reservationAndPolicy` (gồm D-53: chính sách thứ hai lần bị từ chối, không hủy được sau khi dùng) | Giữ chỗ chưa có màn hình / endpoint (phân hệ bán hàng dùng) |
| Chuyển trạng thái chất lượng; khóa / mở khóa lô, serial; thu hồi (khóa khi duyệt, theo dõi khách) | BR-INV-03, 23, 42, D-56 | Hoàn thành | `InventoryControlIntegrationTest.qualityStatusChange`, `lotBlocking`, `recall` | Khóa / mở khóa serial qua API: chưa có test riêng (cùng cơ chế với lô). Nhập kho mặc định ĐẠT (D-56 thay D-09) |
| Cảnh báo sắp hết hạn, dưới định mức; đề xuất FEFO | BR-INV-40, 21, D-52 | Hoàn thành | `lotsSerialsAndFefo` (FEFO), `InventoryControlIntegrationTest.stockAlerts` (cảnh báo) | Chưa có cảnh báo hàng chậm luân chuyển |
| Điều kiện khóa kỳ kho: không còn chứng từ kho đã duyệt / đang vận chuyển chưa ghi sổ | BR-SYS-31 | Hoàn thành | `InventoryControlIntegrationTest.periodCloseCheck` | |
| Màn hình Tồn kho, Chứng từ kho (lập, sửa, thao tác theo trạng thái), Lô / serial, Kiểm kê, Thu hồi, Chính sách xuất vượt tồn | BR-UI, BR-INV | Hoàn thành, test giao diện một phần | `DocumentActions.test.tsx` (2), `documentFormValues.test.ts` (1), `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện. Chọn vị trí kho trên dòng chứng từ: chưa có trên giao diện (dùng vị trí mặc định) |

## Giai đoạn 4A - Kế toán lõi
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Hệ thống tài khoản dạng cây: mã con theo mã cha, tài khoản có con thành tổng hợp, không thêm con dưới tài khoản đã hạch toán, ngừng thay vì xóa | BR-ACC-01 | Hoàn thành | `AccountingIntegrationTest.chartOfAccounts`, `postAndReverse` | Hệ thống tài khoản mặc định chỉ có ở dữ liệu dev/test (TT133 `[Chưa kiểm chứng]`); production nạp bằng Excel sau khi kế toán trưởng xác nhận |
| Nhập hệ thống tài khoản từ Excel (tất cả hoặc không, lỗi theo dòng) | BR-ACC-01, BR-MIG-01 | Hoàn thành | `AccountingIntegrationTest.importAccounts` | |
| Quy tắc hạch toán cấu hình: loại chứng từ + mã nghiệp vụ (+ loại hàng, vai trò đối tượng), vế do chứng từ quyết định, hiệu lực theo ngày | BR-ACC-02, ADR-11 | Hoàn thành | `postAndReverse` (quy tắc cụ thể thắng), `rejectsInvalidPostings` | Chứng từ thu chi (4C) dùng quy tắc mẫu ở dev/test; quy tắc thật chờ kế toán trưởng (D-34, D-35) |
| `JournalPostingService`: ghi bút toán cân Nợ = Có trong transaction chứng từ nguồn; một bút toán gốc mỗi chứng từ; đảo đổi vế từng dòng | BR-ACC-03, BR-GEN-20, 21, AT-09, AT-13 | Hoàn thành | `postAndReverse`, `rejectsInvalidPostings` | Gọi trực tiếp dịch vụ trong test; chưa có chứng từ nghiệp vụ nào gọi (4B, 4C) |
| Chặn bút toán vào kỳ kế toán đã khóa | AT-11, BR-ACC-05 | Hoàn thành | `AccountingIntegrationTest.lockedAccountingPeriod` | |
| Màn hình Bút toán (lọc ngày, loại chứng từ, tài khoản, trạng thái; chi tiết Nợ / Có), Hệ thống tài khoản (cây, nhập Excel), Quy tắc hạch toán | BR-UI, BR-ACC | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện. Cột đối tượng trên dòng bút toán hiện ID (chưa tra tên) |

## Giai đoạn 4B - Sổ công nợ
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Khoản công nợ theo chứng từ nguồn (phải thu / phải trả), sổ chi tiết công nợ bất biến; `DebtPostingService` ghi / đảo, kiểm tra kỳ công nợ | BR-AR-01, BR-AP-01, ADR-12 | Hoàn thành | `DebtIntegrationTest` (các kịch bản kiểm tra số còn lại = số dư sổ chi tiết) | Hóa đơn bán / mua gọi ở Giai đoạn 5, 6 |
| Công nợ đầu kỳ theo từng hóa đơn: lập, sửa, duyệt (Kế toán trưởng), ghi sổ, đảo; hóa đơn đã có công nợ bị chặn; đối tượng phải đúng vai trò | BR-MIG-01, P-07, AT-09, AT-13 | Hoàn thành | `openingReceivables`, `reverseOpening`, `accessAndConcurrency` | Không sinh bút toán `[Giả định]`: số dư đầu kỳ tài khoản nhập ở Giai đoạn 11. Chưa nhập từ Excel |
| Hạn thanh toán mặc định = ngày hóa đơn + số ngày nợ của đối tượng | BR-AR-02 | Hoàn thành | `openingReceivables`, `reverseOpening` | Lịch thu tiền, hợp đồng (ưu tiên trước) ở Giai đoạn 6, 7 |
| Tuổi nợ theo mốc tham số; danh sách quá hạn | BR-AR-05, BR-AP-02 | Hoàn thành | `openingReceivables` | Chưa lọc theo nhân viên kinh doanh, hợp đồng (Giai đoạn 6, 7) |
| Khóa kỳ công nợ chặn ghi sổ | AT-11 | Hoàn thành | `lockedDebtPeriod` | |
| Phân quyền, phạm vi chi nhánh, sửa đồng thời | AT-10, BR-GEN-53 | Hoàn thành | `accessAndConcurrency` | |
| Màn hình Công nợ phải thu / phải trả (theo hóa đơn, tuổi nợ, sổ chi tiết), Công nợ đầu kỳ | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |
| Sửa lỗi Giai đoạn 3: sửa phiếu kho (PUT) có dòng luôn lỗi 409 do Hibernate chèn dòng mới trước khi xóa dòng cũ | BR-GEN-01 | Hoàn thành | `InventoryControlIntegrationTest.editDraftLines` | Phát hiện khi viết test 4B |

## Giai đoạn 4C - Quỹ, ngân hàng
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Quỹ tiền mặt, tài khoản ngân hàng (tài khoản kế toán, số tài khoản); số dư tính từ sổ quỹ; quỹ còn số dư không ngừng được | BR-CSH-01, BR-MD-46 | Hoàn thành | `CashIntegrationTest` (mọi kịch bản kiểm tra số dư quỹ = sổ quỹ) | Chỉ VND; ngoại tệ P2 (BR-AP-03). Chưa có số dư đầu kỳ quỹ: nhập bằng phiếu thu khác hoặc công cụ chuyển đổi Giai đoạn 11 |
| Chứng từ thu chi một bảng: phiếu thu, phiếu chi, giấy báo có, ủy nhiệm chi, chuyển tiền nội bộ; lập, sửa, gửi duyệt, duyệt, từ chối, hủy, ghi sổ, đảo | BR-CSH-02, ADR-13 | Hoàn thành | `receiptAllocation`, `paymentApprovalAndCashLimit`, `transferFlowPeriodAndAccess` | In phiếu thu / chi: chưa làm |
| Phân bổ thu tiền / trả tiền: tự động hóa đơn đến hạn sớm nhất trước, hoặc chỉ định hóa đơn; thu một phần, nhiều hóa đơn; phần dư là trả trước | BR-AR-03, BR-AP-02, AT-08 | Hoàn thành | `receiptAllocation` | Phân bổ trong cùng chi nhánh `[Giả định]`; trả trước chưa đối trừ được (4D); chưa có chiết khấu thanh toán (Giai đoạn 6) |
| Chi phải duyệt theo ngưỡng (tham số), người lập không tự duyệt; quỹ tiền mặt không âm | BR-CSH-03 | Hoàn thành | `paymentApprovalAndCashLimit`, `concurrentPayments` | Tài khoản ngân hàng không chặn âm `[Giả định]` |
| Đồng thời: hai phiếu cùng phân bổ một hóa đơn, hai phiếu cùng chi một quỹ | BR-GEN-52 | Hoàn thành | `concurrentAllocation`, `concurrentPayments` | |
| Đảo chứng từ thu chi: hoàn phân bổ, sổ quỹ ngược, bút toán đảo; chặn đảo làm quỹ âm; chặn đảo công nợ đầu kỳ đã được thu | AT-09, BR-GEN-21 | Hoàn thành | `reverseReceipt` | |
| Bút toán tự sinh theo quy tắc hạch toán: tài khoản tiền lấy từ quỹ, tài khoản công nợ từ quy tắc, tài khoản đối ứng thu chi khác theo dòng | BR-ACC-02, ADR-11 | Hoàn thành | `receiptAllocation`, `paymentApprovalAndCashLimit`, `transferFlowPeriodAndAccess` | Quy tắc mẫu chỉ ở dev/test `[Chưa kiểm chứng]`; kế toán trưởng cấu hình trước UAT |
| Sổ quỹ / sổ tiền gửi, dòng tiền thực tế theo quỹ và nội dung | BR-CSH-01, BR-CSH-06 (phần thực tế) | Hoàn thành | `paymentApprovalAndCashLimit`, `transferFlowPeriodAndAccess` | Dòng tiền dự kiến: Giai đoạn 10D |
| Khóa kỳ quỹ chặn ghi sổ; điều kiện khóa kỳ quỹ: không còn chứng từ thu chi chờ duyệt / đã duyệt chưa ghi sổ | AT-11, BR-SYS-31 | Hoàn thành (chặn ghi sổ); điều kiện khóa kỳ đã code, chưa có test riêng | `transferFlowPeriodAndAccess` | |
| Phân quyền, phạm vi chi nhánh | AT-10 | Hoàn thành | `paymentApprovalAndCashLimit`, `transferFlowPeriodAndAccess` | |
| Màn hình Thu chi, ngân hàng; Sổ quỹ, dòng tiền; Quỹ, tài khoản ngân hàng | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |
| Thu hàng loạt nhiều khách | BR-AR-08 | Chưa làm | | |
| Nhập sao kê ngân hàng | BR-CSH-07 | Dời giai đoạn (P2) | | |

## Giai đoạn 4D - Bù trừ, điều chỉnh, tạm ứng, đối chiếu, kiểm kê quỹ
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Tiền trả trước có số dư khóa được; đảo phiếu thu có trả trước đã bù trừ bị chặn | BR-AR-03, 04, BR-GEN-52 | Hoàn thành | `prepaymentOffset`, `concurrentPrepaymentOffsets` | |
| Bù trừ trả trước với hóa đơn; bù trừ phải thu với phải trả cùng đối tượng; bỏ bù trừ bằng đảo | BR-AR-04 | Hoàn thành | `prepaymentOffset`, `receivablePayableOffset` | Bù trừ khác chi nhánh, khác đối tượng (bên thứ ba): chưa làm |
| Bảng đối chiếu công nợ (kỳ, phát sinh, hóa đơn còn nợ, trả trước); biên bản chốt số dư và ghi nhận xác nhận; in từ giao diện | BR-AR-06, AT-08 | Hoàn thành (trừ gửi email) | `reconciliationAndAdjustment` | Gửi email, thông báo công nợ, giấy đề nghị thanh toán: chưa làm. Hóa đơn còn nợ chỉ hiện khi kỳ đến hôm nay |
| Điều chỉnh công nợ tăng / giảm có duyệt, gắn biên bản đối chiếu, bút toán theo quy tắc; đảo | BR-AR-07 | Hoàn thành | `reconciliationAndAdjustment` | "Theo quyền hạn" = ngưỡng `min_amount` của luồng duyệt DCCN |
| Tạm ứng nhân viên (phiếu chi tạm ứng sinh công nợ tạm ứng có hạn), thanh toán tạm ứng bằng chi phí (TTTU), hoàn ứng bằng tiền; xem công nợ tạm ứng theo khoản, tuổi, sổ chi tiết | BR-CSH-04, ADR-14 | Hoàn thành | `employeeAdvance` | Giấy đề nghị tạm ứng / thanh toán nội bộ riêng: chưa làm (lập thẳng phiếu chi) |
| Kiểm kê quỹ tiền mặt có duyệt, ghi sổ chênh lệch thừa / thiếu, đảo | BR-CSH-05 | Hoàn thành | `cashCount` | Chưa có bảng kê mệnh giá; tài khoản xử lý chênh lệch mẫu 711 / 811 `[Chưa kiểm chứng]` |
| Điều kiện khóa kỳ: kỳ quỹ / kỳ công nợ còn chứng từ chờ duyệt / đã duyệt chưa ghi sổ thì chưa khóa được | BR-SYS-31 | Đã code, chưa có test riêng | | |
| Màn hình Bù trừ, Điều chỉnh công nợ, Thanh toán tạm ứng, Đối chiếu công nợ, Kiểm kê quỹ, Công nợ tạm ứng; nội dung tạm ứng / hoàn ứng trên chứng từ thu chi | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giai đoạn 5A - Đề nghị mua, đơn mua, hàng đang về (lịch sử; luồng chứng từ thay bằng D-54)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Đề nghị mua lập tay, duyệt theo luồng (khi cấu hình), đóng phần chưa đặt; danh sách dòng còn chưa đặt | BR-PUR-01 | Hoàn thành phần lập tay | `requisitionToOrder` | Đề nghị từ đơn bán thiếu hàng (GĐ6), hợp đồng thầu (GĐ7), cảnh báo dưới định mức (GĐ10): chưa làm |
| Đơn mua: NCC, kho nhận, hàng, số lượng, đơn giá, chiết khấu %, thuế suất (mặc định theo hàng), ngày giao, hạn thanh toán (mặc định theo NCC); tiền do server tính, làm tròn đến đồng | BR-PUR-04, D-05 | Hoàn thành | `requisitionToOrder`, `validation` | Chỉ VND (nhập khẩu P2, D-24) |
| Duyệt đơn mua theo ngưỡng tham số; từ chối, sửa, gửi lại; tự duyệt dưới ngưỡng | BR-PUR-04, D-04 | Hoàn thành | `thresholdApproval` | |
| Đơn mua theo đề nghị: không vượt số còn lại, cập nhật số đã đặt khi duyệt / hủy | BR-PUR-01, BR mục 5.5 | Hoàn thành | `requisitionToOrder` | Đóng đơn mua không trả lại số đã đặt cho đề nghị `[Giả định]` |
| Tiến độ từng dòng (đặt / đã nhận / đã nhập / lỗi / đã nhận HĐ / đã đóng / còn thiếu), đóng phần còn lại có lý do | BR-PUR-05 | Hoàn thành khung; số nhận / nhập / HĐ cập nhật ở 5B, 5C | `requisitionToOrder` | Đóng theo cả đơn, chưa đóng từng dòng |
| Hàng đang về theo (hàng, kho): + khi duyệt, − khi hủy / đóng | BR mục 5.4, BR-GEN-30 | Hoàn thành phần đơn mua | `requisitionToOrder`, `thresholdApproval` | Trừ khi nhập kho ở 5B |
| Màn hình Đề nghị mua hàng, Đơn mua hàng (lập theo đề nghị, tiến độ dòng, thao tác theo trạng thái / quyền) | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |
| Admin toàn quyền trên máy dev | (tiện ích dev) | Đã tạo file `db/dev-local`, chờ người dùng thêm location vào `application-dev.yml` | | Sửa cấu hình bị công cụ chặn (giảm bảo mật) |

## Giai đoạn 5B - Phiếu nhận hàng, nhập kho mua hàng (lịch sử; luồng chứng từ thay bằng D-54)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Phiếu nhận hàng theo đơn mua: số thực nhận, kết quả kiểm nhận (đạt / cách ly / trả lại) có lý do, lô / hạn / serial; ghi sổ cập nhật đã nhận, lỗi của đơn mua | BR-PUR-07, BR-PUR-05 | Hoàn thành | `receiveInBatches` | Chưa có đính kèm ảnh / biên bản hàng lỗi |
| Nhận vượt số đặt quá dung sai phải duyệt | BR-PUR-07 | Hoàn thành | `overToleranceAndValidation` | Dung sai một tham số chung cho mọi hàng `[Giả định]` |
| Nhập kho theo phiếu nhận hàng thành phiếu nhập kho PNK riêng (có nguồn, truy vết hai chiều); hàng lỗi cách ly; trừ hàng đang về | BR-PUR-08, BR mục 5.4 | Hoàn thành | `receiveInBatches` | Nhập kho cả phiếu một lần (không nhập từng phần một phiếu nhận) `[Giả định]` |
| Nhận hàng và nhập kho trong một thao tác | D-36 | Hoàn thành | `receiveInBatches`, `overToleranceAndValidation` | |
| Đảo phiếu nhận hàng (kể cả PNK); chặn đảo trực tiếp PNK có nguồn | BR-GEN-21, AT-09 | Hoàn thành | `receiveInBatches` | |
| Hàng về giữ chỗ cho đơn bán / hợp đồng | BR-PUR-06 | Chưa làm (Giai đoạn 6, 7) | | |
| Màn hình Phiếu nhận hàng (chọn đơn còn mở, dòng theo lô / kết quả, nhập kho ngay); phiếu kho hiện chứng từ nguồn | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giai đoạn 5C - Hóa đơn mua, công nợ phải trả (lịch sử; luồng chứng từ thay bằng D-54)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Hóa đơn mua: số, ký hiệu, ngày, MST người bán; nhiều phiếu nhận một hóa đơn và một phiếu nhận nhiều hóa đơn; chống trùng (MST, ký hiệu, số) | BR-PUR-09 | Hoàn thành | `invoiceReceiptsAndService` | Chưa nhập từ XML hóa đơn điện tử |
| Hàng về trước, hóa đơn về sau: phiếu nhập giá tạm tính theo đơn mua; hóa đơn ghi chênh lệch giá theo dòng phiếu nhận | BR-PUR-10, D-34 | Hoàn thành phần ghi nhận chênh lệch | `invoiceReceiptsAndService` | Điều chỉnh giá vốn hàng đã xuất: Giai đoạn 8 (dùng `purchase_cost_entries`) |
| Đối chiếu ba chiều: chặn vượt số đã nhập chưa có hóa đơn; cảnh báo đơn giá lệch đơn mua quá dung sai | BR-PUR-11 | Hoàn thành | `invoiceReceiptsAndService` | Cảnh báo tính khi xem, không lưu |
| Công nợ phải trả và bút toán khi ghi sổ hóa đơn (quy tắc `HDM`); đảo hoàn công nợ, bút toán; đã thanh toán thì không đảo | D-34, BR-AP | Hoàn thành | `invoiceReceiptsAndService` | Tài khoản mẫu 1561 / 6422 / 1331 / 331 `[Chưa kiểm chứng]` |
| Mua dịch vụ không qua kho (theo dòng đơn mua dịch vụ hoặc không) | BR-PUR-16 | Hoàn thành phần hóa đơn | `invoiceReceiptsAndService` | Phân bổ dịch vụ vào giá nhập: 5D |
| Hàng khuyến mại từ NCC (cờ trên dòng, giá trị 0) | BR-PUR-17, D-26 | Hoàn thành mức tối thiểu | | Cách ghi nhận giá trị / thuế chờ kế toán trưởng `[Chưa kiểm chứng]` |
| Màn hình Hóa đơn mua (lấy dòng phiếu nhận chưa có hóa đơn, dòng dịch vụ, cảnh báo đối chiếu, bút toán) | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giai đoạn 5D - Trả hàng NCC, giảm giá hàng mua, chi phí mua (lịch sử; luồng chứng từ thay bằng D-54)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Trả hàng NCC theo phiếu nhận gốc, đúng lô / serial, trạng thái chất lượng; hàng chưa có hóa đơn chỉ giảm tồn, hàng đã có hóa đơn giảm công nợ phải trả và bút toán | BR-PUR-14, AT-06 | Hoàn thành | `returnsAndDiscount` | Trả hàng không mở lại phần còn thiếu của đơn mua `[Giả định]`; hàng hết hạn phải chuyển trạng thái trước khi trả |
| Giảm giá hàng mua theo dòng hóa đơn: giảm công nợ, giá trị nhập (`DISCOUNT`), bút toán | BR-PUR-14 | Hoàn thành | `returnsAndDiscount` | |
| Chi phí mua hàng (dịch vụ trên hóa đơn mua) phân bổ vào phiếu nhận theo giá trị / số lượng | BR-PUR-12, 16, BR-CST-02 | Hoàn thành phần ghi nhận | `landedCostAllocation` | Điều chỉnh giá vốn hàng đã xuất: Giai đoạn 8; chi phí nhập khẩu theo tờ khai: P2 |
| Đảo trả hàng / giảm giá / phân bổ; chặn đảo hóa đơn, phiếu nhận khi có chứng từ phụ thuộc | AT-09 | Hoàn thành | `returnsAndDiscount`, `landedCostAllocation` | |
| Xuất kho theo chứng từ nguồn (`StockOutService`), giữ chỗ dùng một phần / hoàn khi đảo / nhả quá hạn | BR-SAL-13 (hạ tầng), D-37 | Hoàn thành phần kho | `returnsAndDiscount` (xuất trả) | Xuất bán dùng ở Giai đoạn 6 |
| Màn hình Trả hàng / giảm giá, Phân bổ chi phí mua | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |
| Yêu cầu báo giá NCC, so sánh báo giá, lịch sử / đánh giá NCC | BR-PUR-02, 03, 18 | Chưa làm (5E) | | |

## Giai đoạn 6 - Bán hàng (lịch sử; luồng chứng từ thay bằng D-54)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Lấy giá theo thứ tự ưu tiên, giá sàn, chiết khấu đại lý mặc định | BR-SAL-02, BR-MD-42 | Hoàn thành (trừ giá hợp đồng thầu) | `quoteLifecycle` | Giá hợp đồng / trúng thầu: Giai đoạn 7 |
| Báo giá nhiều phiên bản; duyệt giá dưới sàn / chiết khấu vượt ngưỡng; gửi khách, chấp nhận / từ chối / hết hạn; chuyển đơn bán; in báo giá | BR-SAL-01, 03, 04, 05 | Hoàn thành | `quoteLifecycle` | Bản in gồm hàng, giá, điều kiện; thông số kỹ thuật / số lưu hành chưa in trên báo giá; xuất PDF qua chức năng in của trình duyệt |
| Đơn bán; duyệt theo hạn mức tín dụng / nợ quá hạn; cảnh báo tồn khả dụng; giữ / nhả hàng có hạn; đóng phần còn lại; đề nghị mua phần thiếu | BR-SAL-10, 12, 13, 14, 15, D-22, D-37 | Hoàn thành | `orderCreditReservationAndShortage` | Liên kết hợp đồng / gói thầu: Giai đoạn 7 |
| Xuất kho theo đơn FEFO / lô chỉ định, giao nhiều đợt, đang giao / khách nhận / thất bại, biên bản giao nhận | BR-SAL-20..23, AT-03, AT-05 | Hoàn thành | `multipleDeliveries` | Danh mục CO/CQ và file biên bản ký: chưa có (ghi số biên bản); phiếu lắp đặt BR-SAL-24: Giai đoạn 9 |
| Hóa đơn bán từ nhiều phiếu giao + dịch vụ; cảnh báo lập trước khi giao; số HĐĐT MISA chống trùng; công nợ phải thu, bút toán doanh thu; đảo | BR-SAL-30, 31, D-35 | Hoàn thành | `invoiceDeliveriesAndService` | Chưa lập hóa đơn trước khi xuất kho; giá vốn ghi ở Giai đoạn 8 |
| Trả hàng bán đúng lô / serial vào cách ly, giảm doanh thu, công nợ, số đã giao; giảm giá hàng bán; đổi hàng | BR-SAL-32, 33, 36, AT-06 | Hoàn thành phần kho, công nợ, doanh thu | `returnDiscountExchange` | Giảm giá vốn hàng trả lại: Giai đoạn 8 (phiếu nhập lại đơn giá 0 `[Giả định]`); cập nhật hợp đồng: Giai đoạn 7 |
| Tiếp nhận hàng lỗi khách báo, kiểm tra, quyết định | BR-SAL-34 | Hoàn thành mức tối thiểu | `complaint` | Sửa chữa / bảo hành liên kết M09: Giai đoạn 9 |
| Chiết khấu dòng, hàng tặng (dòng khuyến mại giá 0), chiết khấu đại lý | BR-SAL-35 | Một phần | `quoteLifecycle` | Chương trình khuyến mại, chiết khấu đơn / thanh toán: chưa làm; theo doanh số kỳ: P2 |
| Bán nhanh: đơn → giao (khách nhận) → hóa đơn ghi sổ, chứng từ riêng liên kết | BR-SAL-42 | Hoàn thành (không gồm phiếu thu) | `quickSale` | Phiếu thu lập ở Thu chi |
| Lịch thu tiền theo đợt, mốc ngày đơn / giao / hóa đơn, số đã thu | BR-SAL-50 | Hoàn thành (trừ mốc nghiệm thu) | `reportAndSchedule` | Mốc nghiệm thu: Giai đoạn 7; tiền đã thu chia theo tỷ lệ khi hóa đơn gồm nhiều đơn `[Giả định]` |
| Doanh số, giảm trừ, công nợ theo nhân viên / khách / hàng / chi nhánh, truy ngược chứng từ | BR-SAL-51, AT-12 (một phần) | Hoàn thành phần doanh thu | `reportAndSchedule` | Lợi nhuận gộp cần giá vốn (Giai đoạn 8) |
| Màn hình Báo giá, Đơn bán (bán nhanh), Giao hàng (in biên bản), Hóa đơn bán, Trả hàng / giảm giá bán, Hàng lỗi, Báo cáo doanh số | BR-UI | Hoàn thành, test giao diện một phần | `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## D-54 - Gộp chứng từ mua / bán (2026-10-12, V22, V23, ADR-17)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Nhà cung cấp bắt buộc MST, địa chỉ (form, API, nhập Excel); danh sách đối tác hiện cột địa chỉ; đơn mua hiển thị MST, địa chỉ người bán theo danh mục, không sửa (2026-10-13) | BR-MD-34 | Hoàn thành | `MasterDataIntegrationTest.partnerOwnData`, `PurchaseOrderIntegrationTest.postAndReverse` | Hiển thị luôn theo danh mục hiện tại: NCC đổi MST / địa chỉ thì đơn cũ hiển thị giá trị mới (người dùng chọn). MST dùng chống trùng hóa đơn vẫn lưu trên đơn lúc lưu. NCC cũ thiếu MST / địa chỉ phải bổ sung khi sửa |
| Kiểm tra lô / hạn / serial theo cấu hình hàng ngay khi lưu đơn mua (không đợi đến ghi sổ) | BR-INV-01, 02 | Hoàn thành | `PurchaseOrderIntegrationTest.validation` | |
| Phiếu nhập / xuất kho in thêm phần thuế: cộng tiền hàng, thuế suất GTGT, tiền thuế GTGT, tổng tiền thanh toán; số tiền bằng chữ theo tổng thanh toán (2026-10-13) | BR-UI | Hoàn thành, chưa thử in trên trình duyệt | `PurchaseOrderIntegrationTest.postAndReverse`, `SalesOrderIntegrationTest.postDeliverAndFail` | Chỉ tính dòng hàng hóa (dịch vụ không in); nhiều thuế suất thì in các mức cách nhau dấu phẩy, tiền thuế là tổng |
| In phiếu xuất kho từ đơn bán đã ghi sổ, cùng mẫu phiếu nhập kho (lô / hạn thực xuất, serial dưới tên hàng, giá bán trước thuế) (2026-10-13) | BR-SAL-21, BR-UI | Hoàn thành, chưa thử in trên trình duyệt | `SalesOrderIntegrationTest.postDeliverAndFail` | Nợ / Có lấy theo bút toán doanh thu phần hàng hóa (dev: 131 / 5111 `[Chưa kiểm chứng]`) theo lựa chọn người dùng; phiếu xuất kho chuẩn thường ghi giá vốn (Nợ 632 / Có 156) `[Suy luận]`, giá vốn ở Giai đoạn 8 |
| In phiếu nhập kho từ đơn mua đã ghi sổ theo mẫu doanh nghiệp (Nợ / Có, người giao, hóa đơn, lô / hạn, số tiền bằng chữ, chỗ ký) (2026-10-13) | BR-PUR-08, BR-UI | Hoàn thành, chưa thử in trên trình duyệt | `PurchaseOrderIntegrationTest.postAndReverse`, `moneyWords.test.ts` | Chỉ dòng hàng hóa; đơn giá / thành tiền trước thuế; Nợ / Có lấy từ bút toán phần hàng hóa (dev: 1561 / 331 `[Chưa kiểm chứng]`); không có test 403 / 404 riêng cho API in (người dùng bỏ qua) |
| Chọn công ty in đầu phiếu nhập kho (đơn mua) và phiếu xuất kho (đơn bán) (mặc định công ty người dùng; công ty đang hoạt động trong phạm vi); đầu phiếu in tên + địa chỉ công ty đã chọn; in theo công ty khác ghi audit (2026-10-16) | BR-PUR-08, BR-SAL-21, D-58 | Hoàn thành, chưa thử trên trình duyệt | `PurchaseOrderIntegrationTest.postAndReverse`, `SalesOrderIntegrationTest.postDeliverAndFail` | Biên bản giao nhận không in tên công ty; phiếu kho lập tay chưa có chức năng in |
| Tải phiếu nhập kho / phiếu xuất kho về máy dạng Excel (.xlsx), Word (.docx), PDF (.pdf), cùng nội dung bản in (gồm phần thuế GTGT, số tiền bằng chữ) (2026-10-14) | BR-PUR-08, BR-SAL-21, BR-UI | Hoàn thành, chưa thử tải trên trình duyệt | `slipExport.test.ts` | Nút Tải về đặt cạnh nút In trên màn hình chi tiết đơn mua / đơn bán (2026-10-16), theo công ty đang chọn (D-58), chỉ gọi API in phiếu khi bấm; dựng file phía trình duyệt từ dữ liệu API in phiếu; thêm thư viện exceljs 4.4.0, docx 9.7.1, pdfmake 0.3.11 (MIT), chỉ tải khi bấm nút; PDF dùng font Roboto (có đủ dấu tiếng Việt), không phải Times New Roman như bản in; Excel giữ số lượng / tiền là ô số |
| Đơn mua mang hóa đơn NCC (ký hiệu, số, ngày, MST; đủ ba hoặc trống; chống trùng), lô / hạn / serial theo dòng, kho nhập, ngày hạch toán, hạn thanh toán | D-54, BR-PUR-04, 09 | Hoàn thành | `PurchaseOrderIntegrationTest.postAndReverse`, `validation` | Hóa đơn ghi sau khi ghi sổ: chưa hỗ trợ (sửa chỉ ở nháp / bị từ chối) |
| Ghi sổ đơn mua = phiếu nhập kho PNK nguồn DMH + trừ hàng đang về + công nợ phải trả + bút toán; đảo đơn | D-54, BR-PUR-08, D-34, AT-01, AT-09 | Hoàn thành | `postAndReverse` | Tài khoản mẫu `[Chưa kiểm chứng]`; nhận nhiều đợt = nhiều đơn mua |
| Trả hàng / giảm giá / phân bổ chi phí theo dòng đơn mua đã ghi sổ; chặn đảo đơn khi có chứng từ phụ thuộc | BR-PUR-12, 14, 16 | Hoàn thành | `PurchaseReturnIntegrationTest` (2) | |
| Đơn bán là chứng từ ghi sổ duy nhất: ghi sổ = phiếu xuất kho PXK FEFO (dùng giữ chỗ, D-07) + doanh thu + công nợ phải thu + bút toán | D-54, BR-SAL-20..23, 30, D-35, AT-03 | Hoàn thành | `SalesOrderIntegrationTest.postDeliverAndFail` | Giá vốn ở Giai đoạn 8; tài khoản mẫu `[Chưa kiểm chứng]` |
| Số HĐĐT MISA ghi trên đơn trước / sau ghi sổ, chống trùng | BR-SAL-31 | Hoàn thành | `postDeliverAndFail` | Chưa tích hợp API MISA |
| Theo dõi giao hàng trên đơn: chờ giao → đang giao → khách nhận (biên bản, in); giao thất bại = đảo đơn | D-54, AT-05 | Hoàn thành | `postDeliverAndFail` | Giao nhiều đợt = nhiều đơn bán |
| Trả / đổi / giảm giá hàng bán, hàng lỗi theo đơn bán; lịch thu tiền; báo cáo doanh số theo đơn bán đã ghi sổ | BR-SAL-32..36, 50, 51, AT-06, AT-12 | Hoàn thành | `SalesReturnIntegrationTest` (2), `SalesReportIntegrationTest` (2) | Mốc lịch thu "hóa đơn" = ngày HĐĐT hoặc ngày hạch toán `[Giả định]` |
| Bỏ đề nghị mua, yêu cầu báo giá NCC (5E), phiếu nhận, hóa đơn mua, báo giá bán, phiếu giao, hóa đơn bán, bán nhanh (bảng, API, màn hình) | D-54 | Hoàn thành | `navigation.test.ts`; `mvn clean verify` | Dữ liệu cũ của các bảng này bị xóa khi migrate V22 / V23 |
| Màn hình Đơn mua hàng, Đơn bán hàng (ghi sổ, HĐĐT, giao hàng, in), Trả hàng, Phân bổ chi phí | BR-UI | Hoàn thành, test giao diện một phần | lint, TypeScript, `npm test`, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |
| Nhập dòng hàng đơn mua từ Excel (nút "Nhập dòng từ Excel" trong form lập / sửa đơn mua; nhà cung cấp, kho, hóa đơn chọn trên form) (2026-10-17) | BR-PUR-04, BR-UX-05 | Hoàn thành, chưa thử trên trình duyệt | `PurchaseOrderIntegrationTest.importLines` | Chỉ điền form, không tự lưu đơn; dòng nhập nối sau dòng đã chọn hàng; serial chỉ kiểm tra số lượng khi lưu đơn |
| Nhập dòng hàng đơn bán từ Excel (nút "Nhập dòng từ Excel" trong form lập / sửa đơn bán; khách, kho, giao hàng chọn trên form) (2026-10-17) | BR-SAL-01, BR-UX-05 | Hoàn thành, chưa thử trên trình duyệt | `SalesOrderIntegrationTest.importLines` | Chỉ điền form, không tự lưu đơn; giá / chiết khấu / lô để trống thì server lấy theo bảng giá, chiết khấu đại lý, FEFO khi lưu / ghi sổ; tồn khả dụng, hạn mức kiểm tra khi lưu / gửi duyệt như lập tay |

## Sổ chi tiết bán hàng S16-DNN (2026-10-14, V24)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Sổ chi tiết bán hàng theo hàng hóa: dòng đơn bán (doanh thu), trả lại / giảm giá (giảm trừ "Khác"), chứng từ đảo (dòng âm theo ngày đảo), TK đối ứng từ bút toán gốc, cộng phát sinh, doanh thu thuần; thống kê theo khách / nhóm VTHH; lọc kỳ, đơn vị, nhân viên, nhóm KH, nhóm VTHH (gồm nhóm con), nhiều hàng, nhiều khách | BR-RPT-26, H-04, H-06, AT-12 (một phần), AT-10 | Hoàn thành phần doanh thu | `SalesLedgerIntegrationTest` (3 test), `ledgerContent.test.ts`, `navigation.test.ts` | Giá vốn, lãi gộp để trống kèm ghi chú đến Giai đoạn 8. Cột giảm trừ "Thuế" (TTĐB, xuất khẩu) để trống: chưa có nghiệp vụ `[Suy luận]`. Dòng trả lại không ghi số lượng vào cột doanh thu. Ký hiệu mẫu, dòng "Ban hành theo TT133" in theo hiện trạng H-04 `[Chưa kiểm chứng]`, kế toán trưởng xác nhận trước UAT. "Sổ này có ... trang", "Ngày mở sổ" để chấm trống |
| Màn hình Sổ chi tiết bán hàng (menu Bán hàng): điều kiện như màn hình tham số hiện trạng, chọn nhiều hàng / khách tìm ở server (tối đa 200 mỗi loại), bấm số chứng từ mở đơn bán / trả hàng (`?id=`), In, tải Excel / PDF (A4 ngang) | BR-UI, BR-RPT-11, 13 | Hoàn thành, test giao diện một phần | `ledgerContent.test.ts` (dòng sổ, kỳ báo cáo, liên kết, Excel, PDF); lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện. Tải Word chưa có (không yêu cầu) |

## Sổ chi tiết mua hàng (2026-10-14, V25)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Sổ chi tiết mua hàng dạng bảng theo hiện trạng (HienTrang 14-15): dòng đơn mua (số lượng, đơn giá, giá trị mua, chiết khấu), trả hàng, giảm giá, dòng đảo âm theo ngày đảo, dòng tổng "Số dòng = n"; lọc kỳ, đơn vị, nhóm VTHH (gồm nhóm con), nhóm NCC, NV mua hàng, nhiều hàng, nhiều NCC | BR-RPT (L737), BR-RPT-11, 13, H-06, AT-12 (một phần), AT-10 | Hoàn thành | `PurchaseLedgerIntegrationTest` (2 test), `purchaseLedgerContent.test.ts`, `navigation.test.ts` | "NV mua hàng" = người lập chứng từ (đơn mua chưa có trường nhân viên mua hàng, chủ dự án chọn 2026-10-14). Cột "Tiền thuế NK", "Mã thống kê" ẩn vì chưa có dữ liệu. Đơn mua chỉ VND (D-24) |
| Màn hình Sổ chi tiết mua hàng (menu Mua hàng): bấm số chứng từ mở đơn mua / trả hàng (`?id=`), In, tải Excel / PDF (A4 ngang) | BR-UI | Hoàn thành, test giao diện một phần | `purchaseLedgerContent.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện. Dùng chung với sổ bán hàng: `SearchMultiSelect`, `ReportDownloadButton`, `reportPeriod`, `pdfmakeLoader` |

## Giai đoạn 10A - Báo cáo kho (2026-10-14, V26, ADR-18)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Thẻ kho theo hàng / kho / lô, lịch sử serial: tồn đầu, phát sinh có tồn lũy kế, tồn cuối khớp số dư | BR-RPT-21, AT-12 | Hoàn thành | `InventoryReportIntegrationTest.ledgerAndTraceability` | Đơn vị cơ sở; tối đa 20.000 dòng mỗi lần xem |
| Nhập - xuất - tồn theo kỳ nhóm theo kho (dòng tổng kho), Số lượng + Giá trị tạm tính (đơn giá nhập bình quân); đảo trừ đúng chiều chứng từ gốc; số âm đỏ trong ngoặc | BR-RPT-22, AT-09, D-57 | Một phần (giá trị tạm tính) | `ledgerAndTraceability`; `inventoryReports.test.ts` | Giá trị chưa phải giá vốn: thay ở Giai đoạn 8 (D-02). Tổng số lượng dòng kho cộng các hàng khác đơn vị tính (theo mẫu D-57). Kiểm thử thủ công trên trình duyệt: chưa thực hiện |
| Hàng tồn lâu (tham số `inventory.slow-moving-days`), hàng ngoại lệ (hết hạn, chất lượng khác ĐẠT, lô / serial khóa), hàng thiếu hồ sơ bắt buộc | BR-RPT-23 | Hoàn thành | `exceptionsAndMissingDocuments` | Sắp hết hạn, dưới định mức: đã có ở cảnh báo kho (BR-INV-40). Ngưỡng 180 ngày `[Giả định]`. "Thiếu hồ sơ" chỉ xét hồ sơ áp dụng cho hàng hóa (không xét lô / serial) `[Suy luận]` |
| Truy xuất lô / serial → nơi nhập, khách nhận (xuất − trả lại), tồn còn lại; khách → lô / serial | BR-RPT-24, BR-INV-42 | Hoàn thành | `ledgerAndTraceability` (gồm người xem chi nhánh khác không thấy dòng) | Dòng đảo lấy đối tượng của dòng gốc |
| Phân quyền, phạm vi kho, điều kiện sai | AT-10, BR-RPT-12 | Hoàn thành | `accessAndValidation` | |
| Màn hình nhóm Báo cáo: Thẻ kho, Nhập - xuất - tồn, Tồn lâu / ngoại lệ / thiếu hồ sơ, Truy xuất lô / serial; bấm số chứng từ mở phiếu kho; In, Excel, PDF | BR-UI, BR-RPT-11, 13 | Hoàn thành, test giao diện một phần | `inventoryReports.test.ts`, `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện. Bảng báo cáo dùng chung `shared/report` cho các đợt 10B..10D |

## Giai đoạn 10B - Báo cáo mua / bán (2026-10-14, V27, ADR-18)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Tình hình thực hiện đơn mua / đơn bán: trạng thái, hẹn nhận / giao, trễ hẹn, trả lại / giảm giá, đã thanh toán / thu, còn nợ | BR-RPT-25, 26, AT-08, AT-12 | Hoàn thành | `OrderReportIntegrationTest.salesReports`, `purchaseReports` | Sau D-54 đơn nhập / xuất đủ khi ghi sổ nên "thực hiện" là hẹn nhận / giao, giao hàng, trả lại, thanh toán `[Suy luận]`. Đã thanh toán = dòng thu / chi / bù trừ trên sổ công nợ (không gồm trả lại, giảm giá) |
| Lịch sử giá mua (giá sau chiết khấu theo đơn vị cơ sở, thấp / cao / gần nhất) | BR-RPT-25 | Hoàn thành | `purchaseReports` | Chỉ đơn đang ghi sổ (không tính đơn đã đảo) |
| Hàng lỗi theo NCC, truy NCC qua lô | BR-RPT-25 | Hoàn thành | `purchaseReports` | NCC = lần nhập mua đầu tiên của lô; phiếu không ghi lô / lô không từ đơn mua: "Không xác định NCC" (chủ dự án chọn 2026-10-14) |
| Sổ nhật ký bán hàng từ chứng từ bán (phải thu, doanh thu hàng hóa / dịch vụ, giảm trừ, thuế) | BR-RPT-26 | Hoàn thành | `salesReports` | Chủ dự án chọn nguồn chứng từ (không theo bút toán); mẫu sổ TT133 `[Chưa kiểm chứng]`, kế toán trưởng xác nhận |
| Tồn so với đặt hàng chưa giao (đặt, tồn, giữ chỗ, khả dụng, đang về, thiếu) | BR-RPT-26, H-06 | Hoàn thành | `salesReports` | Thiếu = đặt − tồn thực tế − đang về `[Suy luận]` (giữ chỗ của chính các đơn đã nằm trong tồn) |
| Phân quyền, phạm vi chi nhánh, điều kiện sai | AT-10, BR-RPT-12 | Hoàn thành | `OrderReportIntegrationTest.accessAndValidation` | |
| Màn hình: Tình hình thực hiện đơn mua / bán, Giá mua và hàng lỗi theo NCC, Sổ nhật ký bán hàng, Tồn so với đặt hàng; mở đơn / phiếu hàng lỗi (`?id=`); In, Excel, PDF | BR-UI, BR-RPT-11, 13 | Hoàn thành, test giao diện một phần | `orderReports.test.ts`, `navigation.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giai đoạn 10C - Dashboard điều hành (2026-10-14, V28, ADR-18)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Doanh thu thuần trong kỳ (bán − trả lại / giảm giá, chưa thuế), giá trị mua thuần | BR-RPT-01 | Hoàn thành một phần | `DashboardIntegrationTest.tradeMetrics` | Nguồn chứng từ đã ghi sổ (chủ dự án chọn 2026-10-14), cùng cách tính sổ nhật ký bán hàng; đơn mua quy đổi VND theo tỷ giá đơn. Giá vốn, lợi nhuận: chờ Giai đoạn 8 (`costAvailable = false`) |
| Công nợ phải thu / phải trả còn lại, quá hạn theo mốc tuổi nợ | BR-RPT-02 | Hoàn thành | `statusMetrics` | Mốc theo tham số `finance.aging-bucket-1..3-days` (dùng chung báo cáo tuổi nợ). Chưa trừ tiền trả trước chưa phân bổ `[Suy luận]` |
| Hàng đã / sắp hết hạn (lô × kho còn tồn) | BR-RPT-04 | Hoàn thành | `statusMetrics` | Ngưỡng `inventory.expiry-warning-days`; gồm mọi trạng thái chất lượng |
| Đơn mua chưa nhận, đơn bán chưa giao đủ, trễ hẹn | BR-RPT-05, 06 | Hoàn thành | `statusMetrics` | Sau D-54: đơn mua gửi / duyệt chưa ghi sổ; đơn bán chưa ghi sổ hoặc đã ghi sổ khách chưa nhận `[Suy luận]` |
| Top hàng, khách (doanh thu thuần), NCC (giá trị mua thuần) | BR-RPT-10 | Hoàn thành | `tradeMetrics` | Top 5 / 10 / 20 trên màn hình (API 1..50) |
| Bấm chỉ tiêu / dòng top xem chứng từ nguồn; tổng chi tiết = số trên dashboard | BR-RPT-11, AT-12 | Hoàn thành | `tradeMetrics`, `statusMetrics` | Chi tiết có In / Excel / PDF |
| Phạm vi dữ liệu, quyền, điều kiện sai; thời điểm số liệu | BR-RPT-12, 13, AT-10 | Hoàn thành | `accessAndValidation` | |
| Màn hình: dashboard trên trang chủ (người có `dashboard:view`), kỳ mặc định Tháng này, chọn đơn vị và số dòng top | BR-UI-02, BR-RPT-11, 13 | Hoàn thành, test giao diện một phần | `dashboard.test.ts`; lint, TypeScript, build | Kiểm thử thủ công trên trình duyệt: chưa thực hiện. BR-RPT-03 (giá trị tồn), 07, 08 (đấu thầu) chờ Giai đoạn 8, 7; dòng tiền dự kiến (BR-RPT-09) ở đợt 10D |

## Giai đoạn 10D - Dòng tiền dự kiến (2026-10-14, V29, ADR-18)
| Hạng mục | Mã yêu cầu | Trạng thái | Test chứng minh | Ghi chú / hạn chế |
|---|---|---|---|---|
| Số dư quỹ / ngân hàng hôm nay; dự thu (phải thu còn lại theo hạn, đơn bán đã duyệt chưa ghi sổ), dự chi (phải trả còn lại theo hạn, đơn mua đã duyệt chưa ghi sổ) theo kỳ ngày / tuần / tháng; cột Quá hạn, cột Sau kỳ cuối; số dư cuối lũy kế | BR-RPT-30 (phần dự kiến), BR-CSH-06 | Hoàn thành một phần | `CashForecastIntegrationTest.forecastMatchesItems`, `ForecastPeriodsTest` | Đơn bán đã duyệt chưa ghi sổ: chủ dự án chọn thêm (ngoài BR). Lịch thu hợp đồng thầu chờ Giai đoạn 7. Ngày dự kiến của đơn chưa ghi sổ: ngày dự kiến giao / nhận + số ngày được nợ `[Suy luận]`. Chưa tính trả trước chưa phân bổ, phiếu chi đã duyệt chưa ghi sổ. Số dư quỹ ngoại tệ cộng thẳng như VND `[Suy luận]` |
| Bấm ô xem khoản cấu thành; tổng = số trong ô | BR-RPT-11, AT-12 | Hoàn thành | `forecastMatchesItems` (mọi ô × nguồn) | Mở đơn bán / mua; công nợ đầu kỳ chỉ hiện số |
| Thẻ "Số dư dự kiến sau 4 tuần" trên dashboard | BR-RPT-09 | Hoàn thành | `cashForecast.test.ts`; lint, TypeScript, build | Hiện khi có `dashboard:view` và `cash-forecast:view`; bấm mở báo cáo |
| Phân quyền, phạm vi chi nhánh, điều kiện sai | AT-10, BR-RPT-12 | Hoàn thành | `CashForecastIntegrationTest.accessAndValidation` | "Chỉ dữ liệu của mình": không gồm số dư quỹ |
| Màn hình Báo cáo → Dòng tiền dự kiến; In, Excel, PDF | BR-UI, BR-RPT-13 | Hoàn thành, test giao diện một phần | `cashForecast.test.ts`, `navigation.test.ts` | Kiểm thử thủ công trên trình duyệt: chưa thực hiện |

## Giả định đã dùng
- Tạm ẩn chi nhánh (người dùng yêu cầu 2026-10-17): mọi màn hình (kể cả quản trị, menu Chi nhánh, ô chọn chi nhánh làm việc) không hiển thị chi nhánh; chứng từ ngầm định chi nhánh mặc định của người dùng (không có thì chi nhánh đầu tiên trong phạm vi); bộ lọc báo cáo để trống = toàn công ty; khóa / mở kỳ áp dụng mọi chi nhánh của công ty. Chỉ đổi frontend (cờ `BRANCH_VISIBLE` trong `frontend/src/shared/branch.ts`), backend và dữ liệu giữ nguyên; kho chọn được trên chứng từ chỉ là kho thuộc chi nhánh mặc định.
- Giai đoạn 3 (chốt 2026-10-10): ghi sổ kho ở READ COMMITTED với thứ tự khóa cố định (ADR-08); API chung `/inventory-documents` (ADR-10); chế độ duyệt mặc định của loại chứng từ kho và ngưỡng cảnh báo hạn dùng 90 ngày (D-52); mỗi chính sách xuất vượt tồn một chứng từ (D-53). Luồng duyệt mẫu chỉ là dữ liệu dev/test.
- Giai đoạn 4 (duyệt 2026-10-10): công nợ gắn chứng từ nguồn tổng quát (ADR-12); bút toán phiếu kho dời Giai đoạn 8; hệ thống tài khoản TT133 và quy tắc mẫu chỉ ở dữ liệu dev/test; ngưỡng duyệt phiếu chi mặc định 20.000.000 đ `[Giả định]`; mốc tuổi nợ 30/60/90 ngày `[Giả định]`; dòng tiền dự kiến dời Giai đoạn 10.
- Giai đoạn 4B: một bảng `debt_items` cho cả phải thu và phải trả (cùng cấu trúc) thay hai bảng dự kiến; công nợ đầu kỳ không sinh bút toán; đảo công nợ đầu kỳ do người có quyền duyệt thực hiện trực tiếp (hành động là phê duyệt); đối tượng phải thu: khách hàng / bên mời thầu / đại lý / nhân viên, phải trả: nhà cung cấp / nhân viên.
- Giai đoạn 4C (người dùng giao tự duyệt theo khuyến cáo, 2026-10-10): một bảng chứng từ thu chi (ADR-13); quy tắc hạch toán được để trống cả hai vế khi chứng từ quyết định cả hai tài khoản (sửa ADR-11, bỏ `CHECK` ở V11); ngưỡng duyệt chi áp dụng cho PC, UNC, CTN (chuyển tiền coi như chi), chứng từ thu không cần duyệt; tiền chỉ phân bổ vào hóa đơn cùng chi nhánh với chứng từ; quỹ chính cùng chi nhánh với chứng từ, quỹ nhận của chuyển tiền có thể khác chi nhánh; tài khoản ngân hàng không chặn âm; đảo chứng từ thu chi là quyền riêng `cash-document:reverse` (mặc định Kế toán trưởng); trả trước ghi sổ chi tiết công nợ loại `PREPAYMENT` không gắn hóa đơn. Ba điểm của 4B (một bảng `debt_items`, công nợ đầu kỳ không sinh bút toán, đảo công nợ đầu kỳ do người duyệt) được giữ nguyên theo khuyến cáo.
- Giai đoạn 4D (người dùng nói "tiếp", làm theo khuyến cáo, 2026-10-10): tạm ứng là loại công nợ `ADVANCE` (ADR-14); hạn hoàn ứng mặc định 30 ngày; DTCN, TTTU duyệt khi cấu hình luồng (mặc định không cần), DCCN, KKQ bắt buộc duyệt; bù trừ trả trước không sinh bút toán (cùng tài khoản công nợ); bù trừ phải thu - phải trả cần đối tượng có cả vai trò khách hàng và nhà cung cấp, cùng chi nhánh; biên bản toàn công ty chỉ người có phạm vi toàn công ty; kiểm kê chỉ quỹ tiền mặt, số sổ tính đến hết ngày kiểm kê lúc ghi sổ; tài khoản mẫu 711 / 811 cho chênh lệch kiểm kê, 141 cho tạm ứng `[Chưa kiểm chứng]`.
- Giai đoạn 5 (người dùng "duyệt, tự chốt", 2026-10-10): phiếu nhập kho mua hàng thuộc module kho; hàng đang về ghi qua API kho (ADR-15); hóa đơn mua sinh công nợ phải trả và bút toán theo quy tắc (dev tạm Nợ 156, 1331 / Có 331, Giai đoạn 8 đổi sang tài khoản trung gian `[Suy luận]`); chênh lệch giá / chi phí mua lưu theo dòng phiếu nhập, tính lại giá vốn ở Giai đoạn 8; tham số `purchasing.order-approval-threshold` 50.000.000 đ, dung sai nhận vượt và dung sai giá 0% `[Giả định]`; chỉ VND. 5A: DNM duyệt khi có luồng (BR-PUR-01 "có duyệt"; mẫu dev có luồng); kho nhận phải là kho vật lý cùng chi nhánh với đơn; đóng đơn mua không trả lại số đã đặt cho đề nghị; dịch vụ không cộng hàng đang về.
- Giai đoạn 5B: phiếu nhập kho mua hàng ghi sổ ngay khi nhập kho theo phiếu nhận hàng, không qua luồng duyệt phiếu kho (phiếu nhận hàng đã duyệt / không vượt dung sai); đơn vị tính dòng nhận theo dòng đơn mua; đơn giá nhập tạm tính = tiền sau chiết khấu / số lượng của dòng đơn mua (trước thuế); hàng trả lại vẫn tính còn thiếu cho đến khi đóng đơn; đơn mua đã hoàn thành / đóng thì không lập phiếu nhận mới; quyền nhập kho theo phiếu là `purchase-receipt:post` (không cần quyền phiếu nhập kho).
- Giai đoạn 5C: hóa đơn mua duyệt khi cấu hình luồng (mặc định không cần); một khoản công nợ cho cả hóa đơn (tổng gồm thuế); hàng hóa chỉ vào hóa đơn qua dòng phiếu nhận đã nhập kho (chưa hỗ trợ hóa đơn về trước hàng); hàng cách ly vẫn được nhận hóa đơn (trả hàng xử lý ở 5D); dòng khuyến mại giá trị 0; hạn thanh toán mặc định theo đơn mua liên quan đầu tiên rồi đến nhà cung cấp; bút toán hóa đơn ghi Nợ 1561 vì phiếu nhập kho chưa có bút toán đến Giai đoạn 8.
- Giai đoạn 5D: trả hàng và giảm giá dùng chung bảng `purchase_returns` (loại THN / GGM); một phiếu trả hàng một kho và hoặc toàn bộ theo một hóa đơn, hoặc toàn bộ hàng chưa có hóa đơn; đơn giá trả theo hóa đơn (hoặc giá tạm tính khi chưa có hóa đơn), thuế theo dòng hóa đơn; giảm công nợ trừ vào hóa đơn gốc, phần vượt (hóa đơn đã trả) ghi trả trước của nhà cung cấp; trả hàng không sinh điều chỉnh giá trị nhập (Giai đoạn 8 đọc số đã trả); phân bổ chi phí chỉ từ dòng dịch vụ của hóa đơn mua đã ghi sổ cùng chi nhánh, vào mọi dòng đã nhập kho của các phiếu nhận được chọn, cơ sở giá trị là giá tạm tính; bút toán phân bổ chuyển từ tài khoản chi phí đã ghi khi ghi sổ hóa đơn dịch vụ (dev 6422) sang 1562 `[Chưa kiểm chứng]`.
- Giai đoạn 6 (người dùng giao làm hết theo khuyến cáo, 2026-10-10; ADR-16): báo giá / đơn bán chỉ phải duyệt khi có lý do (giá dưới sàn, chiết khấu dòng > `sales.discount-approval-percent` = 10%, công nợ hiện tại + giá trị đơn > hạn mức, có nợ quá hạn), không lý do thì tự duyệt; hạn mức tín dụng tính trên công nợ toàn công ty, chưa tính đơn đã duyệt chưa xuất hóa đơn; đại lý dùng hạn mức đại lý nếu có; hiệu lực báo giá mặc định 30 ngày, giữ hàng 7 ngày (D-37), giữ hàng khi duyệt chỉ khi chọn trên đơn (hợp đồng thầu bắt buộc ở Giai đoạn 7); hàng khách trả lại mở lại phần cần giao của đơn (giao bù / đổi hàng hoặc đóng phần còn lại); đơn hoàn thành khi đã giao đủ (dịch vụ: đã xuất hóa đơn đủ); phiếu giao gộp xuất kho và theo dõi giao hàng, giao thất bại = đảo phiếu xuất; kho nhập lại hàng trả = kho của phiếu giao, mặc định CÁCH LY; hóa đơn bán chỉ từ hàng đã xuất kho (cảnh báo nếu khách chưa nhận); giá, chiết khấu, thuế trên hóa đơn / trả hàng lấy theo đơn bán; bán nhanh cần quyền của từng bước và không lập phiếu thu; tài khoản mẫu 131 / 5111 / 5113 / 3331 `[Chưa kiểm chứng]`.
- Giai đoạn 4A: quy tắc hạch toán khi cả quy tắc và chứng từ cùng có tài khoản cho một vế thì dùng tài khoản của quy tắc; tài khoản chứng từ truyền chỉ dùng cho vế quy tắc để trống.
- Giai đoạn 3: phiếu điều chỉnh sinh từ kiểm kê đã duyệt không qua luồng duyệt riêng (phiếu kiểm kê đã được duyệt).
- Không có profile mặc định; thiếu `SPRING_PROFILES_ACTIVE` thì chạy với cấu hình gốc an toàn cho production.
- Dữ liệu mẫu dev dùng migration repeatable `R__dev_*.sql`; mật khẩu mẫu `Erp@2026` chỉ cho dev/test.
- DTO request/response đặt ở `application/dto` (cập nhật `.claude/rules/architecture.md`).
- Mã lỗi mới ngoài docs/04 bản 0.2: `MALFORMED_REQUEST`, `METHOD_NOT_ALLOWED`, `REQUEST_IN_PROGRESS`, `DATA_INTEGRITY_VIOLATION` (đã cập nhật docs/04).
- D-54 (chủ dự án duyệt 2026-10-12): hóa đơn NCC trên đơn mua là tùy chọn; hạn thanh toán = ngày hóa đơn (không có: ngày hạch toán) + số ngày nợ; đơn giá nhập = tiền sau chiết khấu / số lượng (trước thuế); số HĐĐT ghi sau ghi sổ không đổi nhãn khoản công nợ (giữ số đơn); hàng thực xuất của đơn bán giữ lại sau đảo để truy vết; vai trò mặc định của quyền ghi sổ / giao / đảo đơn `[Giả định]`; quy tắc hạch toán DMH / DBH chỉ là dữ liệu dev `[Chưa kiểm chứng]`.
