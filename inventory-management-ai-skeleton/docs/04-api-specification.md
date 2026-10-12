# Đặc tả API REST

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 (DRAFT - hợp đồng API chi tiết chốt khi làm từng module) |
| Ngày cập nhật | 2026-10-10 |
| Base path | `/api/v1` |
| Tài liệu sinh tự động | OpenAPI `/v3/api-docs`, Swagger UI `/swagger-ui.html` (tắt hoặc bảo vệ ở `prod`) |
| Quy tắc | `.claude/rules/api.md` |

## 1. Quy ước chung

### 1.1 Header
| Header | Bắt buộc | Mô tả |
|---|---|---|
| `Authorization: Bearer <access token>` | Mọi API trừ đăng nhập, làm mới token | |
| `Idempotency-Key: <uuid>` | `POST` tạo mới và mọi endpoint hành động | Chống xử lý trùng (BR-GEN-50) |
| `X-Branch-Id` | Tùy chọn | Chi nhánh đang làm việc; server kiểm tra thuộc phạm vi người dùng |
| `X-Request-Id` | Tùy chọn | Server sinh nếu thiếu, trả lại trong response |

### 1.2 Phản hồi thành công
```json
{ "success": true, "data": { }, "message": null, "timestamp": "2026-10-09T03:00:00Z" }
```
Danh sách:
```json
{
  "success": true,
  "data": { "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 },
  "timestamp": "..."
}
```

### 1.3 Phản hồi lỗi
```json
{
  "success": false,
  "code": "INSUFFICIENT_STOCK",
  "message": "Tồn khả dụng của lô L2401 không đủ (còn 5, cần 8).",
  "errors": [ { "field": "lines[0].quantity", "message": "Phải lớn hơn 0" } ],
  "requestId": "7f3c…",
  "timestamp": "..."
}
```

### 1.4 Mã lỗi
| HTTP | Mã | Ý nghĩa |
|---|---|---|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST` | Dữ liệu không hợp lệ / JSON, tham số, header sai định dạng |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED` | Chưa đăng nhập / access token hết hạn (frontend gọi `/auth/refresh` rồi gửi lại) / đăng nhập sai hoặc tài khoản bị khóa / phiên hết hạn, phải đăng nhập lại |
| 403 | `FORBIDDEN`, `SELF_APPROVAL_NOT_ALLOWED`, `PASSWORD_CHANGE_REQUIRED` | Không đủ quyền / tự duyệt / phải đổi mật khẩu trước khi dùng API khác |
| 404 | `NOT_FOUND` | Không tồn tại hoặc ngoài phạm vi dữ liệu |
| 405 | `METHOD_NOT_ALLOWED` | Phương thức HTTP không hỗ trợ |
| 409 | `DUPLICATE_CODE`, `DUPLICATE_DOCUMENT`, `INVALID_DOCUMENT_STATE`, `CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS`, `HAS_DEPENDENT_DOCUMENTS`, `DATA_INTEGRITY_VIOLATION`, `INVALID_PERIOD_STATE`, `CONFIGURATION_IN_EFFECT`, `DATA_IN_USE` | Xung đột. `DATA_IN_USE`: danh mục đã được tham chiếu, không xóa / đổi được. `HAS_DEPENDENT_DOCUMENTS`: đảo chứng từ khi hàng đã được chứng từ khác xuất (thông báo liệt kê số chứng từ cần đảo trước). `REQUEST_IN_PROGRESS`: yêu cầu cùng `Idempotency-Key` đang xử lý song song. `DATA_INTEGRITY_VIOLATION`: ràng buộc DB chặn (lưới an toàn khi kiểm tra nghiệp vụ bỏ sót). `INVALID_PERIOD_STATE`: khóa kỳ đã khóa / mở kỳ đang mở. `CONFIGURATION_IN_EFFECT`: xóa cấu hình số chứng từ đã có hiệu lực |
| 422 | `INSUFFICIENT_STOCK`, `LOT_EXPIRED`, `LOT_NOT_SELLABLE`, `SERIAL_UNAVAILABLE`, `PERIOD_LOCKED`, `PERIOD_NOT_DEFINED`, `PERIOD_ORDER_VIOLATION`, `PERIOD_CLOSE_PRECONDITION`, `PARAMETER_LOCKED`, `CONTRACT_QUANTITY_EXCEEDED`, `CREDIT_LIMIT_EXCEEDED`, `PRICE_BELOW_FLOOR`, `OVER_ALLOCATION`, `SOURCE_QUANTITY_EXCEEDED`, `CASH_BALANCE_NEGATIVE`, `STOCK_COUNT_IN_PROGRESS`, `APPROVAL_NOT_CONFIGURED`, `POSTING_RULE_NOT_FOUND`, `POSTING_RULE_INCOMPLETE`, `ACCOUNT_NOT_POSTABLE`, `UNBALANCED_JOURNAL` | Vi phạm quy tắc nghiệp vụ. `POSTING_RULE_NOT_FOUND`: chưa có quy tắc hạch toán còn hiệu lực cho nghiệp vụ. `POSTING_RULE_INCOMPLETE`: quy tắc để trống một vế mà chứng từ không truyền tài khoản. `CASH_BALANCE_NEGATIVE`: quỹ tiền mặt không đủ khi chi hoặc khi đảo phiếu thu. `OVER_ALLOCATION`: phân bổ vượt số còn lại của hóa đơn (kiểm tra sau khi khóa). `SOURCE_QUANTITY_EXCEEDED`: chứng từ đích vượt số còn lại của chứng từ nguồn (đơn mua vượt đề nghị mua). `ACCOUNT_NOT_POSTABLE`: tài khoản ngừng / tổng hợp, hoặc bắt buộc đối tượng mà thiếu. `UNBALANCED_JOURNAL`: Nợ ≠ Có. `STOCK_COUNT_IN_PROGRESS`: kho đang kiểm kê (D-11). `APPROVAL_NOT_CONFIGURED`: loại chứng từ bắt buộc duyệt nhưng chưa có luồng khớp (D-52). `LOT_EXPIRED` / `LOT_NOT_SELLABLE`: lô hết hạn / không ĐẠT hoặc bị khóa (BR-INV-04, BR-INV-23). `PERIOD_NOT_DEFINED`: ngày hạch toán chưa có kỳ. `PERIOD_ORDER_VIOLATION`: khóa/mở sai thứ tự tháng hoặc mở kỳ kho khi kỳ kế toán còn khóa. `PERIOD_CLOSE_PRECONDITION`: chưa đủ điều kiện khóa (BR-SYS-31). `PARAMETER_LOCKED`: tham số đã khóa vì đã phát sinh số liệu ghi sổ |
| 423 | `LOCK_TIMEOUT` | Dữ liệu đang được xử lý, thử lại |
| 429 | `TOO_MANY_REQUESTS` | Đăng nhập sai quá nhiều lần từ một IP (`erp.auth.ip-rate-limit`) |
| 500 | `INTERNAL_ERROR` | Lỗi hệ thống |

### 1.5 Phân trang, sắp xếp, lọc
`GET /{resource}?page=0&size=20&sort=documentDate,desc&status=POSTED&fromDate=2026-01-01&toDate=2026-10-09&keyword=...`
- `size` tối đa 200; trường `sort` và bộ lọc theo danh sách cho phép của từng API.
- Dữ liệu tự động lọc theo phạm vi của người dùng.

### 1.6 Mẫu endpoint cho chứng từ
| Phương thức | Đường dẫn | Mô tả | Quyền |
|---|---|---|---|
| POST | `/{docs}` | Tạo DRAFT | `<doc>:create` |
| GET | `/{docs}` | Danh sách | `<doc>:view` |
| GET | `/{docs}/{id}` | Chi tiết (gồm dòng) | `<doc>:view` |
| PUT | `/{docs}/{id}` | Sửa (DRAFT/REJECTED, gửi `version`) | `<doc>:update` |
| DELETE | `/{docs}/{id}` | Xóa DRAFT chưa từng gửi duyệt | `<doc>:delete` |
| POST | `/{docs}/{id}/submit` | Gửi duyệt | `<doc>:submit` |
| POST | `/{docs}/{id}/approve` | Duyệt | `<doc>:approve` |
| POST | `/{docs}/{id}/reject` | Từ chối (`reason`) | `<doc>:approve` |
| POST | `/{docs}/{id}/post` | Ghi sổ (nhóm A) | `<doc>:post` |
| POST | `/{docs}/{id}/cancel` | Hủy chưa ghi sổ (`reason`) | `<doc>:cancel` |
| POST | `/{docs}/{id}/reverse` | Đảo chứng từ đã ghi sổ (`reason`, `reversalDate`) | `<doc>:reverse` |
| POST | `/{docs}/{id}/close` | Đóng phần còn lại (nhóm B, `reason`) | `<doc>:close` |
| GET | `/{docs}/{id}/related-documents` | Chứng từ nguồn và đích | `<doc>:view` |
| GET | `/{docs}/{id}/history` | Lịch sử xử lý | `<doc>:view` |
| GET | `/{docs}/{id}/print` | In / PDF | `<doc>:print` |
| GET | `/{docs}/export` | Xuất Excel theo bộ lọc | `<doc>:export` |

Mẫu danh mục: `POST`, `GET`, `GET /{id}`, `PUT /{id}`, `PATCH /{id}/status`, `POST /import` (Excel, kiểm tra toàn bộ trước khi ghi).

**Không có** endpoint sửa trực tiếp số dư tồn kho, công nợ, quỹ.

## 2. Danh sách tài nguyên theo module
Ký hiệu: **[CT]** áp dụng mẫu chứng từ mục 1.6; **[DM]** áp dụng mẫu danh mục.

### M01 - Quản trị hệ thống
| Tài nguyên | Ghi chú |
|---|---|
| `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me`, `POST /auth/change-password` | **Đã triển khai (1A).** Đăng nhập, làm mới token (cookie httpOnly), đăng xuất, thông tin + quyền + chi nhánh, đổi mật khẩu. Chi tiết: docs/07 mục 1 |
| `/companies`, `/branches`, `/departments` [DM] | **Đã triển khai (1A).** Lọc: `keyword`, `status`, `companyId` / `branchId`; sắp xếp `code, name, createdAt, updatedAt`. Ngoài phạm vi dữ liệu: 404 |
| `/users` [DM], `POST /users/{id}/reset-password`, `PUT /users/{id}/roles`, `PUT /users/{id}/data-scopes` | **Đã triển khai (1A).** Không gán vai trò/phạm vi vượt quyền người thao tác; không tự sửa quyền của mình (403) |
| `/roles` [DM], `PUT /roles/{id}/permissions`, `GET /permissions` | **Đã triển khai (1A).** Không ngừng hoạt động vai trò hệ thống |
| `GET /document-number-formats?companyId=&documentType=`, `POST /document-number-formats`, `DELETE /document-number-formats/{id}` | **Đã triển khai (1C).** Cấu hình số chứng từ (thay tên dự kiến `/document-sequences`). `POST` đặt cấu hình (`documentType, prefix, sequenceLength, resetCycle`), server quyết định `effectiveFrom`: chưa cấp số → hiệu lực ngay; đã cấp số → chu kỳ sau. `DELETE` chỉ cho cấu hình đặt trước. Cần phạm vi toàn công ty để sửa |
| `/approval-workflows` [DM], `GET/PUT /document-types` | **Đã triển khai (1D).** Luồng duyệt theo loại chứng từ (chi nhánh, ngưỡng giá trị / chiết khấu, ưu tiên, các bước ROLE/USER); chế độ duyệt từng loại (D-52) |
| `GET /approvals/pending`, `GET /approvals/{id}` | **Đã triển khai (1D).** Việc chờ duyệt của tôi (gồm phần được ủy quyền). Hành động duyệt / từ chối nằm ở endpoint của chứng từ (`POST /{resource}/{id}/approve|reject`) |
| `/approval-delegations`, `POST /approval-delegations/{id}/revoke` | **Đã triển khai (1D).** Ủy quyền duyệt có thời hạn |
| `GET /users/lookup?companyId=&keyword=` | **Đã triển khai (1D).** Tìm người dùng để chọn người duyệt / người được ủy quyền |
| `GET /fiscal-periods?companyId=&year=&branchId=`, `POST /fiscal-periods/generate`, `POST /fiscal-periods/{id}/lock`, `POST /fiscal-periods/{id}/unlock` | **Đã triển khai (1C).** Danh sách: tối đa 12 kỳ kèm trạng thái khóa 4 phân hệ của một chi nhánh (không phân trang). `generate` (`companyId, year`): tạo 12 kỳ, cần phạm vi toàn công ty. `lock`/`unlock` (`module, branchIds[], reason`): mọi chi nhánh cùng thành công hoặc không; `unlock` bắt buộc `reason`, quyền `period:unlock`. Thứ tự: kỳ trước phải khóa trước; kỳ kế toán khóa sau kỳ kho; mở theo thứ tự ngược |
| `GET /parameters?companyId=`, `PUT /parameters/{key}` | **Đã triển khai (1C).** `PUT` (`companyId, value, version`): `version` = 0 khi chưa có giá trị riêng; sai kiểu/giới hạn: 400 trên trường `value`; cần phạm vi toàn công ty |
| `GET /audit-logs`, `GET /login-logs` | Chỉ đọc. `GET /login-logs` **đã triển khai (1A)**: lọc `username, event, from, to` |
| `/attachments` (`POST` multipart, `GET /{id}/download`) | Giới hạn loại và dung lượng (D-41) |
| `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` | **Đã triển khai (1D).** Chỉ trong ứng dụng (D-49) |

### M02 - Danh mục
`/product-categories`, `/products`, `/units`, `/products/{id}/units`, `/brands`, `/manufacturers`, `/countries`, `/partners` (lọc theo `role`), `/partners/{id}/contacts`, `/partners/{id}/addresses`, `/warehouses`, `/warehouses/{id}/locations`, `/tax-rates`, `/price-lists`, `/currencies`, `/exchange-rates`, `/payment-terms`, `/payment-methods`, `/delivery-methods`, `/contract-types`, `/reasons`, `/cost-items`, `/compliance-document-types`, `/compliance-documents` — tất cả [DM].

`GET /compliance-documents/expiring?days=30` — giấy tờ sắp hết hạn.

**Đã triển khai (Giai đoạn 2):** `/units`, `/brands`, `/manufacturers`, `/partner-groups`, `/countries`, `/reference-items` (thay `/reasons`, `/payment-terms`... bằng một danh mục có `category`), `/tax-rates`, `/product-categories`, `/products` (+ `POST /{id}/units`, `PUT /{id}/stock-levels`, `GET /import-template`, `POST /import`), `/partners` (+ `POST /partners/import`), `/warehouses` (+ `/{id}/locations`, `/warehouse-locations/{id}`), `/price-lists` (+ `PUT /{id}/items`), `/compliance-document-types`, `/compliance-documents`. Danh mục ngừng hoạt động bằng `PATCH /{id}/status` (lý do, `version`). Giá mua tham khảo ẩn khi thiếu `purchase-price:view`. Chưa làm: `/currencies`, `/exchange-rates` (dời tới module dùng ngoại tệ).

### M03 - Mua hàng
| Tài nguyên | Ghi chú |
|---|---|
| `/purchase-requisitions` [CT nhóm B, 5A] | Đề nghị mua: CRUD, `submit`, `approve`, `reject`, `cancel`, `close` (đóng phần chưa đặt, bắt buộc lý do); `GET /open-lines?branchId=&productId=` dòng đề nghị đã duyệt còn số chưa đặt. Quyền `purchase-requisition:view/create/approve/close` |
| `/supplier-quotation-requests` [CT nhóm B] | Gửi nhiều NCC |
| `/supplier-quotations` [CT nhóm B] | `GET /supplier-quotation-requests/{id}/comparison`; `POST /supplier-quotations/{id}/select` |
| `/purchase-orders` [CT nhóm B, 5A] | CRUD (lọc `supplierId`, `warehouseId`, `status`, `openOnly` = chỉ đơn còn mở để nhận hàng, ngày), `submit` (từ ngưỡng `purchasing.order-approval-threshold` phải duyệt, chưa có luồng: 422 `APPROVAL_NOT_CONFIGURED`; dưới ngưỡng tự duyệt), `approve`, `reject`, `cancel` (chưa phát sinh nhận hàng), `close` (đóng phần còn lại). Chi tiết trả tiến độ từng dòng (đặt / đã nhận / đã nhập / lỗi / đã nhận HĐ / đã đóng / còn thiếu) thay cho `/progress` riêng. Dòng tham chiếu `requisitionLineId`: vượt số còn lại của đề nghị 422 `SOURCE_QUANTITY_EXCEEDED`. Quyền `purchase-order:view/create/approve/close` |
| `/purchase-receipts` [CT nhóm A, 5B; đảo khi đã có hóa đơn ghi sổ: 409 `HAS_DEPENDENT_DOCUMENTS`] | Phiếu nhận hàng theo đơn mua: CRUD (lọc `purchaseOrderId`, `status`, ngày), `submit` (vượt dung sai `purchasing.receipt-over-tolerance-percent` phải duyệt), `approve`, `reject`, `cancel`, `post` (xác nhận nhận hàng: số đã nhận / lỗi của đơn mua; vượt dung sai mới phát sinh 422 `SOURCE_QUANTITY_EXCEEDED`), `stock-in` (lập và ghi sổ phiếu nhập kho PNK, thay cho `create-inventory-receipt` dự kiến; kỳ kho khóa 422 `PERIOD_LOCKED`), `POST /receive-and-stock` (thao tác nhanh D-36: lập, gửi, ghi sổ, nhập kho; vượt dung sai thì dừng ở chờ duyệt), `reverse` (`reason`, `postingDate`; đảo cả PNK, hàng đã xuất 409 `HAS_DEPENDENT_DOCUMENTS`). Quyền `purchase-receipt:view/create/approve/post/reverse` |
| `/purchase-invoices` [CT nhóm A, 5C] | CRUD (lọc `supplierId`, `status`, ngày), `submit`, `approve`, `reject`, `cancel`, `post` (công nợ phải trả, bút toán, chênh lệch giá; kỳ khóa 422 `PERIOD_LOCKED`; thiếu quy tắc 422 `POSTING_RULE_NOT_FOUND`), `reverse` (`reason`, `postingDate`; đã thanh toán / bù trừ 409 `HAS_DEPENDENT_DOCUMENTS`); `GET /invoiceable-lines?branchId=&supplierId=` dòng phiếu nhận đã nhập kho còn chưa nhận hóa đơn. Trùng (MST, ký hiệu, số): 409 `DUPLICATE_DOCUMENT`; vượt số chưa nhận hóa đơn: 422 `SOURCE_QUANTITY_EXCEEDED`. Chi tiết trả `matchWarnings` (đối chiếu ba chiều) và `provisionalUnitPrice` từng dòng. Quyền `purchase-invoice:*` |
| `/purchase-returns` [CT nhóm A, 5D] | Trả hàng NCC (`documentType=THN`) và giảm giá hàng mua (`GGM`): CRUD (lọc `documentType`, `supplierId`, `status`, ngày), `submit`, `approve`, `reject`, `cancel`, `post` (trả hàng: phiếu xuất trả PXK đúng lô / serial, tồn không đủ 422 `INSUFFICIENT_STOCK`; có hóa đơn / giảm giá: giảm công nợ phải trả, vượt số còn nợ ghi trả trước, bút toán), `reverse`; `GET /returnable-lines?branchId=&supplierId=&invoiceId=` (trống `invoiceId`: dòng phiếu nhận đã nhập kho chưa có hóa đơn). Vượt số còn trả / giá trị còn lại: 422 `SOURCE_QUANTITY_EXCEEDED`. Quyền `purchase-return:view/create/approve/post/reverse` |
| `/purchase-cost-allocations` [CT nhóm A, 5D] | Phân bổ chi phí mua (thay tên dự kiến `/landed-cost-vouchers`): CRUD (`sources[]: invoiceLineId, amount`, `receiptIds[]`, `method` VALUE / QUANTITY; server tính phân bổ), `submit`, `approve`, `reject`, `cancel`, `post`, `reverse`; `GET /cost-lines?branchId=` dòng dịch vụ hóa đơn mua đã ghi sổ còn chưa phân bổ. Quyền `purchase-cost-allocation:*` |

### M04 - Kho
| Tài nguyên | Ghi chú |
|---|---|
**Đã triển khai (Giai đoạn 3).** So với dự kiến bản trước: bảy loại phiếu kho dùng chung tài nguyên `/inventory-documents` có `documentType` (ADR-10) thay cho `/inventory-receipts`, `/inventory-issues`, `/inventory-transfers`, `/inventory-adjustments`, `/quality-status-changes`; tên hành động kiểm kê đổi thành `start`, `submit`. Các endpoint dự kiến chưa từng phát hành nên không có client nào bị ảnh hưởng.

| Tài nguyên | Ghi chú |
|---|---|
| `/inventory-documents` [CT nhóm A] | Lọc `companyId` (bắt buộc), `documentType, status, warehouseId, keyword, dateFrom, dateTo`; sắp xếp `documentDate, postingDate, documentNo, createdAt, updatedAt`. Hành động: `submit, approve, reject, cancel, post`, `receive` (nhận hàng chuyển khác chi nhánh, body `postingDate` tùy chọn), `reverse` (body `reason`, `postingDate`; trả chứng từ đảo PDK đã gửi duyệt). Quyền theo loại: `<loại>:view|create|approve|post` (docs/07 mục 2.3). Chi tiết trả `transactions` (sổ kho đã ghi), `approvals`, `allowedActions` |
| `/stock-counts` [CT] | `POST /{id}/start` (chụp tồn, chặn nhập xuất kho), `PUT /{id}/counts` (`lines[]: lineId` hoặc dòng phát sinh `productId, locationId, lotId, serialId, qualityStatus`, `countedQuantity`), `submit, approve, reject` (quay về đang đếm), `post` (sinh PDC loại COUNT, mở khóa kho), `cancel` |
| `/recalls` [CT nhóm B] | `submit, approve` (khóa lô / serial), `reject, cancel, close`. Chi tiết gồm `customers` (đã giao, đã thu hồi theo khách) thay cho `/affected-customers` |
| `/stock-issue-policies` [CT] | `submit, approve, reject, cancel`. Phiếu xuất / chuyển kho tham chiếu `issuePolicyId`; chính sách phải đã duyệt và chưa dùng cho chứng từ khác (400 trên `issuePolicyId`; khi ghi sổ: 422 `INSUFFICIENT_STOCK`). Chính sách đã dùng không hủy được (409). Phản hồi có `usedByDocumentId` (D-53) |
| `GET /inventory/stock-summaries?companyId=&warehouseId=&productId=` | Tồn thực tế, bán được, giữ chỗ, khả dụng, đang về theo (hàng, kho) |
| `GET /inventory/stock-balances?...&lotId=&serialId=&qualityStatus=` | Tồn chi tiết theo vị trí / lô / serial / trạng thái |
| `GET /inventory/transactions?companyId=&warehouseId=&productId=&lotId=&serialId=&referenceType=&referenceId=&dateFrom=&dateTo=` | Sổ kho; lọc theo lô / serial là truy xuất lô / serial. Chưa có số dư lũy kế từng dòng (thẻ kho đầy đủ: Giai đoạn 10) |
| `GET /inventory/lots`, `GET /inventory/serials` | Lô kèm tồn và số ngày còn hạn; serial kèm vòng đời, kho hiện tại |
| `POST /inventory/lots/{id}/block|unblock`, `POST /inventory/serials/{id}/block|unblock` | Quyền `lot:block`, bắt buộc `reason` |
| `GET /inventory/fefo-suggestions?warehouseId=&productId=&quantity=` | Đề xuất lô theo FEFO |
| `GET /inventory/alerts?companyId=&warehouseId=` | `EXPIRING` (trong `inventory.expiry-warning-days` ngày hoặc đã hết hạn còn tồn), `BELOW_MIN` (dưới định mức). Chưa có `SLOW_MOVING` |
| Giữ chỗ | Chưa có endpoint: `InventoryReservationService.reserve/reserveAll/release/releaseAll/consume/restore` là API nội bộ cho phân hệ bán hàng / hợp đồng; nhả giữ chỗ quá hạn chạy định kỳ (`erp.inventory.reservation-expiry-cron`, mặc định 15 phút) |
| Xuất / nhập theo chứng từ nguồn | API nội bộ `StockOutService` (xuất bán, trả NCC: PXK có nguồn, ghi sổ ngay, trả lô / serial thực xuất) và `StockInService` (nhập mua, nhập hàng bán trả lại `SALES_RETURN`); đảo chỉ qua chứng từ nguồn |

### M05 - Bán hàng
| Tài nguyên | Ghi chú |
|---|---|
**Đã triển khai (Giai đoạn 6).** So với dự kiến bản trước: hành động đổi tên `send` (thay `mark-sent`), `create-order` (thay `create-sales-order`); tồn khả dụng và tiến độ trả trong chi tiết đơn (thay `/availability`, `/progress`); phiếu xuất theo đơn nằm trong phiếu giao `/sales-deliveries` (ghi sổ = xuất kho, thay `create-issue` và `/deliveries`); số HĐĐT ghi bằng `POST /{id}/einvoice-reference` (hành động có Idempotency-Key, thay `PUT`); trả hàng và giảm giá là `documentType` TLB / GGB, đổi hàng là hành động `create-exchange-delivery` (thay loại EXCHANGE); `/promotions` chưa làm. Các endpoint dự kiến chưa từng phát hành.

| Tài nguyên | Ghi chú |
|---|---|
| `GET /sales/price?customerId=&productId=&unitId=&quantity=&date=` | Giá đề xuất: bảng giá riêng của khách → nhóm khách / đại lý → niêm yết; kèm giá sàn, chiết khấu đại lý, thuế mặc định (BR-SAL-02). Quyền `sales-quote:view` hoặc `sales-order:view` |
| `/sales-quotes` [CT nhóm B] | CRUD (lọc `customerId`, `status`, ngày), `submit` (giá dưới sàn / chiết khấu vượt `sales.discount-approval-percent`: phải duyệt, chưa có luồng 422 `APPROVAL_NOT_CONFIGURED`; không vi phạm: tự duyệt), `approve`, `reject`, `cancel`, `send`, `accept` (hết hiệu lực 409), `decline` (`reason`), `revise` (phiên bản mới, bản cũ CLOSED), `create-order` (`warehouseId`, `expectedDate`, `deliveryAddress`, `reserveStock`; mỗi báo giá một đơn). Quyền `sales-quote:view/create/approve` |
| `/sales-orders` [CT nhóm B] | CRUD (`lines`, `paymentTerms`, `reserveStock`; lọc `customerId`, `status`, `openOnly`), `submit` (giá, hạn mức tín dụng / nợ quá hạn phải duyệt, lý do ở `approvalReasons`; giữ hàng khi duyệt nếu `reserveStock`, thiếu 422 `INSUFFICIENT_STOCK`), `approve`, `reject`, `cancel` (nhả giữ chỗ), `close` (`reason`, đóng phần còn lại, nhả giữ chỗ), `reserve`, `release`, `create-purchase-request` (đề nghị mua phần thiếu, trả `requisitionId`, `requisitionNo`). Chi tiết trả tiến độ dòng, tồn khả dụng / đang giữ, `availabilityWarnings`, `paymentSchedule`. Quyền `sales-order:view/create/approve/close` |
| `/sales-deliveries` [CT nhóm A] | CRUD (theo đơn đã duyệt, dòng hàng hóa còn phải xuất, lô chỉ định / lý do / serial), `submit`, `approve`, `reject`, `cancel`, `post` (xuất kho PXK FEFO dùng hàng đã giữ; vượt 422 `SOURCE_QUANTITY_EXCEEDED`; thiếu tồn 422 `INSUFFICIENT_STOCK`; lô dưới hạn dùng tối thiểu của khách 422 `LOT_NOT_SELLABLE`), `ship` (`shipperName`), `confirm` (`receivedDate`, `receivedBy`, `signedDocumentRef`, `note`), `fail` (`reason`; hàng về kho bằng chứng từ đảo), `reverse` (chưa giao, chưa có hóa đơn / trả hàng). Chi tiết trả hàng thực xuất (lô, hạn, serial), số lưu hành. Quyền `sales-delivery:view/create/approve/post/deliver/reverse` |
| `/sales-invoices` [CT nhóm A] | CRUD (dòng `deliveryLineId` hoặc `orderLineId` dịch vụ), `submit`, `approve`, `reject`, `cancel`, `einvoice-reference` (`series`, `number`, `date`, `taxAuthorityCode`; trùng 409 `DUPLICATE_DOCUMENT`), `post` (công nợ phải thu, bút toán HDB), `reverse` (đã thu / có trả hàng: 409); `GET /invoiceable-lines?branchId=&customerId=`. Chi tiết trả `warnings` (lập trước khi khách nhận). Quyền `sales-invoice:*` |
| `/sales-returns` [CT nhóm A] | `documentType` TLB / GGB; CRUD, `submit`, `approve`, `reject`, `cancel`, `post` (TLB: nhập lại kho đúng lô / serial, mặc định CÁCH LY, giảm số đã giao; có hóa đơn / GGB: giảm công nợ phải thu và doanh thu), `reverse`, `create-exchange-delivery` (đổi hàng: phiếu giao nháp cho phần đã trả); `GET /returnable-lines?branchId=&customerId=&invoiceId=`. Vượt số còn trả (dòng, lô) 422 `SOURCE_QUANTITY_EXCEEDED`. Quyền `sales-return:*` |
| `/sales-complaints` | Hàng lỗi khách báo: CRUD, `inspect` (`result`), `resolve` (`decision`, `note`, `salesReturnId`), `cancel`. Quyền `sales-complaint:view/create/resolve` |
| `POST /sales/quick-sale` | Bán nhanh: đơn → phiếu giao (xuất kho, khách nhận) → hóa đơn ghi sổ trong một transaction; cần `sales-quick-sale:execute` và quyền từng bước; đơn phải duyệt thì dừng (trả trạng thái và thông báo) |
| `GET /sales/reports/summary?companyId=&branchId=&dateFrom=&dateTo=&groupBy=` | Doanh số theo `SALES_USER` / `CUSTOMER` / `PRODUCT` / `BRANCH`: doanh thu, giảm trừ, thuần, thuế, phải thu còn lại; `GET /sales/reports/sources?...&key=` chứng từ cấu thành. Quyền `sales-report:view` |

### M06 - Đấu thầu
| Tài nguyên | Ghi chú |
|---|---|
| `/tender-packages` [CT nhóm B] | `PUT /{id}/items`, `PUT /{id}/bid-documents`, `POST /{id}/mark-submitted`, `POST /{id}/record-result` |
| `/tender-results` [CT] | `POST /{id}/create-contract` |
| `/contracts` [CT nhóm B] | `GET /{id}/progress` (trúng / đặt / giao / còn lại / nghiệm thu / HĐ / thu), `POST /{id}/allocate` |
| `/contracts/{id}/amendments` [CT] | Phụ lục |
| `/contracts/{id}/delivery-plans` | Kế hoạch giao |
| `/contract-acceptances` [CT nhóm A] | Nghiệm thu |
| `GET /contracts/{id}/reconciliation` | Đối soát |
| `GET /contracts/expiring?days=` | Sắp hết hạn |

### M07 - Công nợ, thu chi
| Tài nguyên | Ghi chú |
|---|---|
| `GET /debt-items?companyId=&debtType=RECEIVABLE/PAYABLE&branchId=&partnerId=&keyword=&openOnly=&overdueOnly=` | Khoản công nợ theo hóa đơn (4B), kèm số ngày quá hạn; thay `/receivables`, `/payables` dự kiến (ADR-12). Quyền `debt:view` |
| `GET /debt-items/aging?companyId=&debtType=&branchId=&partnerId=&asOf=&page=&size=` | Tuổi nợ theo đối tượng (4B): `rows` phân trang + `totals`; mốc từ tham số `finance.aging-bucket-*-days` |
| `GET /debt-ledger?companyId=&debtType=&partnerId=&dateFrom=&dateTo=` | Sổ chi tiết công nợ một đối tượng: số dư đầu, từng dòng kèm số dư lũy kế, số dư cuối (4B) |
| `/debt-openings` [CT nhóm A]: `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/submit`, `approve`, `reject`, `cancel`, `post`, `reverse` | Công nợ đầu kỳ (4B). Hóa đơn đã có công nợ: `400` tại `lines[i].invoiceNo`. Đảo (`reason`, `postingDate`) chỉ người có `debt-opening:approve`; khoản đã thanh toán: `409 HAS_DEPENDENT_DOCUMENTS`. Không sinh bút toán `[Giả định]` |
| `/cash-accounts`: `GET ?companyId=&branchId=&accountType=&status=&keyword=`, `GET /{id}`, `POST`, `PUT /{id}`, `PATCH /{id}/status` | Quỹ tiền mặt, tài khoản ngân hàng (4C). `balance` chỉ đọc; tài khoản kế toán phải là tài khoản chi tiết đang hoạt động; mã trùng `409 DUPLICATE_CODE`; ngừng quỹ còn số dư `409 DATA_IN_USE`. Quyền `cash-account:view` / `manage` |
| `/cash-documents` [CT nhóm A]: `GET ?companyId=&branchId=&documentType=&purpose=&status=&cashAccountId=&partnerId=&dateFrom=&dateTo=&keyword=`, `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/submit`, `approve`, `reject`, `cancel`, `post`, `reverse` | Phiếu thu (PT), phiếu chi (PC), giấy báo có (BC), ủy nhiệm chi (UNC), chuyển tiền nội bộ (CTN) (4C, ADR-13). `purpose`: `CUSTOMER_RECEIPT`, `SUPPLIER_PAYMENT` (bắt buộc `partnerId`, `amount`, `allocationMode` `AUTO` = hóa đơn đến hạn sớm nhất trước lúc ghi sổ / `MANUAL` = dòng `debtItemId`; phần dư là trả trước), `OTHER_RECEIPT`, `OTHER_PAYMENT` (dòng `accountId`, `costItemId`), `INTERNAL_TRANSFER` (`targetCashAccountId`, `amount`). Gửi duyệt: PC, UNC, CTN từ ngưỡng `finance.payment-approval-threshold` trở lên chờ duyệt (chưa có luồng: `422 APPROVAL_NOT_CONFIGURED`), còn lại tự duyệt. Ghi sổ: `422 CASH_BALANCE_NEGATIVE`, `OVER_ALLOCATION`, `PERIOD_LOCKED`, `POSTING_RULE_NOT_FOUND`. Đảo (`reason`, `postingDate`) quyền `cash-document:reverse`. Chi tiết trả `lines`, `allocations` (phân bổ đã ghi sổ), `approvals` |
| `GET /cash-book?cashAccountId=&dateFrom=&dateTo=` | Sổ quỹ / sổ tiền gửi (4C): số dư đầu, từng dòng thu / chi kèm chứng từ nguồn và số dư lũy kế, số dư cuối; tối đa 366 ngày. Quyền `cash-book:view` |
| `GET /cash-flow?companyId=&branchId=&dateFrom=&dateTo=` | Dòng tiền thực tế (4C): theo quỹ (đầu kỳ, thu, chi, cuối kỳ) và theo nội dung thu chi |
| `POST /cash-documents/batch` | Thu hàng loạt (BR-AR-08) - chưa làm |
| `GET /debt-prepayments?companyId=&debtType=&partnerId=` | Tiền trả trước còn chưa bù trừ theo chi nhánh trong phạm vi (4D) |
| `/debt-offsets` [CT nhóm A]: `GET ?companyId=&branchId=&offsetType=&partnerId=&status=&dateFrom=&dateTo=&keyword=`, `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/submit`, `approve`, `reject`, `cancel`, `post`, `reverse` | Bù trừ công nợ (DTCN, 4D, BR-AR-04). `offsetType` `PREPAYMENT` (+ `debtType`): dòng hóa đơn cùng loại, tổng ≤ trả trước còn; `AR_AP`: đối tượng vừa là khách vừa là nhà cung cấp, tổng dòng phải thu = tổng dòng phải trả. Ghi sổ: `422 OVER_ALLOCATION` khi trả trước / số còn lại không đủ (kiểm tra sau khi khóa). Đảo = bỏ bù trừ (`debt-offset:reverse`) |
| `/debt-adjustments` [CT nhóm A], cùng bộ hành động | Điều chỉnh công nợ `documentType=DCCN` (bắt buộc duyệt, BR-AR-07) và thanh toán tạm ứng `TTTU` (BR-CSH-04). Dòng `direction` `DECREASE` (bắt buộc `debtItemId`) / `INCREASE` (`invoiceNo`, `dueDate` tùy chọn), `accountId` đối ứng bắt buộc; `reconciliationId` tùy chọn. Đảo khi khoản tăng đã được thanh toán: `409 HAS_DEPENDENT_DOCUMENTS` |
| `GET /debt-statements?companyId=&branchId=&debtType=&partnerId=&dateFrom=&dateTo=` | Bảng đối chiếu công nợ (BR-AR-06, 4D): số dư đầu, phát sinh theo chứng từ, số dư cuối, hóa đơn còn nợ, trả trước; quyền `debt-reconciliation:view`. In ở giao diện; gửi email chưa làm |
| `/debt-reconciliations`: `GET`, `GET /{id}`, `POST`, `POST /{id}/confirm` (`confirmedBalance`, `contactName`, `note`), `POST /{id}/cancel` (`reason`) | Biên bản đối chiếu: chốt số dư hệ thống tại `asOfDate`; `branchId` trống (toàn công ty) cần phạm vi toàn công ty, không thì `403`. Xác nhận → `MATCHED` / `DIFFERENT` |
| `/cash-counts` [CT nhóm A], cùng bộ hành động | Kiểm kê quỹ tiền mặt (KKQ, 4D, BR-CSH-05, bắt buộc duyệt): `cashAccountId` (quỹ tiền mặt), `documentDate` (ngày kiểm kê), `countedAmount`. Ghi sổ chốt `bookBalance`, `differenceAmount`, sinh sổ quỹ và bút toán chênh lệch |
| `/cash-documents` `purpose` `EMPLOYEE_ADVANCE` (PC, UNC) / `ADVANCE_RETURN` (PT, BC) | Chi tạm ứng nhân viên (bắt buộc `partnerId` vai trò nhân viên, `amount`) sinh khoản công nợ tạm ứng; hoàn ứng phân bổ như thu tiền khách (4D) |
| `GET /cash-flow/forecast?from=&to=` | Dòng tiền dự kiến (Giai đoạn 10) |

### M08 - Kế toán, giá vốn
| Tài nguyên | Ghi chú |
|---|---|
| `GET /accounts?companyId=&keyword=&status=&postable=`, `GET /accounts/tree?companyId=`, `POST /accounts`, `PUT /accounts/{id}`, `PATCH /accounts/{id}/status` | Hệ thống tài khoản (4A). `tree` trả toàn bộ tài khoản công ty theo mã (không phân trang, số tài khoản có giới hạn). Thêm tài khoản con dưới tài khoản đã hạch toán: `409 DATA_IN_USE`. Ngừng tài khoản còn con hoạt động / còn quy tắc dùng: `409 DATA_IN_USE`. Quyền `account:view/manage` |
| `GET /accounts/import-template`, `POST /accounts/import?companyId=` (multipart `file`) | Nhập hệ thống tài khoản từ Excel: cột Mã TK, Tên TK, Mã TK cha, Tính chất (NO/CO/LT), Theo dõi đối tượng (C/K); tất cả hoặc không, lỗi `rows[<dòng>].<cột>` |
| `GET /posting-rules?companyId=&documentType=&transactionCode=&status=`, `POST /posting-rules`, `PUT /posting-rules/{id}`, `PATCH /posting-rules/{id}/status` | Quy tắc hạch toán (4A, ADR-11). Sửa chỉ đổi tài khoản, ngày hết hiệu lực, diễn giải; trùng điều kiện + ngày bắt đầu: `409 DUPLICATE_CODE`. Quyền `posting-rule:view/manage` |
| `GET /journal-entries?companyId=&branchId=&dateFrom=&dateTo=&sourceType=&keyword=&accountId=&status=`, `GET /journal-entries/{id}`, `GET /journal-entries/by-source?companyId=&sourceType=&sourceId=` | Bút toán chỉ đọc (4A), lọc theo phạm vi chi nhánh. Chi tiết gồm dòng và bút toán đảo (`reversedBy`). Quyền `journal:view`. Bút toán tổng hợp nhập tay: P3 (BR-ACC-09) |
| `POST /cost-calculations`, `GET /cost-calculations/{id}` | Tính giá vốn theo kỳ/kho |
| `GET /accounting/reconciliation?periodId=` | Đối chiếu kho, công nợ, quỹ với tài khoản |
| `GET /accounting/exports/misa?from=&to=` | Xuất dữ liệu sang MISA |

### M09 - Thiết bị
`/equipment` (theo serial), `GET /equipment/{serialId}/warranty`, `/equipment-handovers` [CT], `/equipment-exchanges` [CT], `/service-tickets` [CT, P2], `GET /equipment?customerId=&contractId=&salesOrderId=`.

### M10 - Báo cáo, dashboard
| Tài nguyên | Ghi chú |
|---|---|
| `GET /dashboard/summary?from=&to=&branchId=` | Mọi chỉ tiêu BR-RPT-01..10 |
| `GET /dashboard/{metric}/details` | Danh sách chi tiết và chứng từ nguồn của chỉ tiêu (BR-RPT-11) |
| `GET /reports/{reportCode}?...` | Báo cáo BR-RPT-20..33 |
| `GET /reports/{reportCode}/export?format=xlsx\|pdf` | Xuất file |

## 3. Ví dụ: ghi sổ phiếu xuất kho
```
POST /api/v1/inventory-issues/1024/post
Authorization: Bearer eyJ...
Idempotency-Key: 4b1e0c7a-8a43-4f0e-9a0c-2f3f6a0f8c11
```
Thành công `200`:
```json
{
  "success": true,
  "data": { "id": 1024, "documentNo": "PX-HN-2026-000123", "status": "POSTED", "postedAt": "2026-10-09T03:15:22.120Z" },
  "timestamp": "..."
}
```
Thiếu tồn `422`:
```json
{ "success": false, "code": "INSUFFICIENT_STOCK", "message": "Tồn khả dụng của lô L2401 không đủ (còn 5, cần 8).", "requestId": "..." }
```
Gửi lại cùng `Idempotency-Key`: trả lại đúng kết quả `200` lần đầu, không ghi sổ lần hai.

## 4. Tương thích
- Thêm trường mới: được phép.
- Đổi tên, xóa trường, đổi ý nghĩa: phải tăng phiên bản hoặc có giai đoạn chuyển tiếp được duyệt.
- Mọi thay đổi cập nhật OpenAPI và tài liệu này.
