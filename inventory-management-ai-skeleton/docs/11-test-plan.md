# Kế hoạch kiểm thử và báo cáo nghiệm thu

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.5 (đến Giai đoạn 4D) |
| Ngày cập nhật | 2026-10-10 |
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
| AT-01 | Đơn mua, nhận hàng nhiều đợt, hàng đang về | Đạt: hàng đang về cộng khi duyệt đơn, trừ khi nhập kho / đóng / hủy; nhận hai đợt (một đợt thao tác nhanh), số đã nhận / đã nhập / lỗi / còn thiếu và tồn đúng sau mỗi đợt; đảo đợt hai hoàn đúng | `PurchaseOrderIntegrationTest.requisitionToOrder`, `PurchaseReceiptIntegrationTest.receiveInBatches` |
| AT-02 | Nhận hàng theo lô, hạn dùng, serial | Đạt: nhận theo lô + hạn dùng, serial, hàng lỗi vào trạng thái cách ly; thiếu lô bị chặn | `InventoryIntegrationTest.lotsSerialsAndFefo`, `PurchaseReceiptIntegrationTest.receiveInBatches`, `overToleranceAndValidation` |
| AT-03 | Đơn bán, xuất kho theo lô FEFO | Đạt: phiếu giao theo đơn xuất lô hết hạn sớm nhất trước, lô chỉ định khác FEFO cần lý do (phần kho), dùng hàng đã giữ; lô dưới hạn dùng tối thiểu của khách bị chặn | `InventoryIntegrationTest.lotsSerialsAndFefo`, `SalesDeliveryIntegrationTest.multipleDeliveries` |
| AT-04 | Hai người đồng thời xuất không làm tồn sai / âm | Đạt (5 luồng cùng ghi sổ trên một dòng tồn); BR-GEN-52 cho quỹ, hóa đơn: hai luồng cùng phân bổ một hóa đơn, hai luồng cùng chi một quỹ | `InventoryIntegrationTest.concurrentIssues`, `CashIntegrationTest.concurrentAllocation`, `CashIntegrationTest.concurrentPayments`, `DebtSettlementIntegrationTest.concurrentPrepaymentOffsets` |
| AT-05 | Giao hàng nhiều đợt | Đạt: ba phiếu giao cho một đơn (một thất bại hàng về kho), số đã xuất / đã giao / còn lại đúng sau mỗi đợt, đơn hoàn thành khi giao đủ; số đã xuất hóa đơn theo dòng | `SalesDeliveryIntegrationTest.multipleDeliveries`, `SalesInvoiceIntegrationTest.invoiceDeliveriesAndService` |
| AT-06 | Trả hàng, điều chỉnh tồn có truy vết | Phủ điều chỉnh từ kiểm kê; trả hàng NCC (5D) đúng lô, có truy vết phiếu nhận / hóa đơn gốc, giảm tồn và công nợ; trả hàng bán (6E) đúng lô vào cách ly, giảm doanh thu, công nợ, số đã giao của đơn; giá vốn ở Giai đoạn 8 | `InventoryIntegrationTest.stockCount`, `PurchaseReturnIntegrationTest.returnsAndDiscount`, `SalesReturnIntegrationTest.returnDiscountExchange` |
| AT-07 | Hợp đồng thầu, chặn giao vượt | Chưa làm (Giai đoạn 7) | |
| AT-08 | Thu tiền một phần, phân bổ nhiều hóa đơn; biên bản đối chiếu | Đạt: thu một phần nhiều hóa đơn, trả trước, bù trừ trả trước; bảng đối chiếu khớp sổ, biên bản chốt số dư, lệch xử lý bằng điều chỉnh có duyệt | `CashIntegrationTest.receiptAllocation`, `DebtSettlementIntegrationTest.prepaymentOffset`, `DebtSettlementIntegrationTest.reconciliationAndAdjustment` |
| AT-09 | Hủy / đảo chứng từ đã ghi sổ | Phủ phần kho, công nợ, quỹ, mua hàng (`PurchaseInvoiceIntegrationTest.invoiceReceiptsAndService`: đảo hóa đơn mua hoàn công nợ, bút toán, chênh lệch giá, số đã nhận hóa đơn; chặn đảo phiếu nhận đã có hóa đơn; `PurchaseReturnIntegrationTest`: đảo trả hàng hoàn tồn, công nợ, bút toán; đảo phân bổ chi phí; chặn đảo hóa đơn / phiếu nhận đã có trả hàng / phân bổ) (nhập, xuất, chuyển kho cùng chi nhánh) | `InventoryIntegrationTest.reversal`, `InventoryControlIntegrationTest.sameBranchTransfer`, `AccountingIntegrationTest.postAndReverse` (đảo bút toán), `DebtIntegrationTest.reverseOpening` (đảo công nợ), `CashIntegrationTest.reverseReceipt` (đảo phiếu thu hoàn phân bổ; chặn đảo công nợ đã thu; chặn đảo làm quỹ âm), `PurchaseReceiptIntegrationTest.receiveInBatches` (đảo phiếu nhận hàng đảo cả phiếu nhập kho; chặn đảo trực tiếp phiếu nhập kho có nguồn), `DebtSettlementIntegrationTest` (bỏ bù trừ; chặn đảo phiếu thu có trả trước đã bù trừ; đảo điều chỉnh; chặn đảo tạm ứng đã hoàn; đảo kiểm kê quỹ) |
| AT-10 | Không có quyền thì không duyệt, không truy cập trái phép | Phủ M01, danh mục, kho | `AuthorizationIntegrationTest`, `ApprovalIntegrationTest`, `MasterDataIntegrationTest`, `InventoryIntegrationTest.idempotencyAndAccess`, `crossBranchTransfer`, `InventoryControlIntegrationTest` (duyệt PCL / thu hồi, khóa lô, cảnh báo ngoài phạm vi), `CashIntegrationTest.paymentApprovalAndCashLimit` (chi từ ngưỡng phải duyệt), `CashIntegrationTest.transferFlowPeriodAndAccess` (403, 404 chi nhánh khác), `PurchaseOrderIntegrationTest.thresholdApproval` (người lập không duyệt được; chi nhánh khác 404), `PurchaseOrderIntegrationTest.validation` |
| AT-11 | Khóa kỳ chặn chứng từ thuộc kỳ khóa | Phủ khung khóa kỳ, ghi sổ kho, điều kiện khóa kỳ kho; chưa phủ sửa / đảo trong kỳ khóa, giá vốn | `PeriodLockIntegrationTest`, `InventoryIntegrationTest.lockedPeriod`, `InventoryControlIntegrationTest.periodCloseCheck`, `AccountingIntegrationTest.lockedAccountingPeriod`, `DebtIntegrationTest.lockedDebtPeriod`, `CashIntegrationTest.transferFlowPeriodAndAccess` (kỳ quỹ) |
| AT-12 | Dashboard, báo cáo khớp nguồn | Phủ báo cáo doanh số: tổng chứng từ cấu thành bằng doanh thu thuần của nhóm; dashboard ở Giai đoạn 10 | `QuickSaleReportIntegrationTest.reportAndSchedule` |
| AT-13 | Gửi lại yêu cầu không tạo dữ liệu trùng | Phủ dịch vụ chung, danh mục, kho, bút toán (một bút toán gốc mỗi chứng từ) | `IdempotencyServiceIntegrationTest`, `MasterDataIntegrationTest`, `InventoryIntegrationTest.idempotencyAndAccess`, `AccountingIntegrationTest.postAndReverse`, `CashIntegrationTest.receiptAllocation` (gửi lại lệnh ghi sổ phiếu thu), `PurchaseOrderIntegrationTest.thresholdApproval` (gửi duyệt lại cùng key: một yêu cầu duyệt) |

## 3. Kết quả lần chạy gần nhất (2026-10-10, sau Giai đoạn 6)
| Lệnh | Kết quả |
|---|---|
| `mvn clean verify` (bản sao backend, MySQL 8.4 local) | 154 test: 154 đạt, 0 lỗi, 0 bỏ qua |
| `npm run lint` | 0 lỗi, 0 cảnh báo |
| `npm test` | 10 file, 33 test đạt |
| `npm run build` | Thành công (cảnh báo kích thước bundle) |

Năm tạo kỳ trong các test khóa kỳ phải cách nhau ít nhất một năm (khóa kỳ đòi kỳ liền trước đã khóa): `InventoryIntegrationTest` 2062, `AccountingIntegrationTest` 2064, `DebtIntegrationTest` 2066, `CashIntegrationTest` 2068, `InventoryControlIntegrationTest` 2070, `PeriodLockIntegrationTest` 2050..2060 (chẵn). Trước 4C, Accounting (2071) và Debt (2072) liền nhau nên kết quả phụ thuộc thứ tự chạy lớp test; đã sửa ở 4C.

Số test backend theo lớp: `ArchitectureTest` 7; `GlobalExceptionHandlerTest` 6; `AuditLogServiceIntegrationTest` 3; `DocumentNumberFormatTest` 5; `DocumentNumberServiceIntegrationTest` 4; `IdempotencyServiceIntegrationTest` 4; `IdempotentCommandTest` 2; `RequestIdFilterTest` 2; `InventoryIntegrationTest` 8; `InventoryControlIntegrationTest` 7; `AccountingIntegrationTest` 5; `DebtIntegrationTest` 4; `CashIntegrationTest` 6; `DebtSettlementIntegrationTest` 6; `PurchaseOrderIntegrationTest` 3; `PurchaseReceiptIntegrationTest` 2; `PurchaseInvoiceIntegrationTest` 2; `PurchaseReturnIntegrationTest` 2; `SalesQuoteOrderIntegrationTest` 2; `SalesDeliveryIntegrationTest` 1; `SalesInvoiceIntegrationTest` 1; `SalesReturnIntegrationTest` 2; `QuickSaleReportIntegrationTest` 2; `MasterDataIntegrationTest` 8; `AccessGrantIntegrationTest` 2; `ApprovalIntegrationTest` 9; `AuthIntegrationTest` 8; `AuthorizationIntegrationTest` 9; `DocumentNumberFormatIntegrationTest` 4; `ParameterIntegrationTest` 4; `PeriodLockIntegrationTest` 6; `SecurityIntegrationTest` 3; `AdminBootstrapTest` 3; `LoginRateLimiterTest` 2; `ParameterServiceTest` 3; `PasswordPolicyTest` 4; `UserTest` 3.

## 4. Chưa kiểm thử
- Kiểm thử thủ công giao diện trên trình duyệt cho các màn hình 1D, Giai đoạn 2, 3, 4.
- Test tích hợp riêng cho: khóa / mở khóa serial qua API (đã phủ lô), nhập đối tác từ Excel.
- Triển khai WAR lên Tomcat 10.1 ngoài.
