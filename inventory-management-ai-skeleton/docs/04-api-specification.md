# Đặc tả API REST

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 (DRAFT - hợp đồng API chi tiết chốt khi làm từng module) |
| Ngày cập nhật | 2026-10-09 |
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
| 409 | `DUPLICATE_CODE`, `DUPLICATE_DOCUMENT`, `INVALID_DOCUMENT_STATE`, `CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS`, `HAS_DEPENDENT_DOCUMENTS`, `DATA_INTEGRITY_VIOLATION` | Xung đột. `REQUEST_IN_PROGRESS`: yêu cầu cùng `Idempotency-Key` đang xử lý song song. `DATA_INTEGRITY_VIOLATION`: ràng buộc DB chặn (lưới an toàn khi kiểm tra nghiệp vụ bỏ sót) |
| 422 | `INSUFFICIENT_STOCK`, `LOT_EXPIRED`, `LOT_NOT_SELLABLE`, `SERIAL_UNAVAILABLE`, `PERIOD_LOCKED`, `CONTRACT_QUANTITY_EXCEEDED`, `CREDIT_LIMIT_EXCEEDED`, `PRICE_BELOW_FLOOR`, `OVER_ALLOCATION`, `CASH_BALANCE_NEGATIVE`, `STOCK_COUNT_IN_PROGRESS` | Vi phạm quy tắc nghiệp vụ |
| 423 | `LOCK_TIMEOUT` | Dữ liệu đang được xử lý, thử lại |
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
| `/document-sequences` | Cấu hình số chứng từ |
| `/approval-workflows` [DM] | Ma trận duyệt |
| `GET /approvals/pending`, `GET /approvals/{id}` | Việc chờ duyệt của tôi |
| `/fiscal-periods`, `POST /fiscal-periods/{id}/lock`, `POST /fiscal-periods/{id}/unlock` | Khóa/mở theo phân hệ (`module`, `reason`) |
| `/parameters` | Tham số hệ thống |
| `GET /audit-logs`, `GET /login-logs` | Chỉ đọc. `GET /login-logs` **đã triển khai (1A)**: lọc `username, event, from, to` |
| `/attachments` (`POST` multipart, `GET /{id}/download`) | Giới hạn loại và dung lượng (D-41) |
| `GET /notifications`, `POST /notifications/{id}/read` | |

### M02 - Danh mục
`/product-categories`, `/products`, `/units`, `/products/{id}/units`, `/brands`, `/manufacturers`, `/countries`, `/partners` (lọc theo `role`), `/partners/{id}/contacts`, `/partners/{id}/addresses`, `/warehouses`, `/warehouses/{id}/locations`, `/tax-rates`, `/price-lists`, `/currencies`, `/exchange-rates`, `/payment-terms`, `/payment-methods`, `/delivery-methods`, `/contract-types`, `/reasons`, `/cost-items`, `/compliance-document-types`, `/compliance-documents` — tất cả [DM].

`GET /compliance-documents/expiring?days=30` — giấy tờ sắp hết hạn.

### M03 - Mua hàng
| Tài nguyên | Ghi chú |
|---|---|
| `/purchase-requests` [CT nhóm B] | `POST /{id}/create-quotation-request`, `POST /{id}/create-purchase-order` |
| `/supplier-quotation-requests` [CT nhóm B] | Gửi nhiều NCC |
| `/supplier-quotations` [CT nhóm B] | `GET /supplier-quotation-requests/{id}/comparison`; `POST /supplier-quotations/{id}/select` |
| `/purchase-orders` [CT nhóm B] | `GET /{id}/progress` (đặt / nhận / nhập / lỗi / HĐ / còn lại) |
| `/purchase-receipts` [CT nhóm A] | Phiếu nhận hàng; `POST /{id}/create-inventory-receipt`; `POST /purchase-receipts/receive-and-stock` (thao tác nhanh, D-36) |
| `/purchase-invoices` [CT nhóm A] | Chống trùng (MST, ký hiệu, số) |
| `/purchase-returns` [CT nhóm A] | Trả hàng, giảm giá hàng mua |
| `/landed-cost-vouchers` [CT nhóm A] | Phân bổ chi phí mua |

### M04 - Kho
| Tài nguyên | Ghi chú |
|---|---|
| `/inventory-receipts`, `/inventory-issues`, `/inventory-transfers`, `/inventory-adjustments`, `/quality-status-changes` [CT nhóm A] | |
| `/stock-counts` [CT] | Thêm `POST /{id}/start-counting` (snapshot), `PUT /{id}/counts`, `POST /{id}/submit-review` |
| `/recalls` [CT nhóm B] | `GET /{id}/affected-customers` |
| `/stock-issue-policies` [CT] | Chính sách xuất vượt tồn khả dụng (D-40) |
| `GET /inventory/stock` | Tồn: thực tế, khả dụng, giữ chỗ, đang về theo hàng/kho/vị trí/lô/serial/trạng thái |
| `GET /inventory/available?productId=&warehouseId=` | Tồn khả dụng theo lô, gợi ý FEFO |
| `GET /inventory/stock-card?productId=&warehouseId=&lotId=&from=&to=` | Thẻ kho |
| `GET /inventory/lots/{id}/trace`, `GET /inventory/serials/{id}/trace` | Truy xuất lô/serial |
| `GET /inventory/reservations` | Giữ chỗ |
| `GET /inventory/alerts?type=BELOW_MIN\|SLOW_MOVING\|NEAR_EXPIRY` | Cảnh báo |

### M05 - Bán hàng
| Tài nguyên | Ghi chú |
|---|---|
| `/sales-quotes` [CT nhóm B] | `POST /{id}/revise`, `POST /{id}/mark-sent`, `POST /{id}/accept`, `POST /{id}/create-sales-order` |
| `/sales-orders` [CT nhóm B] | `GET /{id}/availability`, `POST /{id}/reserve`, `POST /{id}/release`, `POST /{id}/create-issue`, `POST /{id}/create-purchase-request`, `GET /{id}/progress` |
| `/deliveries` [CT nhóm B] | `POST /{id}/confirm` (khách nhận), `POST /{id}/fail` |
| `/sales-invoices` [CT nhóm A] | `PUT /{id}/einvoice-reference` (P1: số/ký hiệu/mã CQT từ MISA) |
| `/sales-returns` [CT nhóm A] | Loại: RETURN, EXCHANGE, PRICE_REDUCTION |
| `/promotions` [DM] | |
| `POST /sales/quick-sale` | Thao tác nhanh sinh các chứng từ riêng, liên kết (BR-SAL-42) |
| `GET /sales/price?customerId=&productId=&unitId=&quantity=&date=` | Giá áp dụng theo thứ tự ưu tiên (BR-SAL-02) |

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
| `GET /receivables`, `GET /payables` | Theo hóa đơn, lọc quá hạn |
| `GET /receivables/aging`, `GET /payables/aging` | Tuổi nợ |
| `/cash-receipts`, `/cash-payments`, `/cash-transfers` [CT nhóm A] | Phân bổ trong dòng (`allocations[]`), gợi ý FIFO hóa đơn |
| `POST /cash-receipts/batch` | Thu hàng loạt |
| `/payment-allocations` | Đối trừ; `POST /{id}/reverse` |
| `/debt-adjustments` [CT nhóm A] | Điều chỉnh chênh lệch đối chiếu |
| `/employee-advances` [CT] | Tạm ứng, hoàn ứng |
| `GET /partners/{id}/debt-statement?from=&to=` | Biên bản đối chiếu công nợ (in, gửi email) |
| `GET /cash-accounts`, `GET /cash-accounts/{id}/ledger` | Sổ quỹ, sổ tiền gửi |
| `GET /cash-flow/forecast?from=&to=` | Dòng tiền dự kiến |

### M08 - Kế toán, giá vốn
| Tài nguyên | Ghi chú |
|---|---|
| `/accounts` [DM] | Hệ thống tài khoản |
| `/posting-rules` [DM] | Quy tắc hạch toán |
| `/journal-entries` [CT nhóm A] | Bút toán tổng hợp thủ công; bút toán tự sinh chỉ đọc |
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
