# Đặc tả API REST

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 (DRAFT - hợp đồng API chi tiết chốt khi làm từng module) |
| Ngày cập nhật | 2026-10-14 |
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
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `OCR_FILE_INVALID`, `ASSISTANT_MESSAGE_TOO_LONG` | Dữ liệu không hợp lệ / JSON, tham số, header sai định dạng / file nhận dạng hỏng, PDF quá số trang, ảnh quá số điểm ảnh (D-64) / câu hỏi trợ lý vượt `ai.chat.max-message-chars` (AI-3) |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED` | Chưa đăng nhập / access token hết hạn (frontend gọi `/auth/refresh` rồi gửi lại) / đăng nhập sai hoặc tài khoản bị khóa / phiên hết hạn, phải đăng nhập lại |
| 403 | `FORBIDDEN`, `PASSWORD_CHANGE_REQUIRED`, `LICENSE_INACTIVE` | Không đủ quyền / phải đổi mật khẩu trước khi dùng API khác / bản quyền không hiệu lực: hệ thống chỉ đọc (D-59) |
| 404 | `NOT_FOUND` | Không tồn tại hoặc ngoài phạm vi dữ liệu |
| 405 | `METHOD_NOT_ALLOWED` | Phương thức HTTP không hỗ trợ |
| 409 | `DUPLICATE_CODE`, `DUPLICATE_DOCUMENT`, `INVALID_DOCUMENT_STATE`, `CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS`, `HAS_DEPENDENT_DOCUMENTS`, `DATA_INTEGRITY_VIOLATION`, `INVALID_PERIOD_STATE`, `CONFIGURATION_IN_EFFECT`, `DATA_IN_USE` | Xung đột. `DATA_IN_USE`: danh mục đã được tham chiếu, không xóa / đổi được. `HAS_DEPENDENT_DOCUMENTS`: đảo chứng từ khi hàng đã được chứng từ khác xuất (thông báo liệt kê số chứng từ cần đảo trước). `REQUEST_IN_PROGRESS`: yêu cầu cùng `Idempotency-Key` đang xử lý song song. `DATA_INTEGRITY_VIOLATION`: ràng buộc DB chặn (lưới an toàn khi kiểm tra nghiệp vụ bỏ sót). `INVALID_PERIOD_STATE`: khóa kỳ đã khóa / mở kỳ đang mở. `CONFIGURATION_IN_EFFECT`: xóa cấu hình số chứng từ đã có hiệu lực |
| 422 | `INSUFFICIENT_STOCK`, `LOT_EXPIRED`, `LOT_NOT_SELLABLE`, `SERIAL_UNAVAILABLE`, `PERIOD_LOCKED`, `PERIOD_NOT_DEFINED`, `PERIOD_ORDER_VIOLATION`, `PERIOD_CLOSE_PRECONDITION`, `PARAMETER_LOCKED`, `CONTRACT_QUANTITY_EXCEEDED`, `CREDIT_LIMIT_EXCEEDED`, `PRICE_BELOW_FLOOR`, `OVER_ALLOCATION`, `SOURCE_QUANTITY_EXCEEDED`, `CASH_BALANCE_NEGATIVE`, `STOCK_COUNT_IN_PROGRESS`, `AUTHORITY_LIMIT_EXCEEDED`, `POSTING_RULE_NOT_FOUND`, `POSTING_RULE_INCOMPLETE`, `ACCOUNT_NOT_POSTABLE`, `UNBALANCED_JOURNAL` | Vi phạm quy tắc nghiệp vụ. `POSTING_RULE_NOT_FOUND`: chưa có quy tắc hạch toán còn hiệu lực cho nghiệp vụ. `POSTING_RULE_INCOMPLETE`: quy tắc để trống một vế mà chứng từ không truyền tài khoản. `CASH_BALANCE_NEGATIVE`: quỹ tiền mặt không đủ khi chi hoặc khi đảo phiếu thu. `OVER_ALLOCATION`: phân bổ vượt số còn lại của hóa đơn (kiểm tra sau khi khóa). `SOURCE_QUANTITY_EXCEEDED`: chứng từ đích vượt số còn lại của chứng từ nguồn (đơn mua vượt đề nghị mua). `ACCOUNT_NOT_POSTABLE`: tài khoản ngừng / tổng hợp, hoặc bắt buộc đối tượng mà thiếu. `UNBALANCED_JOURNAL`: Nợ ≠ Có. `STOCK_COUNT_IN_PROGRESS`: kho đang kiểm kê (D-11). `AUTHORITY_LIMIT_EXCEEDED`: chứng từ từ ngưỡng giá trị trở lên mà người ghi sổ thiếu quyền ghi sổ vượt ngưỡng (D-61). `PRICE_BELOW_FLOOR` / `CREDIT_LIMIT_EXCEEDED` khi ghi sổ đơn bán: thiếu quyền `sales-order:override-price` / `sales-order:override-credit` (D-61). Đã bỏ `SELF_APPROVAL_NOT_ALLOWED`, `APPROVAL_NOT_CONFIGURED` (D-61). `LOT_EXPIRED` / `LOT_NOT_SELLABLE`: lô hết hạn / không ĐẠT hoặc bị khóa (BR-INV-04, BR-INV-23). `PERIOD_NOT_DEFINED`: ngày hạch toán chưa có kỳ. `PERIOD_ORDER_VIOLATION`: khóa/mở sai thứ tự tháng hoặc mở kỳ kho khi kỳ kế toán còn khóa. `PERIOD_CLOSE_PRECONDITION`: chưa đủ điều kiện khóa (BR-SYS-31). `PARAMETER_LOCKED`: tham số đã khóa vì đã phát sinh số liệu ghi sổ |
| 413 | `OCR_FILE_TOO_LARGE` | File nhận dạng vượt `ai.ocr.max-file-size` (D-64) |
| 415 | `OCR_UNSUPPORTED_TYPE` | Loại file thật (chữ ký byte đầu file) không phải JPG / PNG / PDF (D-64) |
| 422 | `OCR_NO_TEXT` | Không nhận dạng được chữ trên tài liệu (D-64) |
| 404 | `TAX_CODE_NOT_FOUND` | Tra cứu mã số thuế không thấy doanh nghiệp (D-66) |
| 423 | `LOCK_TIMEOUT` | Dữ liệu đang được xử lý, thử lại |
| 429 | `TOO_MANY_REQUESTS`, `AI_BUSY` | Đăng nhập sai quá nhiều lần từ một IP (`erp.auth.ip-rate-limit`) / nguồn tra cứu mã số thuế báo vượt tần suất (D-66) / hết suất xử lý AI đồng thời, thử lại sau (D-64) |
| 500 | `INTERNAL_ERROR` | Lỗi hệ thống |
| 503 | `AI_SERVICE_UNAVAILABLE` | AI Service ngừng hoặc `ai.enabled=false`; nhập tay vẫn bình thường (D-64, BR-AI-09) |
| 503 | `TAX_CODE_LOOKUP_UNAVAILABLE` | Tra cứu mã số thuế tắt (`erp.tax-code-lookup.enabled=false`), không kết nối được, sai `client-id` / `api-key` (401 / 403 từ nguồn) hoặc nguồn trả lỗi / dữ liệu không đọc được (D-66) |
| 504 | `AI_TIMEOUT` | AI Service xử lý quá `ai.ocr.timeout` / `ai.chat.timeout` (D-64) |
| 504 | `TAX_CODE_LOOKUP_TIMEOUT` | Nguồn tra cứu mã số thuế phản hồi quá `erp.tax-code-lookup.read-timeout` (D-66) |

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
| PUT | `/{docs}/{id}` | Sửa (DRAFT, gửi `version`) | `<doc>:update` |
| DELETE | `/{docs}/{id}` | Xóa DRAFT chưa từng gửi duyệt | `<doc>:delete` |
| POST | `/{docs}/{id}/post` | Ghi sổ (nhóm A) từ DRAFT (hoặc APPROVED lập trước D-61) | `<doc>:post` |
| POST | `/{docs}/{id}/cancel` | Hủy chưa ghi sổ (`reason`) | `<doc>:cancel` |
| POST | `/{docs}/{id}/reverse` | Đảo chứng từ đã ghi sổ (`reason`, `reversalDate`) | `<doc>:reverse` |
| POST | `/{docs}/{id}/unpost` | Bỏ ghi sổ về DRAFT giữ số (`reason`; chỉ `/purchase-orders`, `/sales-orders`, D-62): dòng ngược ngày hạch toán gốc | `<doc>:unpost` |
| POST | `/{docs}/{id}/close` | Đóng phần còn lại (nhóm B, `reason`) | `<doc>:close` |
| GET | `/{docs}/{id}/related-documents` | Chứng từ nguồn và đích | `<doc>:view` |
| GET | `/{docs}/{id}/history` | Lịch sử xử lý | `<doc>:view` |
| GET | `/{docs}/{id}/print` | In / PDF | `<doc>:print` |
| GET | `/{docs}/export` | Xuất Excel theo bộ lọc | `<doc>:export` |

Mẫu danh mục: `POST`, `GET`, `GET /{id}`, `PUT /{id}`, `PATCH /{id}/status`, `POST /import` (Excel, kiểm tra toàn bộ trước khi ghi).

**Không có** endpoint sửa trực tiếp số dư tồn kho, công nợ, quỹ.

**D-61 (2026-10-21, thay đổi vỡ tương thích đã được người dùng duyệt):** bỏ bước duyệt trên mọi chứng từ. Đã gỡ `POST /{docs}/{id}/submit|approve|reject` của `cash-documents`, `cash-counts`, `debt-offsets`, `debt-adjustments`, `purchase-orders`, `purchase-returns`, `purchase-cost-allocations`, `sales-orders`, `sales-returns`, `inventory-documents`, `stock-counts`, `recalls`, `stock-issue-policies` và các API `/approvals`, `/approval-workflows`, `/approval-delegations`, `PUT /document-types/{code}` (gọi tới: 404). `post` nhận DRAFT (hoặc APPROVED lập trước D-61); chứng từ SUBMITTED / REJECTED cũ chỉ còn `cancel`. Chi tiết chứng từ vẫn trả `approvals` (lịch sử duyệt cũ, chỉ đọc; rỗng với chứng từ mới).

**D-65 (2026-10-29, thay đổi vỡ tương thích đã được người dùng duyệt):** gỡ `POST /purchase-orders/{id}/cancel`, `POST /sales-orders/{id}/cancel` (gọi tới: 404 / 405), thay bằng `DELETE /purchase-orders/{id}`, `DELETE /sales-orders/{id}` (body `reason`, header `Idempotency-Key`, trả `{id, documentNo}`). Đơn mua / bán không còn trạng thái `CANCELLED`; báo cáo tiến độ đơn không nhận lọc `status=CANCELLED` (400). Chứng từ khác giữ `cancel`.

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
| `GET /document-types` (D-61: chỉ đọc; bỏ `/approval-workflows`, `PUT /document-types/{code}`, response bỏ `approvalMode`, `selfApprovalAllowed`) | **Đã triển khai (1D), thu gọn theo D-61.** Trước D-61: Luồng duyệt theo loại chứng từ (chi nhánh, ngưỡng giá trị / chiết khấu, ưu tiên, các bước ROLE/USER); chế độ duyệt từng loại (D-52) |
| ~~`GET /approvals/pending`, `GET /approvals/{id}`~~ | **Đã gỡ (D-61).** Trước D-61: Việc chờ duyệt của tôi (gồm phần được ủy quyền). Hành động duyệt / từ chối nằm ở endpoint của chứng từ (`POST /{resource}/{id}/approve|reject`) |
| ~~`/approval-delegations`, `POST /approval-delegations/{id}/revoke`~~ | **Đã gỡ (D-61).** Trước D-61: Ủy quyền duyệt có thời hạn |
| `GET /users/lookup?companyId=&keyword=` | **Đã triển khai (1D).** Tìm người dùng (trước D-61 dùng chọn người duyệt / người được ủy quyền) |
| `GET /fiscal-periods?companyId=&year=&branchId=`, `POST /fiscal-periods/generate`, `POST /fiscal-periods/{id}/lock`, `POST /fiscal-periods/{id}/unlock` | **Đã triển khai (1C).** Danh sách: tối đa 12 kỳ kèm trạng thái khóa 4 phân hệ của một chi nhánh (không phân trang). `generate` (`companyId, year`): tạo 12 kỳ, cần phạm vi toàn công ty. `lock`/`unlock` (`module, branchIds[], reason`): mọi chi nhánh cùng thành công hoặc không; `unlock` bắt buộc `reason`, quyền `period:unlock`. Thứ tự: kỳ trước phải khóa trước; kỳ kế toán khóa sau kỳ kho; mở theo thứ tự ngược |
| `GET /parameters?companyId=`, `PUT /parameters/{key}` | **Đã triển khai (1C).** `PUT` (`companyId, value, version`): `version` = 0 khi chưa có giá trị riêng; sai kiểu/giới hạn: 400 trên trường `value`; cần phạm vi toàn công ty |
| `GET /license`, `PUT /license` (2026-10-17) | Bản quyền phần mềm (D-59). `GET`: mọi người đã đăng nhập; trả `state` (`VALID, GRACE, EXPIRED, MISSING, INVALID, CLOCK_TAMPERED, COMPANY_NOT_LICENSED`), `writable`, `message`, `licenseId, customerName, taxCodes, validTo, graceUntil, daysRemaining, issuedAt, installedAt, unlicensedCompanies`. `PUT` (quyền `license:manage`, body `license`: nguyên văn license): thay license đang dùng; sai định dạng / chữ ký / khóa không tin cậy / đã hết ân hạn / ngày cấp sau giờ máy chủ: 400 tại `license`; ghi audit `SOFTWARE_LICENSE`. Khi `writable = false`, mọi request ghi khác (trừ `/auth/*`, `PUT /license`, `PATCH /companies/{id}/status`) trả 403 `LICENSE_INACTIVE` |
| `GET /audit-logs`, `GET /login-logs` | Chỉ đọc. `GET /login-logs` **đã triển khai (1A)**: lọc `username, event, from, to` |
| `/attachments` (`POST` multipart, `GET /{id}/download`) | Giới hạn loại và dung lượng (D-41) |
| `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` | **Đã triển khai (1D).** Chỉ trong ứng dụng (D-49) |

### M02 - Danh mục
`/product-categories`, `/products`, `/units`, `/products/{id}/units`, `/brands`, `/manufacturers`, `/countries`, `/partners` (lọc theo `role`), `/partners/{id}/contacts`, `/partners/{id}/addresses`, `/warehouses`, `/warehouses/{id}/locations`, `/tax-rates`, `/price-lists`, `/currencies`, `/exchange-rates`, `/payment-terms`, `/payment-methods`, `/delivery-methods`, `/contract-types`, `/reasons`, `/cost-items`, `/compliance-document-types`, `/compliance-documents` — tất cả [DM].

`GET /compliance-documents/expiring?days=30` — giấy tờ sắp hết hạn.

**Đã triển khai (Giai đoạn 2):** `/units`, `/brands`, `/manufacturers`, `/partner-groups`, `/countries`, `/reference-items` (thay `/reasons`, `/payment-terms`... bằng một danh mục có `category`), `/tax-rates`, `/product-categories`, `/products` (+ `POST /{id}/units`, `PUT /{id}/stock-levels`, `GET /import-template`, `POST /import`), `/partners` (+ `POST /partners/import`; vai trò `SUPPLIER` bắt buộc `taxCode`, `address`: thiếu thì 400), `/warehouses` (+ `/{id}/locations`, `/warehouse-locations/{id}`), `/price-lists` (+ `PUT /{id}/items`), `/compliance-document-types`, `/compliance-documents`. Danh mục ngừng hoạt động bằng `PATCH /{id}/status` (lý do, `version`). Giá mua tham khảo ẩn khi thiếu `purchase-price:view`. Chưa làm: `/currencies`, `/exchange-rates` (dời tới module dùng ngoại tệ).

**Nhóm hàng (D-67):** `PUT /product-categories/{id}` nhận thêm `code` (tùy chọn; trống thì giữ mã cũ; trùng: 409 `DUPLICATE_CODE`). `PATCH /product-categories/{id}/status` với `INACTIVE`: nhóm chưa có hàng và nhóm con bị xóa hẳn, phản hồi `deleted: true` (thêm trường `deleted` vào `ProductCategoryResponse`, mặc định `false`).

**Tra cứu mã số thuế (D-66):** `GET /partners/tax-code-lookup?taxCode=0311315789` (quyền `partner:manage`; chỉ đọc, không cần Idempotency-Key). Trả `taxCode, name, internationalName, shortName, address, phone, status, source` (trường nguồn không có: `null`; nguồn Xinvoice không có `phone`, `internationalName`, `shortName`). Mã sai định dạng (10 số hoặc 10 số-3 số): 400 tại `taxCode`; không thấy: 404 `TAX_CODE_NOT_FOUND`; nguồn báo vượt tần suất: 429 `TOO_MANY_REQUESTS`; tắt / lỗi nguồn: 503 `TAX_CODE_LOOKUP_UNAVAILABLE`; quá giờ: 504 `TAX_CODE_LOOKUP_TIMEOUT`.

### M03 - Mua hàng
D-54 (V22, 2026-10-12): bỏ `/purchase-requisitions`, `/purchase-receipts` (kể cả `receive-and-stock`), `/purchase-invoices`, hành động `close` của đơn mua; yêu cầu / so sánh báo giá NCC không làm. Đơn mua là chứng từ ghi sổ duy nhất.

| Tài nguyên | Ghi chú |
|---|---|
| `/purchase-orders` [CT nhóm A] | CRUD (lọc `supplierId`, `warehouseId`, `status`, ngày; request: `postingDate`, hóa đơn NCC `invoiceSeries, invoiceNo, invoiceDate` (đủ ba hoặc trống; MST người bán lấy theo nhà cung cấp, bỏ `sellerTaxCode` 2026-10-13), `dueDate`, dòng có `lotNo, manufactureDate, expiryDate, locationId, serialNumbers`), `DELETE /purchase-orders/{id}` (D-65, thay `cancel` đã gỡ: header `Idempotency-Key`, body `reason`; xóa hẳn đơn chưa từng ghi sổ, đơn đã duyệt trước D-61 trừ hàng đang về; trả `{id, documentNo}`; POSTED / REVERSED / nháp đã từng ghi sổ: 409 `INVALID_DOCUMENT_STATE`; quyền `purchase-order:create`), `post` (từ DRAFT; tổng tiền từ ngưỡng `purchasing.order-approval-threshold` mà thiếu quyền `purchase-order:post-over-threshold`: 422 `AUTHORITY_LIMIT_EXCEEDED`; phiếu nhập kho PNK nguồn `DMH`, trừ hàng đang về của đơn đã duyệt trước D-61, công nợ phải trả, bút toán; hóa đơn trùng: 409 `DUPLICATE_DOCUMENT`), `reverse` (`reason`, `postingDate`; còn trả hàng / giảm giá / phân bổ chưa đảo, hàng đã xuất, đã thanh toán: 409 `HAS_DEPENDENT_DOCUMENTS`). Response có `inventoryDocumentNo`, `supplierTaxCode`, `supplierAddress` (MST, địa chỉ hiện tại của nhà cung cấp, chỉ đọc), dòng có `returnedQuantity, adjustedAmount, allocatedAmount, returnableQuantity, remainingAmount`. `GET /purchase-orders/{id}/stock-in-slip`: dữ liệu in phiếu nhập kho của đơn đã ghi sổ (công ty, số phiếu PNK, ngày hạch toán, tài khoản Nợ / Có phần hàng hóa, người giao, hóa đơn, kho, dòng hàng hóa có lô / hạn, đơn giá và thành tiền sau chiết khấu trước thuế, cộng tiền hàng, thuế suất `taxRates`, tiền thuế `taxAmount`, tổng thanh toán `paymentAmount` của dòng hàng hóa); chưa ghi sổ / đã đảo: 409 `INVALID_DOCUMENT_STATE`; quyền `purchase-order:view`. Tham số tùy chọn `companyId` (D-58): tên / địa chỉ đầu phiếu theo công ty này (đang hoạt động, trong phạm vi người dùng; sai: 400 tại `companyId`), khác công ty của đơn thì ghi nhật ký `PRINT_AS_COMPANY`. `GET /purchase-orders/print-companies`: công ty đang hoạt động trong phạm vi người dùng `id, code, name, address` để chọn khi in; quyền `purchase-order:view`. Quyền `purchase-order:view|create|post|post-over-threshold|reverse`; `unpost` (D-62, BR-PUR-20: `reason`; đảo phiếu nhập, công nợ, bút toán theo ngày hạch toán gốc rồi về DRAFT giữ số; quyền `purchase-order:unpost`; 409 `HAS_DEPENDENT_DOCUMENTS` như đảo, 422 `PERIOD_LOCKED` khi kỳ gốc đã khóa) |
| `GET /purchase-orders/line-import-template`, `POST /purchase-orders/line-import` (2026-10-17) | Nhập dòng hàng đơn mua từ Excel vào form (BR-PUR-04, BR-UX-05). Quyền `purchase-order:create`. `POST` multipart `file` (.xlsx), tham số `companyId`, `documentDate` (tùy chọn, ngày kiểm tra hệ số quy đổi / thuế suất; trống = hôm nay); chỉ đọc và kiểm tra (mã hàng, ĐVT, thuế suất, lô / hạn dùng theo cấu hình hàng, tối đa 200 dòng), **không lưu** nên không cần `Idempotency-Key`; trả `{ created, lines[] }` (`productId, productCode, productName, serviceLine, unitId, quantity, unitPrice, discountPercent, taxRateId` (trống = mặc định của hàng), `lotNo, manufactureDate, expiryDate, serialNumbers, note`). Lỗi: `400` theo dòng `rows[<dòng Excel>].<trường>` |
| `/purchase-returns` [CT nhóm A] | Trả hàng NCC (`documentType=THN`) và giảm giá hàng mua (`GGM`) theo `purchaseOrderId` (đơn đã ghi sổ): CRUD (dòng `orderLineId`, THN: `quantity, qualityStatus, serialNumbers`; GGM: `amount`), `cancel`, `post` (THN: phiếu xuất trả PXK đúng lô / serial; giảm công nợ phải trả, phần vượt thành trả trước; bút toán), `reverse`. `GET /purchase-returns/returnable-lines?branchId=&supplierId=&orderId=`. Vượt số còn trả / giá trị còn lại: 422 `SOURCE_QUANTITY_EXCEEDED`. Quyền `purchase-return:*` |
| `/purchase-cost-allocations` [CT nhóm A] | Phân bổ chi phí mua: CRUD (`sources[]: orderLineId` dòng dịch vụ của đơn mua đã ghi sổ, `amount`; `orderIds[]` đơn mua đã ghi sổ nhận phân bổ; `method` VALUE / QUANTITY), `cancel`, `post`, `reverse`. `GET /purchase-cost-allocations/cost-lines?branchId=` (dòng dịch vụ còn phân bổ được). Quyền `purchase-cost-allocation:*` |
| `GET /purchasing/reports/detail-ledger?companyId=&branchId=&dateFrom=&dateTo=&purchaserId=&supplierGroupId=&productCategoryId=&productIds=&supplierIds=` | Sổ chi tiết mua hàng (hiện trạng HienTrang 14-15, 2026-10-14). Dòng đơn mua (`purchaseQuantity`, `unitPrice`, `purchaseAmount` = số lượng × đơn giá, `discountAmount`), trả hàng THN (`returnQuantity`, `returnAmount`), giảm giá GGM (`priceReductionAmount`) đã ghi sổ theo ngày hạch toán; chứng từ đã đảo: dòng gốc + dòng âm theo ngày đảo (`reversal = true`); `total` có `lineCount`. `purchaserId`: người lập chứng từ (đơn mua chưa có trường nhân viên mua hàng). `productIds`, `supplierIds` dạng `1,2,3`, tối đa 200; `productCategoryId` gồm nhóm con. Không có cột thuế nhập khẩu, mã thống kê (chưa có dữ liệu). Lỗi điều kiện / phạm vi: 400 `VALIDATION_ERROR`. Quyền `purchase-ledger:view` |

### M04 - Kho
| Tài nguyên | Ghi chú |
|---|---|
**Đã triển khai (Giai đoạn 3).** So với dự kiến bản trước: bảy loại phiếu kho dùng chung tài nguyên `/inventory-documents` có `documentType` (ADR-10) thay cho `/inventory-receipts`, `/inventory-issues`, `/inventory-transfers`, `/inventory-adjustments`, `/quality-status-changes`; tên hành động kiểm kê đổi thành `start`, `submit`. Các endpoint dự kiến chưa từng phát hành nên không có client nào bị ảnh hưởng.

| Tài nguyên | Ghi chú |
|---|---|
| `/inventory-documents` [CT nhóm A] | Lọc `companyId` (bắt buộc), `documentType, subType, status, warehouseId, keyword, dateFrom, dateTo`; PNK nhận `subType` `OPENING` (tồn kho đầu kỳ, BR-MIG-02; dòng có thể ghi `qualityStatus` `QUARANTINE` cho lô tạm, D-13) hoặc `RECALL_IN`, loại khác trả `400` tại `subType`; sắp xếp `documentDate, postingDate, documentNo, createdAt, updatedAt`. Hành động: `cancel, post` (từ DRAFT, D-61), `receive` (nhận hàng chuyển khác chi nhánh, body `postingDate` tùy chọn), `reverse` (body `reason`, `postingDate`; trả chứng từ đảo PDK ở DRAFT, ghi sổ bằng `post`). Quyền theo loại: `<loại>:view|create|post` (docs/07 mục 2.3). Chi tiết trả `transactions` (sổ kho đã ghi), `approvals`, `allowedActions` |
| `GET /inventory-documents/opening-import-template`, `POST /inventory-documents/opening-import` (2026-10-17) | Nhập tồn kho đầu kỳ từ Excel (BR-MIG-02, BR-UX-05). Quyền `inventory-receipt:create`. `POST` multipart `file` (.xlsx), tham số `companyId`, `documentDate` (YYYY-MM-DD), header `Idempotency-Key`; mỗi kho trong file lập một PNK `OPENING` ở trạng thái `DRAFT` (kho trên 500 dòng tách nhiều phiếu); trả `{ created, documentIds, documentNos }`. Tất cả hoặc không: lỗi trả `400` theo dòng `rows[<dòng Excel>].<trường>` |
| `/stock-counts` [CT] | `POST /{id}/start` (chụp tồn, chặn nhập xuất kho), `PUT /{id}/counts` (`lines[]: lineId` hoặc dòng phát sinh `productId, locationId, lotId, serialId, qualityStatus`, `countedQuantity`), `post` (từ COUNTING khi đã đếm đủ, D-61; sinh PDC loại COUNT, mở khóa kho), `cancel` |
| `/recalls` [CT nhóm B] | `activate` (từ DRAFT, khóa lô / serial; quyền `recall:manage`, D-61), `cancel`, `close` (quyền `recall:manage`). Chi tiết gồm `customers` (đã giao, đã thu hồi theo khách) thay cho `/affected-customers` |
| `/stock-issue-policies` [CT] | `activate` (từ DRAFT, quyền `stock-policy:activate`, D-61; trạng thái `APPROVED` = có hiệu lực), `cancel`. Phiếu xuất / chuyển kho tham chiếu `issuePolicyId`; chính sách phải có hiệu lực và chưa dùng cho chứng từ khác (400 trên `issuePolicyId`; khi ghi sổ: 422 `INSUFFICIENT_STOCK`). Chính sách đã dùng không hủy được (409). Phản hồi có `usedByDocumentId` (D-53) |
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
D-54 (V23, 2026-10-12): bỏ `/sales-quotes`, `/sales-deliveries`, `/sales-invoices`, `POST /sales/quick-sale`, hành động `close`, `create-purchase-request` của đơn bán. Đơn bán là chứng từ ghi sổ duy nhất.

| Tài nguyên | Ghi chú |
|---|---|
| `GET /sales/price?customerId=&productId=&unitId=&quantity=&date=` | Giá đề xuất: bảng giá riêng của khách → nhóm khách / đại lý → niêm yết; kèm giá sàn, chiết khấu đại lý, thuế suất mặc định. Quyền `sales-order:view` |
| `/sales-orders` [CT nhóm A] | CRUD (`postingDate`, `lines` có `lotNo, lotOverrideReason, serialNumbers` chỉ định, `paymentTerms`, `reserveStock`; lọc `customerId`, `status`, ngày), lưu nháp ghi lý do cần thẩm quyền vào `approvalReasons` (giá dưới sàn / chiết khấu vượt `sales.discount-approval-percent`, vượt hạn mức, nợ quá hạn) và giữ hàng ngay nếu `reserveStock` (thiếu: 422 `INSUFFICIENT_STOCK`; sửa đơn nhả rồi giữ lại), `DELETE /sales-orders/{id}` (D-65, thay `cancel` đã gỡ: header `Idempotency-Key`, body `reason`; xóa hẳn đơn chưa từng ghi sổ, nhả hàng đang giữ; trả `{id, documentNo}`; POSTED / REVERSED / nháp đã từng ghi sổ: 409 `INVALID_DOCUMENT_STATE`; quyền `sales-order:create`), `reserve`, `release` (đơn nháp hoặc đã duyệt trước D-61), `post` (từ DRAFT; giá dưới sàn / chiết khấu vượt ngưỡng thiếu quyền `sales-order:override-price`: 422 `PRICE_BELOW_FLOOR`; vượt hạn mức / nợ quá hạn thiếu quyền `sales-order:override-credit`: 422 `CREDIT_LIMIT_EXCEEDED`; phiếu xuất PXK nguồn `DBH` FEFO / lô chỉ định, dùng giữ chỗ; lô hạn ngắn hơn yêu cầu của khách: 422 `LOT_NOT_SELLABLE`; công nợ phải thu, bút toán doanh thu), `einvoice-reference` (`series, number, date, taxAuthorityCode`; trùng: 409 `DUPLICATE_DOCUMENT`), `ship` (`shipperName`), `confirm` (`receivedDate, receivedBy, signedDocumentRef, note`), `reverse` (lập sai hoặc giao thất bại; còn trả hàng / giảm giá, đã thu: 409 `HAS_DEPENDENT_DOCUMENTS`). `GET /sales-orders/{id}/stock-out-slip`: dữ liệu in phiếu xuất kho của đơn đã ghi sổ (công ty, số phiếu PXK, ngày hạch toán, tài khoản Nợ / Có của bút toán doanh thu phần hàng hóa, người nhận, đơn / HĐĐT, kho, dòng hàng hóa có lô / hạn / serial thực xuất, giá bán sau chiết khấu trước thuế, cộng tiền hàng, `taxRates`, `taxAmount`, `paymentAmount` của dòng hàng hóa); chưa ghi sổ / đã đảo: 409 `INVALID_DOCUMENT_STATE`; tham số tùy chọn `companyId` và `GET /sales-orders/print-companies` như phiếu nhập kho của đơn mua (D-58), in theo công ty khác ghi nhật ký `PRINT_AS_COMPANY`; quyền `sales-order:view`. Response chi tiết: dòng có `items` (hàng thực xuất), xuất xứ, model, số lưu hành; `paymentSchedule`, `availabilityWarnings`. Quyền `sales-order:view|create|post|override-price|override-credit|deliver|reverse`; `unpost` (D-62, BR-SAL-25: `reason`; đảo phiếu xuất, doanh thu, công nợ, bút toán theo ngày hạch toán gốc rồi về DRAFT giữ số, không giữ hàng lại; quyền `sales-order:unpost`; đã chuyển đi giao / có số hóa đơn điện tử: 409 `INVALID_DOCUMENT_STATE`; còn trả hàng / giảm giá, có khiếu nại, đã thu tiền: 409 `HAS_DEPENDENT_DOCUMENTS`; kỳ gốc đã khóa: 422 `PERIOD_LOCKED`); `GET /sales-orders/receipt-sources?branchId=&warehouseId=&productId=` (D-63, BR-SAL-26, quyền `sales-order:create`): dòng nhập kho theo lô / serial còn tồn ĐẠT tại kho (FEFO, tối đa 500; hàng không quản lý lô / serial: rỗng); dòng đơn nhận `receiptTransactionId` (kiểm tra cùng hàng / kho, chưa đảo, lô / serial khớp; sai: 400 `VALIDATION_ERROR` tại `lines[i].receiptTransactionId` / `lotNo` / `serialNumbers`), phản hồi thêm `receiptTransactionId`, `receiptNo` |
| `GET /sales-orders/line-import-template`, `POST /sales-orders/line-import` (2026-10-17) | Nhập dòng hàng đơn bán từ Excel vào form (BR-SAL-01, BR-UX-05). Quyền `sales-order:create`. `POST` multipart `file` (.xlsx), tham số `companyId`, `documentDate` (tùy chọn); chỉ đọc và kiểm tra (mã hàng, ĐVT, thuế suất, hàng tặng C/K, tối đa 200 dòng), **không lưu** nên không cần `Idempotency-Key`; trả `{ created, lines[] }` (`productId, productCode, productName, unitId, quantity, unitPrice` (trống = bảng giá khi lưu), `discountPercent` (trống = chiết khấu đại lý), `taxRateId` (trống = mặc định của hàng), `promotional, expectedDate, lotNo` (trống = FEFO), `lotOverrideReason, serialNumbers, description`). Lỗi: `400` theo dòng `rows[<dòng Excel>].<trường>` |
| `/sales-returns` [CT nhóm A] | `documentType` TLB / GGB theo `salesOrderId` (đơn đã ghi sổ); CRUD (dòng `orderLineId`, TLB: `lotNo, quantity, qualityStatus` (trống: QUARANTINE), `serialNumbers`; GGB: `amount`), `cancel`, `post` (TLB: nhập lại kho đúng lô / serial; giảm doanh thu, công nợ), `reverse`, `create-exchange-order` (đơn bán nháp cùng hàng, giá; cần thêm `sales-order:create`). `GET /sales-returns/returnable-lines?branchId=&customerId=&orderId=`. Quyền `sales-return:*` |
| `/sales-complaints` | Hàng lỗi khách báo (`salesOrderId` tùy chọn): CRUD, `inspect` (`result`), `resolve` (`decision`, `note`, `salesReturnId`), `cancel`. Quyền `sales-complaint:view|create|resolve` |
| `GET /sales/reports/summary?companyId=&branchId=&dateFrom=&dateTo=&groupBy=` | Doanh số theo `SALES_USER` / `CUSTOMER` / `PRODUCT` / `BRANCH`: doanh thu (đơn bán đã ghi sổ), giảm trừ (trả hàng / giảm giá), thuế, phải thu còn lại; `GET /sales/reports/sources?...&key=` trả chứng từ cấu thành. Quyền `sales-report:view` |
| `GET /sales/reports/detail-ledger?companyId=&branchId=&dateFrom=&dateTo=&salesUserId=&customerGroupId=&productCategoryId=&productIds=&customerIds=&groupBy=` | Sổ chi tiết bán hàng mẫu S16-DNN (BR-RPT-26, 2026-10-14). `groupBy`: `NONE` (mặc định, chỉ theo hàng) / `CUSTOMER` / `PRODUCT_CATEGORY`; `productIds`, `customerIds` dạng `1,2,3`, tối đa 200 mỗi loại; `productCategoryId` gồm cả nhóm con. Nguồn: dòng đơn bán (doanh thu), trả lại / giảm giá (cột giảm trừ "Khác") đã ghi sổ theo ngày hạch toán; chứng từ đã đảo: dòng gốc + dòng âm theo ngày đảo (`reversal = true`). `offsetAccount`: TK Nợ của bút toán gốc (đơn bán) / TK Có (trả lại, giảm giá). Response: `groups[].products[].lines[]` + `totals` (`quantity, amount, taxDeduction, otherDeduction, netRevenue, costOfGoodsSold, grossProfit`), `total`, `costAvailable` (false: giá vốn, lãi gộp null đến Giai đoạn 8), `generatedAt`, `companyName/Address`. Không có chi nhánh khi không có phạm vi toàn công ty, chi nhánh ngoài phạm vi, từ ngày > đến ngày, nhóm không tồn tại, chọn quá 200: 400 `VALIDATION_ERROR`. Nhân viên "chỉ dữ liệu của mình": chỉ chứng từ mình phụ trách / lập. Quyền `sales-ledger:view` |

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
| `/debt-openings` [CT nhóm A]: `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `cancel`, `post`, `reverse`, `GET /line-import-template`, `POST /line-import` (multipart `file`, `companyId`, `debtType`) | Công nợ đầu kỳ (4B, D-60): không qua duyệt, `post` từ `DRAFT` (hoặc `APPROVED` của luồng cũ). Dòng: `partnerId`, `dueDate` (trống = ngày chứng từ + số ngày nợ), `amount`, `note`; không còn `invoiceNo`, `invoiceDate`. `line-import` chỉ đọc file Excel, trả dòng (đối tượng theo mã, hạn, số tiền, ghi chú) để điền form, không lưu, không cần `Idempotency-Key`; lỗi `400` theo dòng `rows[n].<cột>`. Đảo (`reason`, `postingDate`) chỉ người có `debt-opening:post`; khoản đã thanh toán: `409 HAS_DEPENDENT_DOCUMENTS`. Không sinh bút toán `[Giả định]` |
| `/cash-accounts`: `GET ?companyId=&branchId=&accountType=&status=&keyword=`, `GET /{id}`, `POST`, `PUT /{id}`, `PATCH /{id}/status` | Quỹ tiền mặt, tài khoản ngân hàng (4C). `balance` chỉ đọc; tài khoản kế toán phải là tài khoản chi tiết đang hoạt động; mã trùng `409 DUPLICATE_CODE`; ngừng quỹ còn số dư `409 DATA_IN_USE`. Quyền `cash-account:view` / `manage` |
| `/cash-documents` [CT nhóm A]: `GET ?companyId=&branchId=&documentType=&purpose=&status=&cashAccountId=&partnerId=&dateFrom=&dateTo=&keyword=`, `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/cancel`, `post`, `reverse` | Phiếu thu (PT), phiếu chi (PC), giấy báo có (BC), ủy nhiệm chi (UNC), chuyển tiền nội bộ (CTN) (4C, ADR-13). `purpose`: `CUSTOMER_RECEIPT`, `SUPPLIER_PAYMENT` (bắt buộc `partnerId`, `amount`, `allocationMode` `AUTO` = hóa đơn đến hạn sớm nhất trước lúc ghi sổ / `MANUAL` = dòng `debtItemId`; phần dư là trả trước), `OTHER_RECEIPT`, `OTHER_PAYMENT` (dòng `accountId`, `costItemId`), `INTERNAL_TRANSFER` (`targetCashAccountId`, `amount`). Ghi sổ từ DRAFT (D-61): PC, UNC, CTN từ ngưỡng `finance.payment-approval-threshold` trở lên mà thiếu quyền `cash-document:post-over-threshold`: `422 AUTHORITY_LIMIT_EXCEEDED`; `422 CASH_BALANCE_NEGATIVE`, `OVER_ALLOCATION`, `PERIOD_LOCKED`, `POSTING_RULE_NOT_FOUND`. Đảo (`reason`, `postingDate`) quyền `cash-document:reverse`. Chi tiết trả `lines`, `allocations` (phân bổ đã ghi sổ), `approvals` |
| `GET /cash-book?cashAccountId=&dateFrom=&dateTo=` | Sổ quỹ / sổ tiền gửi (4C): số dư đầu, từng dòng thu / chi kèm chứng từ nguồn và số dư lũy kế, số dư cuối; tối đa 366 ngày. Quyền `cash-book:view` |
| `GET /cash-flow?companyId=&branchId=&dateFrom=&dateTo=` | Dòng tiền thực tế (4C): theo quỹ (đầu kỳ, thu, chi, cuối kỳ) và theo nội dung thu chi |
| `POST /cash-documents/batch` | Thu hàng loạt (BR-AR-08) - chưa làm |
| `GET /debt-prepayments?companyId=&debtType=&partnerId=` | Tiền trả trước còn chưa bù trừ theo chi nhánh trong phạm vi (4D) |
| `/debt-offsets` [CT nhóm A]: `GET ?companyId=&branchId=&offsetType=&partnerId=&status=&dateFrom=&dateTo=&keyword=`, `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/cancel`, `post`, `reverse` | Bù trừ công nợ (DTCN, 4D, BR-AR-04). `offsetType` `PREPAYMENT` (+ `debtType`): dòng hóa đơn cùng loại, tổng ≤ trả trước còn; `AR_AP`: đối tượng vừa là khách vừa là nhà cung cấp, tổng dòng phải thu = tổng dòng phải trả. Ghi sổ: `422 OVER_ALLOCATION` khi trả trước / số còn lại không đủ (kiểm tra sau khi khóa). Đảo = bỏ bù trừ (`debt-offset:reverse`) |
| `/debt-adjustments` [CT nhóm A], cùng bộ hành động | Điều chỉnh công nợ `documentType=DCCN` (BR-AR-07; không qua duyệt, D-61) và thanh toán tạm ứng `TTTU` (BR-CSH-04). Dòng `direction` `DECREASE` (bắt buộc `debtItemId`) / `INCREASE` (`invoiceNo`, `dueDate` tùy chọn), `accountId` đối ứng bắt buộc; `reconciliationId` tùy chọn. Đảo khi khoản tăng đã được thanh toán: `409 HAS_DEPENDENT_DOCUMENTS` |
| `GET /debt-statements?companyId=&branchId=&debtType=&partnerId=&dateFrom=&dateTo=` | Bảng đối chiếu công nợ (BR-AR-06, 4D): số dư đầu, phát sinh theo chứng từ, số dư cuối, hóa đơn còn nợ, trả trước; quyền `debt-reconciliation:view`. In ở giao diện; gửi email chưa làm |
| `/debt-reconciliations`: `GET`, `GET /{id}`, `POST`, `POST /{id}/confirm` (`confirmedBalance`, `contactName`, `note`), `POST /{id}/cancel` (`reason`) | Biên bản đối chiếu: chốt số dư hệ thống tại `asOfDate`; `branchId` trống (toàn công ty) cần phạm vi toàn công ty, không thì `403`. Xác nhận → `MATCHED` / `DIFFERENT` |
| `/cash-counts` [CT nhóm A], cùng bộ hành động | Kiểm kê quỹ tiền mặt (KKQ, 4D, BR-CSH-05; không qua duyệt, D-61): `cashAccountId` (quỹ tiền mặt), `documentDate` (ngày kiểm kê), `countedAmount`. Ghi sổ chốt `bookBalance`, `differenceAmount`, sinh sổ quỹ và bút toán chênh lệch |
| `/cash-documents` `purpose` `EMPLOYEE_ADVANCE` (PC, UNC) / `ADVANCE_RETURN` (PT, BC) | Chi tạm ứng nhân viên (bắt buộc `partnerId` vai trò nhân viên, `amount`) sinh khoản công nợ tạm ứng; hoàn ứng phân bổ như thu tiền khách (4D) |
| `GET /cash-flow/forecast` | Thay bằng `GET /reports/cash-flow-forecast` (Giai đoạn 10D, ADR-18) |

### M08 - Kế toán, giá vốn
| Tài nguyên | Ghi chú |
|---|---|
| `GET /accounts?companyId=&keyword=&status=&postable=`, `GET /accounts/tree?companyId=`, `POST /accounts`, `PUT /accounts/{id}`, `PATCH /accounts/{id}/status` | Hệ thống tài khoản (4A). `tree` trả toàn bộ tài khoản công ty theo mã (không phân trang, số tài khoản có giới hạn). Thêm tài khoản con dưới tài khoản đã hạch toán: `409 DATA_IN_USE`. Ngừng tài khoản còn con hoạt động / còn quy tắc dùng: `409 DATA_IN_USE`. Quyền `account:view/manage` |
| `GET /accounts/import-template`, `POST /accounts/import?companyId=` (multipart `file`) | Nhập hệ thống tài khoản từ Excel: cột Mã TK, Tên TK, Mã TK cha, Tính chất (NO/CO/LT), Theo dõi đối tượng (C/K); tất cả hoặc không, lỗi `rows[<dòng>].<cột>` |
| `GET /posting-rules?companyId=&documentType=&transactionCode=&status=`, `POST /posting-rules`, `PUT /posting-rules/{id}`, `PATCH /posting-rules/{id}/status` | Quy tắc hạch toán (4A, ADR-11). Sửa chỉ đổi tài khoản, ngày hết hiệu lực, diễn giải; trùng điều kiện + ngày bắt đầu: `409 DUPLICATE_CODE`. Quyền `posting-rule:view/manage` |
| `GET /journal-entries?companyId=&branchId=&dateFrom=&dateTo=&sourceType=&keyword=&accountId=&status=`, `GET /journal-entries/{id}`, `GET /journal-entries/by-source?companyId=&sourceType=&sourceId=` | Bút toán chỉ đọc (4A), lọc theo phạm vi chi nhánh. Chi tiết gồm dòng (kèm `partnerCode`, `partnerName` của đối tượng) và bút toán đảo (`reversedBy`). Quyền `journal:view`. Bút toán tổng hợp nhập tay: P3 (BR-ACC-09) |
| `POST /cost-calculations`, `GET /cost-calculations/{id}` | Tính giá vốn theo kỳ/kho |
| `GET /accounting/reconciliation?periodId=` | Đối chiếu kho, công nợ, quỹ với tài khoản |
| `GET /accounting/exports/misa?from=&to=` | Xuất dữ liệu sang MISA |

### M09 - Thiết bị
`/equipment` (theo serial), `GET /equipment/{serialId}/warranty`, `/equipment-handovers` [CT], `/equipment-exchanges` [CT], `/service-tickets` [CT, P2], `GET /equipment?customerId=&contractId=&salesOrderId=`.

### M10 - Báo cáo, dashboard
| Tài nguyên | Ghi chú |
|---|---|
| `GET /dashboard/summary?companyId=&branchId=&dateFrom=&dateTo=&top=` | Dashboard điều hành (10C, BR-RPT-01, 02, 04..06, 10..13). `revenue` / `purchases`: `grossAmount` (đơn đã ghi sổ trong kỳ, chưa thuế, sau chiết khấu, đơn mua quy đổi VND), `deductionAmount` (trả lại / giảm giá), `netAmount`, `documentCount`, `costAvailable = false` (giá vốn, lợi nhuận: Giai đoạn 8); chứng từ đảo trừ vào ngày đảo. `receivables` / `payables`: còn lại đến hôm nay, `overdueAmount`, `overdueBuckets[]` (`fromDays, toDays, amount` theo tham số `finance.aging-bucket-1..3-days`). `expiry`: `expiredCount`, `expiringCount` (lô × kho còn tồn, `inventory.expiry-warning-days`). `purchaseBacklog` (đơn mua gửi / duyệt chưa ghi sổ), `salesBacklog` (đơn bán chưa ghi sổ hoặc đã ghi sổ khách chưa nhận): `orderCount, totalAmount, overdueCount`. `topProducts`, `topCustomers` (doanh thu thuần), `topSuppliers` (giá trị mua thuần): `top` 1..50, mặc định 10. `today`, `generatedAt` (BR-RPT-13). Quyền `dashboard:view`; phạm vi chi nhánh / kho, "chỉ dữ liệu của mình" |
| `GET /dashboard/{revenue\|purchases}/details?companyId=&branchId=&dateFrom=&dateTo=&partnerId=&productId=` | Chứng từ cấu thành chỉ tiêu (BR-RPT-11): `rows[]` (`entryDate, documentType, documentId, documentNo, reversal, partnerCode, partnerName, amount` có dấu), `total` = `netAmount` khi không lọc; lọc đối tượng / hàng để xem một dòng top |
| `GET /dashboard/{receivables\|payables}/details?companyId=&branchId=` | Khoản công nợ còn lại: `sourceType, sourceId, sourceNo, invoiceNo, dueDate, originalAmount, settledAmount, remainingAmount, overdueDays`; `totalAmount`, `overdueAmount` |
| `GET /dashboard/expiring/details?companyId=&branchId=` | Lô × kho còn tồn đã / sắp hết hạn: `lotNo, expiryDate, daysLeft` (âm: đã quá hạn), `onHand` (đơn vị cơ sở) |
| `GET /dashboard/{purchase-backlog\|sales-backlog}/details?companyId=&branchId=` | Đơn chưa giao đủ: `status, deliveryStatus, expectedDate, overdueDays, totalAmount` (gồm thuế); `totalAmount` |
| `GET /reports/{reportCode}?...` | Báo cáo BR-RPT-20..33, module `reporting` (ADR-18). Xuất Excel / PDF dựng ở trình duyệt từ cùng dữ liệu (không có endpoint `/export` riêng) |
| `GET /reports/stock-card?companyId=&warehouseId=&productId=&lotId=&serialId=&dateFrom=&dateTo=` | Thẻ kho / lịch sử serial (BR-RPT-21): `productId` hoặc `serialId` bắt buộc; `opening`, `lines[]` (`postingDate, transactionType, referenceType, referenceId, referenceNo, reversal, warehouseCode, lotNo, expiryDate, serialNo, partnerName, qualityStatus, inQuantity, outQuantity, balance`), `totalIn`, `totalOut`, `closing`; đơn vị cơ sở; tối đa 20.000 dòng (vượt: 400). Quyền `inventory-report:view` (cả nhóm báo cáo kho) |
| `GET /reports/inventory-movement?companyId=&warehouseId=&productCategoryId=&productIds=&dateFrom=&dateTo=` | Nhập - xuất - tồn (BR-RPT-22) theo hàng × kho: `opening, inQuantity, outQuantity, closing`, giá trị tạm tính `averageUnitCost, openingValue, inValue, outValue, closingValue` (null khi hàng chưa có đơn giá nhập; D-57); đảo trừ vào chiều chứng từ gốc; chuyển trạng thái chất lượng không tính; `valueAvailable = false`, `valueProvisional = true` đến Giai đoạn 8; `productIds` dạng `1,2,3` tối đa 200 |
| `GET /reports/slow-moving?companyId=&warehouseId=&days=` | Tồn lâu (BR-RPT-23): còn tồn, lần xuất gần nhất (chưa xuất: lần nhập) trước `today − days`; `days` mặc định tham số `inventory.slow-moving-days`; `idleDays` |
| `GET /reports/stock-exceptions?companyId=&warehouseId=` | Hàng ngoại lệ (BR-RPT-23): số dư còn tồn có `reasons` EXPIRED (quá hạn dùng) / QUALITY (khác PASSED) / BLOCKED (lô, serial khóa) |
| `GET /reports/missing-documents?companyId=&warehouseId=` | Hàng thiếu hồ sơ (BR-RPT-23): hàng còn tồn có loại hàng thuộc `required_product_types` của loại hồ sơ áp dụng cho hàng, không có hồ sơ còn hiệu lực; `expiredDocumentNo` khi chỉ có hồ sơ hết hạn |
| `GET /reports/lot-trace?companyId=&lotId=\|serialId=` | Truy xuất lô / serial (BR-RPT-24): `lines[]` (số lượng có dấu, đối tượng), `customers[]` (`issued, returned, net`), `onHand`; chọn đúng một trong `lotId`, `serialId` |
| `GET /reports/customer-trace?companyId=&customerId=&dateFrom=&dateTo=` | Lô, serial khách đã nhận / trả lại (BR-RPT-24), kể cả dòng đảo |
| `GET /reports/purchase-order-progress?companyId=&branchId=&supplierId=&status=&dateFrom=&dateTo=` | Tình hình thực hiện đơn mua (BR-RPT-25) theo ngày chứng từ: `status`, `expectedDate`, `overdueDays` (đơn nháp hoặc đã duyệt trước D-61 chưa ghi sổ, quá ngày dự kiến), `totalAmount`, `returnedAmount` (trả lại / giảm giá đã ghi sổ, gồm thuế), `paidAmount` (thu / chi / bù trừ trên sổ công nợ, sau đảo), `remainingAmount`; `total` có `orderCount`, `overdueCount`. Bỏ đơn đã hủy trừ khi lọc `status`. Quyền `purchase-report:view` |
| `GET /reports/purchase-price-history?companyId=&branchId=&productIds=&supplierId=&dateFrom=&dateTo=` | Lịch sử giá mua (BR-RPT-25): dòng hàng đơn mua đang ghi sổ (`unitPrice`, `discountPercent`, `netUnitPrice`, `netBasePrice` theo đơn vị cơ sở), `summaries[]` (`minPrice`, `maxPrice`, `lastPrice`, `lastSupplierName`). Quyền `purchase-report:view` |
| `GET /reports/supplier-defects?companyId=&branchId=&dateFrom=&dateTo=` | Hàng lỗi theo NCC (BR-RPT-25): phiếu hàng lỗi (trừ đã hủy) theo ngày nhận, NCC truy qua lô = đối tượng lần nhập mua đầu tiên của lô (phiếu nhập nguồn DMH); `suppliers[]` (`supplierId` null: không xác định). Quyền `purchase-report:view` |
| `GET /reports/sales-order-progress?companyId=&branchId=&customerId=&status=&dateFrom=&dateTo=` | Tình hình thực hiện đơn bán (BR-RPT-26): như đơn mua, thêm `deliveryStatus`; trễ hẹn gồm đơn đã ghi sổ khách chưa nhận; "chỉ dữ liệu của mình" lọc theo nhân viên phụ trách / người lập. Quyền `sales-order-report:view` |
| `GET /reports/sales-journal?companyId=&branchId=&dateFrom=&dateTo=` | Sổ nhật ký bán hàng (BR-RPT-26) từ chứng từ bán đã ghi sổ (chủ dự án chọn nguồn chứng từ): `receivable` (gồm thuế), `goodsAmount`, `serviceAmount` (theo loại dòng đơn bán), `deductionAmount` (trả lại / giảm giá), `taxAmount`; đảo: dòng âm theo ngày đảo; `total` |
| `GET /reports/stock-backlog?companyId=&warehouseId=&productIds=` | Tồn so với đặt hàng chưa giao (BR-RPT-26, H-06) theo hàng × kho: `ordered` (đơn bán nháp hoặc đã duyệt trước D-61 chưa ghi sổ), `orderCount`, `onHand`, `reserved`, `available`, `incoming`, `shortage` = max(ordered − onHand − incoming, 0) |
| `GET /reports/cash-flow-forecast?companyId=&branchId=&granularity=&periods=` | Dòng tiền dự kiến (10D, BR-RPT-09, 30, BR-CSH-06): `openingBalance`, `cashAccounts[]` (số dư từ sổ quỹ đến hôm nay), `columns[]` (`OVERDUE` ngày dự kiến trước hôm nay, `P1..Pn` từ hôm nay, `LATER`; `from`, `to`), `lines[]` theo `source` RECEIVABLE / SALES_ORDER (thu), PAYABLE / PURCHASE_ORDER (chi) với `amounts[]`, `total`; `inflow`, `outflow`, `net`, `closing` (lũy kế). `granularity` DAY (tối đa 92 kỳ) / WEEK (mặc định, 53) / MONTH (24, kỳ đầu đến cuối tháng); `periods` mặc định 12. Ngày dự kiến: hạn công nợ còn lại; đơn đã duyệt trước D-61 chưa ghi sổ (đơn nháp không tính, D-61): hạn trên đơn mua, hoặc ngày dự kiến giao / nhận (trống: ngày đơn) + số ngày được nợ. "Chỉ dữ liệu của mình": không gồm số dư quỹ (`cashBalanceIncluded = false`). Quyền `cash-forecast:view` |
| `GET /reports/cash-flow-forecast/items?companyId=&branchId=&source=&dateFrom=&dateTo=` | Khoản cấu thành một ô (BR-RPT-11): `rows[]` (`source, documentType, documentId, documentNo, partnerCode, partnerName, expectedDate, overdueDays, amount`), `total` bằng số trong ô khi lọc đúng nguồn và `from` / `to` của cột |

### M11 - Trợ lý AI và OCR (D-64, docs/14)
| Tài nguyên | Ghi chú |
|---|---|
| `GET /ai/health` | `{ocrAvailable, chatAvailable}` để giao diện ẩn / hiện nút nhận dạng, nút trợ lý (BR-AI-09). Chỉ cần đăng nhập; không lộ địa chỉ, model của AI Service |
| `POST /ocr/recognitions?companyId=&branchId=&documentType=&documentDate=` (multipart `file`) | AI-1b: nhận dạng `PURCHASE_INVOICE` (hóa đơn NCC) / `PURCHASE_ORDER` (đơn đặt hàng gửi NCC) để điền form đơn mua; AI-2: `SALES_ORDER` (phiếu đặt hàng của khách) để điền form đơn bán. Quyền `ocr:use` **và** quyền lập form đích (`purchase-order:create` / `sales-order:create`, thiếu: 403 `FORBIDDEN`); công ty, chi nhánh trong phạm vi (ngoài phạm vi: 404). Chỉ trả gợi ý, **không lưu chứng từ, không lưu file** (BR-AI-04, BR-AI-10), không cần `Idempotency-Key` (Q-06). `documentDate` (trống: hôm nay) để xét hiệu lực thuế suất. Phản hồi: `header.supplier` (theo MST), `invoiceSeries`, `invoiceNo`, `invoiceDate` (hóa đơn), `documentDate`, `description` "Theo đơn đặt hàng số ..." (đơn đặt hàng), `currencyCode`; `lines[]` (`product`, `unit`, `taxRate` dạng đối chiếu danh mục; `quantity`, `unitPrice`, `discountPercent`, `printedAmount` dạng giá trị OCR; `amountCheck`); `totals` (`goods, vat, total, check`); `warnings[]`. Đối chiếu danh mục: `status` MATCHED (có `id`) / AMBIGUOUS (`candidates[]`, không gán) / NOT_FOUND / UNKNOWN, `matchedBy` CODE / TAX_CODE / NAME / UNIT / RATE, `reviewRequired`. Giá trị OCR: `value` (chuỗi thập phân), `rawText`, `confidence`, `status` OK / LOW_CONFIDENCE / UNKNOWN. Kiểm tra số học: OK / MISMATCH (giữ giá trị OCR) / UNKNOWN. Lỗi: 400 `OCR_FILE_INVALID`, 413, 415, 422 `OCR_NO_TEXT`, 429 `AI_BUSY`, 503, 504; license không hiệu lực: 403 `LICENSE_INACTIVE` (Q-11). `SALES_ORDER` trả `header.customer` (khách theo MST) thay `supplier`, `documentDate`, `description` "Theo đơn đặt hàng của khách số ...", `deliveryAddress` (giá trị OCR, chữ có dấu có thể mất: giao diện chỉ hiển thị để đối chiếu), `currencyCode`; `lines`, `totals` như đơn mua. Chứng từ không in MST: đối tác chỉ trả ứng viên theo tên (`AMBIGUOUS`), không tự gán |
| `GET /assistant/conversations?page=&size=&sort=` | AI-3: hội thoại của chính người dùng. Sắp xếp `lastMessageAt` (mặc định giảm dần), `createdAt`, `title`. Quyền `assistant:use` |
| `POST /assistant/conversations` | Tạo hội thoại `{title?}` (trống: lấy câu hỏi đầu tiên). Bắt buộc `Idempotency-Key`. 201 |
| `GET /assistant/conversations/{id}/messages?page=&size=` | Tin nhắn theo thứ tự gửi: `id, role` USER / ASSISTANT, `content`, `status` PENDING / ANSWERED / FAILED / COMPLETED, `citations[]` (tên mục tài liệu hướng dẫn), `grounded`, `createdAt`. Hội thoại của người khác: 404 |
| `POST /assistant/conversations/{id}/messages` | Gửi câu hỏi `{content}` (tối đa `ai.chat.max-message-chars`), nhận `{question, answer}`. Bắt buộc `Idempotency-Key`: gửi lại cùng key và cùng nội dung trả lại câu trả lời đã có, không gọi AI lại; đang xử lý: 409 `REQUEST_IN_PROGRESS`; lần trước lỗi: hỏi lại; cùng key khác nội dung: 409 `IDEMPOTENCY_KEY_REUSED` (AT-13). Trợ lý chỉ trả lời từ tài liệu hướng dẫn đã duyệt; không có đoạn phù hợp thì trả câu "chưa có trong tài liệu" (`grounded = false`). Lỗi: 400 `ASSISTANT_MESSAGE_TOO_LONG`, 404, 429 `AI_BUSY` (bận hoặc vượt `ai.rate-limit.per-user-per-minute`), 503, 504; license không hiệu lực: 403 (Q-11) |
| `DELETE /assistant/conversations/{id}` | Xóa hẳn hội thoại và tin nhắn của mình (không phải chứng từ) |

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
