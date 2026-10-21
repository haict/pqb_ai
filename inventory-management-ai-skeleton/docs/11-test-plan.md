# Kế hoạch kiểm thử và báo cáo nghiệm thu

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.6 (sau D-61 bỏ bước duyệt) |
| Ngày cập nhật | 2026-10-21 |
| Căn cứ | BR mục 18 (AT-01..AT-13), `.claude/rules/testing.md` |

## 1. Chiến lược
| Lớp | Công cụ | Phạm vi |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | Quy tắc thuần (mật khẩu, định dạng số chứng từ, tham số, giới hạn đăng nhập) |
| Tích hợp | Spring Boot Test + MockMvc trên **MySQL 8 thật**, schema `inventory_db_test` (xóa sạch rồi chạy Flyway V1..V12 + `db/dev-data` mỗi lần chạy) | API thật: quyền, phạm vi, idempotency, khóa dữ liệu, ghi sổ, đồng thời |
| Kiến trúc | ArchUnit | Ranh giới module, tầng, phụ thuộc vòng |
| Frontend | Vitest + React Testing Library | Điều hướng theo quyền, form, nút thao tác, client HTTP |

Sau mỗi kịch bản ghi sổ kho (`InventoryIntegrationTest`, `InventoryControlIntegrationTest`, helper chung `InventoryIntegrationSupport`), test kiểm tra tính nhất quán: tồn chi tiết = tổng sổ kho theo từng khóa tồn; tổng (hàng, kho) = tổng tồn chi tiết; không dòng tồn nào âm (`InventoryIntegrationTest.assertLedgerConsistent`).

## 2. Kịch bản nghiệm thu
| Mã | Kịch bản | Trạng thái | Test |
|---|---|---|---|
| AT-01 | Đơn mua, hàng đang về (D-54: mỗi đợt nhận là một đơn mua) | Đạt: từ D-61 đơn nháp không cộng hàng đang về, ghi sổ là nhập kho ngay (đơn đã duyệt trước D-61: trừ khi ghi sổ / hủy); ghi sổ tăng tồn đúng lô; đảo đơn hoàn tồn, hàng đang về không đổi lại | `PurchaseOrderIntegrationTest.postAndReverse`, `thresholdAuthority` |
| AT-02 | Nhận hàng theo lô, hạn dùng, serial | Đạt: đơn mua ghi lô + hạn dùng, serial theo dòng; thiếu lô / hạn dùng, số serial không khớp số lượng bị chặn | `InventoryIntegrationTest.lotsSerialsAndFefo`, `PurchaseOrderIntegrationTest.postAndReverse`, `validation` |
| AT-03 | Đơn bán, xuất kho theo lô FEFO | Đạt: ghi sổ đơn bán xuất theo FEFO, dùng hàng đã giữ; lô dưới hạn dùng tối thiểu của khách bị chặn (D-07); lô chỉ định khác FEFO cần lý do (phần kho) | `InventoryIntegrationTest.lotsSerialsAndFefo`, `SalesOrderIntegrationTest.postDeliverAndFail` |
| AT-04 | Hai người đồng thời xuất không làm tồn sai / âm | Đạt (5 luồng cùng ghi sổ trên một dòng tồn); BR-GEN-52 cho quỹ, hóa đơn: hai luồng cùng phân bổ một hóa đơn, hai luồng cùng chi một quỹ | `InventoryIntegrationTest.concurrentIssues`, `CashIntegrationTest.concurrentAllocation`, `CashIntegrationTest.concurrentPayments`, `DebtSettlementIntegrationTest.concurrentPrepaymentOffsets` |
| AT-05 | Giao hàng (D-54: mỗi đợt giao là một đơn bán) | Đạt: đơn đã ghi sổ chuyển đang giao → khách nhận (biên bản); giao thất bại = đảo đơn, hàng về kho, công nợ / bút toán đảo | `SalesOrderIntegrationTest.postDeliverAndFail` |
| AT-06 | Trả hàng, điều chỉnh tồn có truy vết | Phủ điều chỉnh từ kiểm kê; trả hàng NCC đúng lô theo dòng đơn mua đã ghi sổ, giảm tồn và công nợ; trả hàng bán đúng lô vào cách ly theo dòng đơn bán, giảm doanh thu, công nợ; giá vốn ở Giai đoạn 8 | `InventoryIntegrationTest.stockCount`, `PurchaseReturnIntegrationTest.returnsAndDiscount`, `SalesReturnIntegrationTest.returnDiscountExchange` |
| AT-07 | Hợp đồng thầu, chặn giao vượt | Chưa làm (Giai đoạn 7) | |
| AT-08 | Thu tiền một phần, phân bổ nhiều hóa đơn; biên bản đối chiếu | Đạt: thu một phần nhiều hóa đơn, trả trước, bù trừ trả trước; bảng đối chiếu khớp sổ, biên bản chốt số dư, lệch xử lý bằng điều chỉnh có duyệt | `CashIntegrationTest.receiptAllocation`, `DebtSettlementIntegrationTest.prepaymentOffset`, `DebtSettlementIntegrationTest.reconciliationAndAdjustment` |
| AT-09 | Hủy / đảo chứng từ đã ghi sổ | Phủ phần kho, công nợ, quỹ, mua, bán: đảo đơn mua / đơn bán hoàn tồn, công nợ, bút toán; chặn đảo đơn khi còn trả hàng / giảm giá / phân bổ; chặn đảo trực tiếp phiếu kho có nguồn; đảo trả hàng, phân bổ chi phí | `InventoryIntegrationTest.reversal`, `InventoryControlIntegrationTest.sameBranchTransfer`, `AccountingIntegrationTest.postAndReverse`, `DebtIntegrationTest.reverseOpening`, `CashIntegrationTest.reverseReceipt`, `DebtSettlementIntegrationTest`, `PurchaseOrderIntegrationTest.postAndReverse`, `PurchaseReturnIntegrationTest`, `SalesOrderIntegrationTest.postDeliverAndFail`, `SalesReturnIntegrationTest.returnDiscountExchange` |
| AT-10 | Không có quyền thì không duyệt, không truy cập trái phép (từ D-61: không có quyền vượt ngưỡng thì không ghi sổ được) | Phủ M01, danh mục, kho | `AuthorizationIntegrationTest`, `ApprovalIntegrationTest`, `MasterDataIntegrationTest`, `InventoryIntegrationTest.idempotencyAndAccess`, `crossBranchTransfer`, `InventoryControlIntegrationTest` (kích hoạt thu hồi, khóa lô, cảnh báo ngoài phạm vi), `CashIntegrationTest.paymentApprovalAndCashLimit` (chi từ ngưỡng cần quyền vượt ngưỡng), `CashIntegrationTest.transferFlowPeriodAndAccess` (403, 404 chi nhánh khác), `PurchaseOrderIntegrationTest.thresholdAuthority` (người lập không duyệt được; chi nhánh khác 404), `PurchaseOrderIntegrationTest.validation` |
| AT-11 | Khóa kỳ chặn chứng từ thuộc kỳ khóa | Phủ khung khóa kỳ, ghi sổ kho, điều kiện khóa kỳ kho; chưa phủ sửa / đảo trong kỳ khóa, giá vốn | `PeriodLockIntegrationTest`, `InventoryIntegrationTest.lockedPeriod`, `InventoryControlIntegrationTest.periodCloseCheck`, `AccountingIntegrationTest.lockedAccountingPeriod`, `DebtIntegrationTest.lockedDebtPeriod`, `CashIntegrationTest.transferFlowPeriodAndAccess` (kỳ quỹ) |
| AT-12 | Dashboard, báo cáo khớp nguồn | Phủ báo cáo doanh số: tổng chứng từ cấu thành bằng doanh thu thuần của nhóm; dashboard ở Giai đoạn 10 | `SalesReportIntegrationTest.report` |
| AT-13 | Gửi lại yêu cầu không tạo dữ liệu trùng | Phủ dịch vụ chung, danh mục, kho, bút toán, phiếu thu, đơn mua, đơn bán (gửi lại lệnh ghi sổ cùng key: một phiếu kho, một khoản công nợ) | `IdempotencyServiceIntegrationTest`, `MasterDataIntegrationTest`, `InventoryIntegrationTest.idempotencyAndAccess`, `AccountingIntegrationTest.postAndReverse`, `CashIntegrationTest.receiptAllocation`, `PurchaseOrderIntegrationTest.postAndReverse`, `thresholdAuthority`, `SalesOrderIntegrationTest.postDeliverAndFail` |

## 3. Kết quả lần chạy gần nhất (2026-10-21, sau D-63 chọn chứng từ nhập F8)
| Lệnh | Kết quả |
|---|---|
| `mvn clean verify` (bản sao pom, thư mục build `build-cli/target` vì Eclipse cùng biên dịch vào `target/classes`; MySQL 8 local, Flyway V1..V38 trên DB test trống) | 176 test: 176 đạt, 0 lỗi, 0 bỏ qua; BUILD SUCCESS (gồm đóng gói WAR) |
| `npm run lint` | 0 lỗi, 0 cảnh báo |
| `npm test` | 22 file, 78 test đạt |
| `npm run build` | Thành công (cảnh báo kích thước bundle) |

Test thêm theo D-63: `SalesReceiptSourceIntegrationTest.pickReceiptSource`, `salesLines.test.ts`.

## 3a. Lần chạy 2026-10-21, sau D-62 bỏ ghi sổ đơn mua / đơn bán
| Lệnh | Kết quả |
|---|---|
| `mvn verify` (MySQL 8 local, Flyway V1..V37 trên DB test trống) | 175 test: 175 đạt, 0 lỗi, 0 bỏ qua. Bước đóng gói WAR lỗi vì file `target/erp/WEB-INF/classes/application-dev.yml` đang bị tiến trình khác khóa (không liên quan mã); `mvn clean` cũng lỗi cùng lý do |
| `npm run lint` | 0 lỗi, 0 cảnh báo |
| `npm test` | 21 file, 76 test đạt |
| `npm run build` | Thành công (cảnh báo kích thước bundle) |

Test thêm theo D-62: `PurchaseOrderUnpostIntegrationTest` (`unpostEditRepost`, `blockedWhenGoodsIssued`), `SalesOrderUnpostIntegrationTest` (`unpostEditRepost`, `blockedAfterShipmentOrEinvoice`), `orderActions.test.ts`. Phủ AT-09, AT-10, AT-13 cho bỏ ghi sổ; chưa có test bỏ ghi sổ khi kỳ gốc đã khóa (AT-11).

## 3b. Lần chạy 2026-10-21, sau D-61 bỏ bước duyệt
| Lệnh | Kết quả |
|---|---|
| `mvn clean verify` (MySQL 8 local, Flyway V1..V36 trên DB test trống) | 171 test: 171 đạt, 0 lỗi, 0 bỏ qua. Chạy bằng bản sao pom có thư mục build tạm vì `backend/target` không ghi đè được (thiếu quyền xóa file); `ArchitectureTest` chạy lại với thư mục build tên `target` (ArchUnit nhận lớp test qua đường dẫn `/target/test-classes/`): 7/7 đạt |
| `npm run lint` | 0 lỗi, 0 cảnh báo |
| `npm test` | 20 file, 72 test đạt |
| `npm run build` | Thành công (cảnh báo kích thước bundle) |

Test viết lại / thêm theo D-61: `ApprovalIntegrationTest` (`noApprovalConfiguration`, `approvalEndpointsRemoved`), `PurchaseOrderIntegrationTest.thresholdAuthority`, `legacyPendingApproval`, `SalesOrderIntegrationTest.pricingAndAuthority`, `reservation`, `CashIntegrationTest.paymentApprovalAndCashLimit`, `InventoryIntegrationTest.reservationAndPolicy` (kích hoạt chính sách), `InventoryControlIntegrationTest.recall` (kích hoạt thu hồi).

## 3a. Lần chạy trước (2026-10-12, sau D-54 gộp chứng từ mua / bán)
| Lệnh | Kết quả |
|---|---|
| `mvn clean verify` (bản sao backend, MySQL 8.4 local) | 149 test: 149 đạt, 0 lỗi, 0 bỏ qua (Flyway V1..V23 trên DB test trống) |
| `npm run lint` | 0 lỗi, 0 cảnh báo |
| `npm test` | 11 file, 35 test đạt |
| `npm run build` | Thành công (cảnh báo kích thước bundle) |

Năm tạo kỳ trong các test khóa kỳ phải cách nhau ít nhất một năm (khóa kỳ đòi kỳ liền trước đã khóa): `InventoryIntegrationTest` 2062, `AccountingIntegrationTest` 2064, `DebtIntegrationTest` 2066, `CashIntegrationTest` 2068, `InventoryControlIntegrationTest` 2070, `PeriodLockIntegrationTest` 2050..2060 (chẵn). Trước 4C, Accounting (2071) và Debt (2072) liền nhau nên kết quả phụ thuộc thứ tự chạy lớp test; đã sửa ở 4C.

Số test backend theo lớp: `ArchitectureTest` 7; `GlobalExceptionHandlerTest` 6; `AuditLogServiceIntegrationTest` 3; `DocumentNumberFormatTest` 5; `DocumentNumberServiceIntegrationTest` 4; `IdempotencyServiceIntegrationTest` 4; `IdempotentCommandTest` 2; `RequestIdFilterTest` 2; `InventoryIntegrationTest` 8; `InventoryControlIntegrationTest` 7; `AccountingIntegrationTest` 5; `DebtIntegrationTest` 4; `CashIntegrationTest` 6; `DebtSettlementIntegrationTest` 6; `PurchaseOrderIntegrationTest` 3; `PurchaseReturnIntegrationTest` 2; `SalesOrderIntegrationTest` 3; `SalesReturnIntegrationTest` 2; `SalesReportIntegrationTest` 2; `MasterDataIntegrationTest` 8; `AccessGrantIntegrationTest` 2; `ApprovalIntegrationTest` 9; `AuthIntegrationTest` 8; `AuthorizationIntegrationTest` 9; `DocumentNumberFormatIntegrationTest` 4; `ParameterIntegrationTest` 4; `PeriodLockIntegrationTest` 6; `SecurityIntegrationTest` 3; `AdminBootstrapTest` 3; `LoginRateLimiterTest` 2; `ParameterServiceTest` 3; `PasswordPolicyTest` 4; `UserTest` 3.

## 4. Chưa kiểm thử
- Kiểm thử thủ công giao diện trên trình duyệt cho các màn hình 1D, Giai đoạn 2, 3, 4.
- Test tích hợp riêng cho: khóa / mở khóa serial qua API (đã phủ lô), nhập đối tác từ Excel.
- Triển khai WAR lên Tomcat 10.1 ngoài.
