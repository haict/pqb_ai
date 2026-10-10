# Kế hoạch phát triển

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 |
| Ngày cập nhật | 2026-10-09 |
| Phạm vi | Giai đoạn P1 (MVP) chi tiết; P2, P3 tóm tắt |
| Căn cứ | `docs/01-business-requirements.md` v0.6 mục 3.2, 18, 19 |

## Nguyên tắc
- Làm theo từng module / từng use case; mỗi bước nhỏ, trọn vẹn, có test.
- Thiết kế (ERD, API, quyền) của module được duyệt trước khi code.
- Không đánh dấu hoàn thành chỉ vì code đã có. Một hạng mục hoàn thành khi:
  - Test đạt; kịch bản AT liên quan đã phủ.
  - Quy tắc nghiệp vụ đã kiểm chứng.
  - Migration chạy trên DB trống.
  - Hợp đồng API và tài liệu đã cập nhật.
  - Đã review mã.
- Trạng thái từng tính năng ghi tại `docs/12-feature-status.md`.

## Giai đoạn 0 - Nền tảng
- [x] Yêu cầu nghiệp vụ (01), kiến trúc (02), ERD sơ bộ (03), API sơ bộ (04), luồng (05)
- [x] `CLAUDE.md`, `.claude/rules/*`
- [x] Monorepo: `backend/` (Maven, WAR, Java 17, Spring Boot 3.5.16), `frontend/` (Vite, React, TS, Ant Design)
- [x] Bỏ Docker (D-44): dev/test dùng MySQL local, backend chạy Eclipse + Tomcat 10.1
- [x] Cấu hình profile `dev/test/uat/prod`, mẫu `backend/config/application-local.example.yml`, Logback, `requestId`
- [x] `common`: phản hồi API, xử lý lỗi tập trung, phân trang, idempotency, đánh số chứng từ, audit, `Clock`
- [x] Flyway V1; `db/dev-data` tách riêng (đã chạy trên DB trống)
- [x] springdoc-openapi
- [x] Test tích hợp trên MySQL local (`~/.erp/application-local.yml`) hoặc `ERP_TEST_DB_URL`; ArchUnit kiểm tra ranh giới module
- [~] CI chạy `mvn clean verify` và `npm run lint && npm test && npm run build` (đã viết, chưa chạy trên remote)
- [x] README cài đặt, chạy; `docs/08-environment.md`

`[~]` = đã code, chưa kiểm chứng đầy đủ. Chi tiết: `docs/12-feature-status.md`.

## Giai đoạn 1 - Quản trị hệ thống (M01)
- [x] 1A: Công ty, chi nhánh, phòng ban (kho thuộc M02, Giai đoạn 2)
- [x] 1A: Người dùng, vai trò, quyền; đăng nhập JWT + refresh token xoay vòng; khóa tài khoản; nhật ký đăng nhập
- [x] 1A: Phạm vi dữ liệu công ty/chi nhánh; chặn truy cập chéo (AT-10 phần backend). Phạm vi kho: Giai đoạn 2
- [x] 1D: Ma trận duyệt, `approval_requests`, ủy quyền duyệt; tách biệt nhiệm vụ (D-52)
- [x] 1C: Kỳ kế toán, kỳ kho; khóa/mở kỳ theo chi nhánh × phân hệ (AT-11 phần khung; D-45..D-47)
- [x] 1C: Tham số hệ thống (D-05); cấu hình số chứng từ (D-48); 3 màn hình quản trị
- [x] 1B: Frontend: đăng nhập, tự làm mới phiên, đổi mật khẩu, layout (sidebar theo quyền, header người dùng, chi nhánh làm việc), quản trị công ty / chi nhánh / phòng ban / người dùng / vai trò / nhật ký đăng nhập
- [x] 1D: Thông báo trong ứng dụng (BR-SYS-25, D-49)
- [x] `docs/07-authorization.md`
- [x] 1D: Giới hạn tần suất đăng nhập theo IP (docs/07 mục 1.2)

## Giai đoạn 2 - Danh mục (M02)
- [x] Nhóm hàng, hàng hóa (loại hàng, cờ lô/hạn/serial, pháp lý, bảo quản, định mức)
- [x] Đơn vị tính, quy đổi
- [x] Nhãn hiệu, nhà sản xuất, xuất xứ
- [x] Đối tác (khách, NCC, đại lý, bên mời thầu), liên hệ, địa chỉ, điều khoản
- [x] Kho, vị trí, kho ảo
- [x] Thuế suất, bảng giá, giá sàn
- [x] Loại hồ sơ, hồ sơ/giấy phép có hạn
- [x] Danh mục phụ (điều khoản, phương thức, lý do, khoản mục) - một danh mục `reference_items` theo `category`
- [x] Nhập danh mục từ Excel (BR-UX-05, BR-MIG-01; hàng hóa, đối tác; D-50)
- [ ] Tiền tệ, tỷ giá: dời tới module dùng ngoại tệ

## Giai đoạn 3 - Lõi kho (M04)
- [x] Lô, serial
- [x] `inventory_transactions`, `inventory_balances`, `inventory_stock_summaries`, `inventory_reservations` (giữ chỗ: dịch vụ nội bộ, chưa có màn hình)
- [x] `InventoryPostingService`: khóa theo thứ tự, kiểm tra tồn khả dụng, FEFO (FIFO cấu hình được: chưa làm)
- [x] Phiếu nhập, xuất, chuyển kho (cùng / khác chi nhánh); xuất nội bộ, hủy
- [x] Đảo chứng từ kho
- [x] Chuyển trạng thái chất lượng, khóa lô, thu hồi
- [x] Kiểm kê, điều chỉnh
- [x] Chính sách xuất vượt tồn khả dụng (D-53: một chính sách một chứng từ)
- [x] Test: AT-02, AT-03 (FEFO phần kho), AT-04, AT-09 (phần kho), AT-10, AT-11, AT-13; test tính nhất quán balance = ledger

## Giai đoạn 4 - Kế toán lõi và công nợ (M08 phần lõi, M07)
- [ ] Hệ thống tài khoản, quy tắc hạch toán, `JournalPostingService`; quy tắc theo D-34, D-35 nằm trong `posting_rules` (cấu hình)
- [ ] Sổ công nợ, `receivables`, `payables`, phân bổ thanh toán
- [ ] Quỹ, ngân hàng, phiếu thu, phiếu chi, chuyển tiền, tạm ứng
- [ ] Đối trừ, điều chỉnh công nợ, biên bản đối chiếu
- [ ] Test: AT-08

## Giai đoạn 5 - Mua hàng (M03)
- [ ] Đề nghị mua, yêu cầu báo giá, so sánh báo giá
- [ ] Đơn mua, theo dõi tiến độ
- [ ] Phiếu nhận hàng, nhập kho (kể cả thao tác nhanh)
- [ ] Hóa đơn mua, hóa đơn về sau, đối chiếu ba chiều
- [ ] Chi phí mua hàng và phân bổ
- [ ] Trả hàng NCC
- [ ] Test: AT-01, AT-02

## Giai đoạn 6 - Bán hàng (M05)
- [ ] Bảng giá, chính sách chiết khấu, khuyến mại
- [ ] Báo giá, duyệt giá
- [ ] Đơn bán, hạn mức tín dụng, giữ chỗ
- [ ] Xuất kho theo đơn, giao hàng nhiều đợt, biên bản giao nhận
- [ ] Hóa đơn bán (tham chiếu hóa đơn điện tử MISA)
- [ ] Trả hàng, đổi hàng, hàng lỗi
- [ ] Lịch thu tiền; thao tác bán nhanh
- [ ] Test: AT-03, AT-05, AT-06

## Giai đoạn 7 - Đấu thầu (M06)
- [ ] Gói thầu, hồ sơ dự thầu, hàng dự thầu, giá dự thầu
- [ ] Kết quả trúng thầu, hợp đồng, phụ lục
- [ ] Phân bổ hàng, kế hoạch giao
- [ ] Liên kết đơn bán/xuất/giao với hợp đồng; chặn vượt số còn lại
- [ ] Nghiệm thu, hồ sơ đợt, đối soát
- [ ] Cảnh báo hết hạn
- [ ] Test: AT-07

## Giai đoạn 8 - Giá vốn và khóa kỳ (M08)
- [ ] Tính giá vốn theo phương pháp đã chốt (D-02): bình quân gia quyền cuối kỳ, đích danh cho serial
- [ ] Điều chỉnh giá vốn khi hóa đơn về sau / phân bổ chi phí
- [ ] Đối chiếu kho - kế toán; khóa kỳ đầy đủ
- [ ] Xuất dữ liệu sang MISA
- [ ] Test: AT-11, AT-09 (đầy đủ)

## Giai đoạn 9 - Thiết bị, bảo hành (M09 phần P1)
- [ ] Hồ sơ thiết bị theo serial, bàn giao, bảo hành, đổi thiết bị
- [ ] Hồ sơ kỹ thuật, cảnh báo giấy tờ hết hạn
- [ ] Truy xuất theo khách, đơn, hợp đồng

## Giai đoạn 10 - Báo cáo và dashboard (M10)
- [ ] Dashboard điều hành BR-RPT-01..10, xem chi tiết đến chứng từ nguồn
- [ ] Báo cáo kho, mua, bán, đấu thầu, công nợ, quỹ, lợi nhuận
- [ ] Xuất Excel/PDF
- [ ] Test: AT-12

## Giai đoạn 11 - Chuyển đổi dữ liệu, nghiệm thu, triển khai
- [ ] Công cụ nhập số dư đầu kỳ (tồn theo lô/serial, công nợ theo hóa đơn, quỹ, hợp đồng)
- [ ] Kiểm thử hiệu năng theo BR-NF-11
- [ ] Kiểm thử bảo mật cơ bản
- [ ] Hướng dẫn triển khai Tomcat 10.1 (`docs/10-deployment.md`), backup/restore (`docs/09-backup-restore.md`) đã diễn tập
- [ ] Kế hoạch kiểm thử và báo cáo nghiệm thu (`docs/11-test-plan.md`)
- [ ] Danh sách tính năng hoàn thành / chưa hoàn thành / giả định (`docs/12-feature-status.md`)
- [ ] Kế toán trưởng / pháp chế ký xác nhận quy tắc kế toán, lưu trữ, hồ sơ pháp lý (D-02, D-14, D-26, D-28, D-34, D-35)
- [ ] UAT, chạy song song với MISA 1–2 tháng

## P2 và P3 (tóm tắt)
- **P2:**
  - Phát hành hóa đơn điện tử.
  - Nhập khẩu, ngoại tệ.
  - Sửa chữa, bảo trì.
  - Tuân thủ y tế nâng cao.
  - Bảng kê thuế.
  - Sổ chi tiết, sổ tổng hợp.
  - Dashboard nâng cao, báo cáo tùy chỉnh.
  - Xác thực hai lớp.
- **P3:**
  - Báo cáo tài chính, tờ khai thuế.
  - Ngân sách, hoa hồng.
  - TSCĐ, CCDC, khế ước vay (nếu thay MISA hoàn toàn).
  - Ngân hàng điện tử.

## Phụ thuộc và mốc xác nhận
Mọi quyết định đã chốt (BR mục 20, v0.7); không còn hạng mục bị chặn khi lập trình. Các mốc xác nhận còn lại:

| Hạng mục | Cần trước | Người xác nhận |
|---|---|---|
| Quy tắc hạch toán, giá vốn, ghi nhận doanh thu/công nợ, hàng khuyến mại (D-02, D-26, D-34, D-35) | UAT (GĐ 11) | Kế toán trưởng |
| Thời gian lưu trữ (D-14) | UAT | Kế toán trưởng / pháp chế |
| Bộ hồ sơ pháp lý y tế mặc định (D-28) | UAT | Pháp chế |
| Danh sách người dùng, chi nhánh thật (D-08, D-23) | Triển khai (GĐ 11) | Doanh nghiệp |
| Máy chủ cụ thể (D-32) | Đầu GĐ 11 | Doanh nghiệp |
| Quy mô thực tế (D-16) | Kiểm thử hiệu năng | Doanh nghiệp |
| Thay MISA hoàn toàn (D-01) | Cuối P2 | Ban giám đốc |
