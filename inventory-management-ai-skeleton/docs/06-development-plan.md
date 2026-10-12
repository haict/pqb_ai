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
Kế hoạch duyệt 2026-10-10: làm lần lượt 4A (kế toán lõi) → 4B (sổ công nợ, công nợ đầu kỳ) → 4C (quỹ, ngân hàng, phân bổ) → 4D (đối trừ, điều chỉnh, đối chiếu, tạm ứng, kiểm kê quỹ). Bút toán phiếu kho dời Giai đoạn 8 (cùng giá vốn); dòng tiền dự kiến dời Giai đoạn 10.
- [x] 4A: Hệ thống tài khoản (cây, nhập Excel), quy tắc hạch toán (`posting_rules`, ADR-11), `JournalPostingService` (ghi, đảo, kiểm tra kỳ kế toán), màn hình Hệ thống tài khoản / Quy tắc hạch toán / Bút toán; quy tắc theo D-34, D-35 nhập vào `posting_rules` khi có chứng từ tương ứng
- [x] 4B: Sổ công nợ (`debt_items` phải thu / phải trả theo chứng từ nguồn, `debt_ledger_entries`), công nợ đầu kỳ theo hóa đơn (duyệt, ghi sổ, đảo), `DebtPostingService` cho Giai đoạn 5, 6, tuổi nợ theo mốc tham số, sổ chi tiết công nợ; màn hình Công nợ phải thu / phải trả / đầu kỳ
- [x] 4C: Quỹ, tài khoản ngân hàng (`cash_accounts`), chứng từ thu chi một bảng (PT, PC, BC, UNC, CTN, ADR-13), phân bổ thanh toán (tự động hóa đơn đến hạn sớm nhất trước / chỉ định), trả trước, duyệt chi theo ngưỡng, quỹ tiền mặt không âm, sổ quỹ, dòng tiền thực tế, bút toán theo quy tắc hạch toán; màn hình Thu chi / Sổ quỹ, dòng tiền / Quỹ; test AT-08 (phần thu một phần, nhiều hóa đơn)
- [x] 4D: Bù trừ (trả trước với hóa đơn, phải thu với phải trả), điều chỉnh công nợ có duyệt, bảng / biên bản đối chiếu (phần còn lại của AT-08), tạm ứng / thanh toán tạm ứng / hoàn ứng (ADR-14), kiểm kê quỹ; màn hình tương ứng
- [ ] Thu hàng loạt nhiều khách (BR-AR-08), nhập sao kê (BR-CSH-07, P2)

## Giai đoạn 5 - Mua hàng (M03)
Kế hoạch duyệt 2026-10-10 ("duyệt, tự chốt"): 5A đề nghị mua, đơn mua, hàng đang về → 5B nhận hàng, nhập kho → 5C hóa đơn mua, công nợ phải trả → 5D chi phí mua, trả hàng NCC, giảm giá → 5E yêu cầu báo giá, so sánh, lịch sử NCC. Đề nghị mua từ đơn bán / giữ chỗ khi hàng về / mua giao thẳng dời Giai đoạn 6; từ hợp đồng dời Giai đoạn 7; từ định mức dời Giai đoạn 10.
- [x] 5A: Đề nghị mua (lập tay, duyệt khi có luồng, đóng phần còn lại), đơn mua (duyệt theo ngưỡng, theo đề nghị, tiến độ từng dòng, đóng phần còn lại), hàng đang về (ADR-15); màn hình Đề nghị mua hàng / Đơn mua hàng
- [x] 5B: Phiếu nhận hàng theo đơn mua (kiểm nhận đạt / cách ly / trả lại, lô / hạn / serial, dung sai nhận vượt có duyệt), nhập kho thành PNK riêng có nguồn (`StockInService`), nhận và nhập trong một thao tác (D-36), đảo; màn hình Phiếu nhận hàng; AT-01, AT-02
- [x] 5C: Hóa đơn mua n-n với phiếu nhận hàng, dịch vụ, hàng khuyến mại, chống trùng, đối chiếu ba chiều (cảnh báo), công nợ phải trả + bút toán theo quy tắc, chênh lệch giá theo dòng phiếu nhận (`purchase_cost_entries`), đảo; màn hình Hóa đơn mua
- [x] 5D: Trả hàng NCC (đúng lô / serial theo phiếu nhận, hàng chưa / đã có hóa đơn; giảm tồn, công nợ phải trả, bút toán), giảm giá hàng mua (giảm công nợ và giá trị nhập), phân bổ chi phí mua theo giá trị / số lượng (`LANDED_COST`), đảo và chặn đảo khi có chứng từ phụ thuộc; `StockOutService` và giữ chỗ dùng được cho Giai đoạn 6; màn hình Trả hàng / giảm giá, Phân bổ chi phí mua
- [ ] Yêu cầu báo giá, so sánh báo giá, lịch sử / đánh giá NCC (5E, chưa làm)
- [x] Test: AT-01, AT-02

## Giai đoạn 6 - Bán hàng (M05)
Người dùng giao làm hết Giai đoạn 6 theo khuyến cáo (2026-10-10): 6A giá, báo giá → 6B đơn bán → 6C giao hàng → 6D hóa đơn bán → 6E trả hàng, đổi hàng, hàng lỗi → 6F bán nhanh, lịch thu tiền, báo cáo doanh số (ADR-16).
- [x] 6A: Lấy giá theo thứ tự (bảng giá riêng của khách → nhóm / đại lý → niêm yết), giá sàn, chiết khấu đại lý mặc định; báo giá nhiều phiên bản, duyệt giá dưới sàn / chiết khấu vượt ngưỡng, gửi khách, chấp nhận / từ chối, hết hạn, in báo giá
- [x] 6B: Đơn bán, chuyển từ báo giá, duyệt theo hạn mức tín dụng / nợ quá hạn, cảnh báo tồn khả dụng, giữ / nhả hàng có hạn, đóng phần còn lại, đề nghị mua phần thiếu
- [x] 6C: Phiếu giao hàng: xuất kho theo đơn (FEFO, lô chỉ định có lý do, dùng hàng đã giữ, hạn dùng tối thiểu theo khách D-07), giao nhiều đợt, đang giao / khách nhận / giao thất bại, biên bản giao nhận (in)
- [x] 6D: Hóa đơn bán từ nhiều phiếu giao + dịch vụ, số HĐĐT MISA chống trùng, công nợ phải thu, bút toán doanh thu, đảo
- [x] 6E: Trả hàng bán đúng lô / serial vào cách ly, giảm doanh thu / công nợ; giảm giá hàng bán; đổi hàng; tiếp nhận hàng lỗi
- [x] 6F: Bán nhanh (đơn → giao → hóa đơn), lịch thu tiền theo đợt, báo cáo doanh số theo nhân viên / khách / hàng / chi nhánh có truy ngược chứng từ
- [ ] Khuyến mại theo chính sách (mua X tặng Y), chiết khấu đơn / thanh toán / theo doanh số kỳ (BR-SAL-35 phần chính sách, P2 cho doanh số kỳ); hàng tặng hiện nhập tay từng dòng
- [ ] Giá vốn, lợi nhuận gộp (Giai đoạn 8); phiếu thu trong bán nhanh; đề nghị lắp đặt (BR-SAL-24, Giai đoạn 9)
- [x] Test: AT-03, AT-05, AT-06 (cùng AT-09, AT-10, AT-12 phần bán hàng, AT-13)

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
