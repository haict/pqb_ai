# Kế hoạch kiểm thử và báo cáo nghiệm thu

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.1 (đến hết Giai đoạn 3) |
| Ngày cập nhật | 2026-10-10 |
| Căn cứ | BR mục 18 (AT-01..AT-13), `.claude/rules/testing.md` |

## 1. Chiến lược
| Lớp | Công cụ | Phạm vi |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | Quy tắc thuần (mật khẩu, định dạng số chứng từ, tham số, giới hạn đăng nhập) |
| Tích hợp | Spring Boot Test + MockMvc trên **MySQL 8 thật**, schema `inventory_db_test` (xóa sạch rồi chạy Flyway V1..V8 + `db/dev-data` mỗi lần chạy) | API thật: quyền, phạm vi, idempotency, khóa dữ liệu, ghi sổ, đồng thời |
| Kiến trúc | ArchUnit | Ranh giới module, tầng, phụ thuộc vòng |
| Frontend | Vitest + React Testing Library | Điều hướng theo quyền, form, nút thao tác, client HTTP |

Sau mỗi kịch bản ghi sổ kho, test kiểm tra tính nhất quán: tồn chi tiết = tổng sổ kho theo từng khóa tồn; tổng (hàng, kho) = tổng tồn chi tiết; không dòng tồn nào âm (`InventoryIntegrationTest.assertLedgerConsistent`).

## 2. Kịch bản nghiệm thu
| Mã | Kịch bản | Trạng thái | Test |
|---|---|---|---|
| AT-01 | Đơn mua, nhận hàng nhiều đợt, hàng đang về | Chưa làm (Giai đoạn 5) | |
| AT-02 | Nhận hàng theo lô, hạn dùng, serial | Phủ phần kho (phiếu nhập kho) | `InventoryIntegrationTest.lotsSerialsAndFefo` |
| AT-03 | Đơn bán, xuất kho theo lô FEFO | Phủ phần kho (xuất nội bộ theo FEFO); đơn bán ở Giai đoạn 6 | `InventoryIntegrationTest.lotsSerialsAndFefo` |
| AT-04 | Hai người đồng thời xuất không làm tồn sai / âm | Đạt (5 luồng cùng ghi sổ trên một dòng tồn) | `InventoryIntegrationTest.concurrentIssues` |
| AT-05 | Giao hàng nhiều đợt | Chưa làm (Giai đoạn 6) | |
| AT-06 | Trả hàng, điều chỉnh tồn có truy vết | Phủ điều chỉnh từ kiểm kê; trả hàng ở Giai đoạn 5, 6 | `InventoryIntegrationTest.stockCount` |
| AT-07 | Hợp đồng thầu, chặn giao vượt | Chưa làm (Giai đoạn 7) | |
| AT-08 | Thu tiền một phần | Chưa làm (Giai đoạn 4) | |
| AT-09 | Hủy / đảo chứng từ đã ghi sổ | Phủ phần kho | `InventoryIntegrationTest.reversal` |
| AT-10 | Không có quyền thì không duyệt, không truy cập trái phép | Phủ M01, danh mục, kho | `AuthorizationIntegrationTest`, `ApprovalIntegrationTest`, `MasterDataIntegrationTest`, `InventoryIntegrationTest.idempotencyAndAccess`, `crossBranchTransfer` |
| AT-11 | Khóa kỳ chặn chứng từ thuộc kỳ khóa | Phủ khung khóa kỳ và ghi sổ kho; chưa phủ sửa / đảo trong kỳ khóa, giá vốn | `PeriodLockIntegrationTest`, `InventoryIntegrationTest.lockedPeriod` |
| AT-12 | Dashboard, báo cáo khớp nguồn | Chưa làm (Giai đoạn 10) | |
| AT-13 | Gửi lại yêu cầu không tạo dữ liệu trùng | Phủ dịch vụ chung, danh mục, kho | `IdempotencyServiceIntegrationTest`, `MasterDataIntegrationTest`, `InventoryIntegrationTest.idempotencyAndAccess` |

## 3. Kết quả lần chạy gần nhất (2026-10-10)
| Lệnh | Kết quả |
|---|---|
| `mvn clean verify` (bản sao backend, MySQL 8.4 local) | 109 test: 109 đạt, 0 lỗi, 0 bỏ qua |
| `npm run lint` | 0 lỗi, 0 cảnh báo |
| `npm test` | 10 file, 27 test đạt |
| `npm run build` | Thành công (cảnh báo kích thước bundle) |

Số test backend theo lớp: `ArchitectureTest` 7; `GlobalExceptionHandlerTest` 6; `AuditLogServiceIntegrationTest` 3; `DocumentNumberFormatTest` 5; `DocumentNumberServiceIntegrationTest` 4; `IdempotencyServiceIntegrationTest` 4; `IdempotentCommandTest` 2; `RequestIdFilterTest` 2; `InventoryIntegrationTest` 8; `MasterDataIntegrationTest` 8; `AccessGrantIntegrationTest` 2; `ApprovalIntegrationTest` 9; `AuthIntegrationTest` 8; `AuthorizationIntegrationTest` 9; `DocumentNumberFormatIntegrationTest` 4; `ParameterIntegrationTest` 4; `PeriodLockIntegrationTest` 6; `SecurityIntegrationTest` 3; `AdminBootstrapTest` 3; `LoginRateLimiterTest` 2; `ParameterServiceTest` 3; `PasswordPolicyTest` 4; `UserTest` 3.

## 4. Chưa kiểm thử
- Kiểm thử thủ công giao diện trên trình duyệt cho các màn hình 1D, Giai đoạn 2, 3.
- Test tích hợp riêng cho: phiếu chuyển trạng thái chất lượng, thu hồi (khóa lô khi duyệt, danh sách khách), khóa / mở khóa lô qua API, cảnh báo tồn, điều kiện khóa kỳ kho (`PeriodCloseCheck` của kho), chuyển kho cùng chi nhánh, nhập đối tác từ Excel.
- Triển khai WAR lên Tomcat 10.1 ngoài.
