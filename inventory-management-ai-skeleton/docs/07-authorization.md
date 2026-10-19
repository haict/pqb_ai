# Xác thực và phân quyền

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.3 (Giai đoạn 1A, 1C, 1D, 2, 3) |
| Ngày cập nhật | 2026-10-14 |
| Căn cứ | BR-SYS-02..06, BR-SYS-20..23, D-38; `.claude/rules/security.md` |
| Mã nguồn | `backend/.../system` (api, application, domain, infrastructure/security); migration `V2`..`V6`; dữ liệu mẫu `db/dev-data` |

## 1. Xác thực (D-38)
| Thành phần | Thiết kế |
|---|---|
| Access token | JWT ký HS256 (Spring OAuth2 Resource Server), hạn 15 phút. Claim: `sub` = id người dùng, `username`, `cv` = phiên bản thông tin đăng nhập, `iss` = `erp`. Gửi qua header `Authorization: Bearer`. Frontend chỉ giữ trong bộ nhớ |
| Refresh token | 256 bit ngẫu nhiên; DB chỉ lưu băm SHA-256 (`sys_refresh_tokens`). Trả trong cookie `ERP_REFRESH`: `HttpOnly`, `SameSite=Strict`, `Secure` (trừ dev), `Path=<context>/api/v1/auth` |
| Phiên | Hết hạn sau 30 phút không thao tác hoặc tối đa 12 giờ (`erp.auth.session-*`) |
| Xoay vòng | Mỗi lần `/auth/refresh` đánh dấu token cũ đã dùng, phát hành token mới cùng phiên (family). Token đã dùng bị gửi lại → thu hồi cả phiên, ghi `REFRESH_REUSE_DETECTED` |
| Nạp quyền | Mỗi request nạp lại người dùng, vai trò, quyền, phạm vi từ DB. Khóa tài khoản, đổi quyền có hiệu lực ngay, không chờ token hết hạn |
| Vô hiệu token cũ | Đổi / đặt lại mật khẩu thay `credentials_changed_at` (mili-giây). Token có `cv` khác bị từ chối (401). Mọi refresh token của người dùng bị thu hồi |
| Secret | `ERP_JWT_SECRET` ≥ 32 byte theo môi trường. Thiếu hoặc ngắn thì ứng dụng không khởi động |

### 1.1 API xác thực
| Endpoint | Quyền | Ghi chú |
|---|---|---|
| `POST /api/v1/auth/login` | Công khai | Sai mật khẩu, tên không tồn tại, tài khoản khóa hoặc ngừng hoạt động: cùng lỗi `401 INVALID_CREDENTIALS`, không tiết lộ tài khoản có tồn tại hay không |
| `POST /api/v1/auth/refresh` | Công khai (cần cookie) | Lỗi: `401 SESSION_EXPIRED` |
| `POST /api/v1/auth/logout` | Công khai (cookie) | Thu hồi phiên, xóa cookie |
| `GET /api/v1/auth/me` | Đã đăng nhập | Quyền, chi nhánh được làm việc, `mustChangePassword` |
| `POST /api/v1/auth/change-password` | Đã đăng nhập | Thu hồi mọi phiên khác, trả phiên mới |

### 1.2 Chính sách tài khoản (BR-SYS-23, tham số `erp.auth.*`)
- **Mật khẩu:**
  - Tối thiểu 8 ký tự, tối đa 72.
  - Phải có chữ và số.
  - Không trùng tên đăng nhập.
  - Băm bằng BCrypt (`{bcrypt}`).
- **Khóa tạm:**
  - Sai 5 lần liên tiếp thì khóa 15 phút.
  - Đặt lại mật khẩu thì mở khóa.
  - Các cột thống kê đăng nhập được cập nhật bằng câu lệnh riêng, nên không làm tăng `version` và không ghi đè `updated_by` của người dùng.
- **Bắt buộc đổi mật khẩu:**
  - Áp dụng cho người dùng mới, người vừa được đặt lại mật khẩu, và quản trị viên khởi tạo.
  - Trước khi đổi, mọi API ngoài `/api/v1/auth/**` trả `403 PASSWORD_CHANGE_REQUIRED`.
- **Quản trị viên đầu tiên (uat/prod):**
  - Đặt `ERP_BOOTSTRAP_ADMIN_PASSWORD`; tên đăng nhập lấy từ `ERP_BOOTSTRAP_ADMIN_USERNAME`, mặc định `admin`.
  - Chỉ tạo khi database chưa có người dùng nào.
  - Phải đổi mật khẩu ở lần đăng nhập đầu.

## 2. Phân quyền (RBAC)
- Người dùng → nhiều vai trò → nhiều quyền dạng `<chức năng>:<hành động>`.
- Mỗi endpoint khai báo `@PreAuthorize("hasAuthority('<quyền>')")`, mặc định từ chối.
- Vai trò ngừng hoạt động không mang quyền.
- **Chống leo thang quyền:**
  - Chỉ gán cho vai trò những quyền mà người thao tác đang có.
  - Chỉ gán cho người dùng những vai trò có toàn bộ quyền nằm trong quyền của người thao tác.
  - Không tự đổi vai trò, phạm vi, trạng thái hay đặt lại mật khẩu của chính mình (`403`).

### 2.1 Danh mục quyền M01
| Quyền | Ý nghĩa |
|---|---|
| `company:view` / `company:create` / `company:update` | Xem / tạo / sửa, ngừng hoạt động công ty |
| `branch:view` / `branch:create` / `branch:update` | Chi nhánh |
| `department:view` / `department:create` / `department:update` | Phòng ban |
| `user:view` / `user:create` / `user:update` | Người dùng (sửa thông tin, kích hoạt / ngừng hoạt động) |
| `user:reset-password` | Đặt lại mật khẩu |
| `user:assign-roles` / `user:assign-scopes` | Gán vai trò / phạm vi dữ liệu |
| `role:view` / `role:create` / `role:update` / `role:assign-permissions` | Vai trò và quyền; `role:view` cho phép xem danh mục quyền |
| `login-log:view` | Nhật ký đăng nhập |
| `period:view` / `period:manage` | Xem kỳ và trạng thái khóa / tạo kỳ, khóa kỳ (1C) |
| `period:unlock` | Mở lại kỳ đã khóa (BR-SYS-32) |
| `parameter:view` / `parameter:update` | Tham số hệ thống (1C) |
| `license:manage` | Xem chi tiết và nạp bản quyền phần mềm (D-59, V30); mặc định `SYSTEM_ADMIN`. Trạng thái bản quyền (banner) mọi người đã đăng nhập đều xem được |
| `document-number:view` / `document-number:update` | Cấu hình số chứng từ (1C) |

Module sau thêm quyền bằng migration của mình và gán cho vai trò mặc định tương ứng.

### 2.2 Ma trận vai trò mặc định × quyền (Giai đoạn 1A, 1C)
| Vai trò | Quyền M01 |
|---|---|
| `SYSTEM_ADMIN` Quản trị hệ thống | Toàn bộ quyền 1A; `period:view`, `parameter:view/update`, `document-number:view/update`. **Không** có `period:manage`, `period:unlock` (khóa/mở kỳ là việc của kế toán) |
| `DIRECTOR` Giám đốc | `company/branch/department:view`, `user:view`, `role:view`, `login-log:view`, `period:view`, `period:unlock`, `parameter:view`, `document-number:view` |
| `CHIEF_ACCOUNTANT` Kế toán trưởng | `company/branch/department:view`, `period:view/manage/unlock`, `parameter:view/update`, `document-number:view/update` |
| `TAX_ACCOUNTANT` Kế toán thuế / tổng hợp | `company/branch/department:view`, `period:view/manage`, `parameter:view`, `document-number:view` |
| `AR_ACCOUNTANT`, `CASHIER`, `WAREHOUSE_MANAGER`, `BRANCH_DIRECTOR` | `company/branch/department:view`, `period:view` |
| Các vai trò còn lại (BR mục 4) | `company/branch/department:view` |

Mở lại kỳ chỉ Kế toán trưởng và Giám đốc (BR-SYS-32): người có quyền `period:unlock` thực hiện trực tiếp, bắt buộc lý do, ghi audit; hành động đó là sự phê duyệt. Gán `period:manage` cho Kế toán thuế / tổng hợp, `period:view` cho các vai trò còn lại trong bảng là `[Suy luận]`, doanh nghiệp điều chỉnh được ở màn hình vai trò.

Quyền nghiệp vụ (kho, mua, bán, ...) của từng vai trò bổ sung khi làm module tương ứng. `[Suy luận]` Doanh nghiệp cần xác nhận lại ma trận đầy đủ trước UAT.

### 2.3 Quyền 1D, danh mục (M02), kho (M04)
| Nhóm | Quyền |
|---|---|
| Phê duyệt (1D) | `approval-workflow:view/manage`; `access:grant-any` (gán vai trò chứa quyền mà người gán không có; chỉ `SYSTEM_ADMIN`). Duyệt chứng từ không cần quyền riêng ở M01: người duyệt là người được luồng chỉ định (vai trò hoặc người dùng, kể cả người được ủy quyền) **và** có quyền `<loại>:approve` của chứng từ |
| Danh mục | `catalog:view/manage`, `product:view/manage`, `partner:view/manage`, `warehouse:view/manage`, `price:view/manage`, `purchase-price:view` (giá mua tham khảo), `compliance:view/manage` |
| Kho | `inventory:view` (tồn, sổ kho, lô, serial, cảnh báo; cũng là quyền xem chứng từ đảo), `lot:block`; `inventory-receipt`, `inventory-issue` (PXK, PXH), `inventory-transfer`, `inventory-adjustment` (PDC, PCL), `inventory-reversal` (PDK): mỗi nhóm có `:view` (trừ đảo), `:create`, `:approve`, `:post`; `stock-count:view/create/approve/post`; `recall:view/create/approve`; `stock-policy:view/create/approve` |

Ma trận mặc định `[Suy luận]` (migration V4..V6, doanh nghiệp chỉnh ở màn hình vai trò):

| Vai trò | Danh mục | Kho |
|---|---|---|
| `SYSTEM_ADMIN` | Toàn bộ | `inventory:view` |
| `DIRECTOR` | Xem, gồm giá mua | Mọi quyền xem và duyệt của kho |
| `BRANCH_DIRECTOR` | Xem (không giá mua) | Mọi quyền xem và duyệt của kho |
| `WAREHOUSE_MANAGER` | Xem; quản lý kho, vị trí | Toàn bộ kho trừ duyệt chính sách xuất vượt tồn, duyệt thu hồi, duyệt đảo |
| `WAREHOUSE_STAFF` | Xem | Xem; lập và ghi sổ nhập, xuất, chuyển kho; lập điều chỉnh; lập kiểm kê; xem thu hồi |
| `QA` | Xem; quản lý hồ sơ | Xem; khóa lô; lập, duyệt điều chỉnh / chuyển trạng thái; lập thu hồi |
| `CHIEF_ACCOUNTANT` | Xem, gồm giá mua; quản lý danh mục chung | Mọi quyền xem; lập, duyệt, ghi sổ chứng từ đảo; duyệt kiểm kê |
| `PURCHASING_STAFF` | Quản lý hàng, đối tác, hồ sơ; xem giá mua | `inventory:view` |
| `SALES_MANAGER` | Quản lý đối tác, bảng giá | `inventory:view` |
| Kinh doanh, thầu, kế toán khác, `VIEWER` | Xem | `inventory:view` |

### 2.4 Quyền kế toán (M08, Giai đoạn 4A, migration V9)
| Quyền | Ý nghĩa | Vai trò mặc định `[Suy luận]` |
|---|---|---|
| `account:view`, `account:manage` | Xem; thêm, sửa, ngừng, nhập Excel hệ thống tài khoản | Xem: `CHIEF_ACCOUNTANT`, `TAX_ACCOUNTANT`, `AR_ACCOUNTANT`, `CASHIER`, `DIRECTOR`, `BRANCH_DIRECTOR`. Quản lý: `CHIEF_ACCOUNTANT` |
| `posting-rule:view`, `posting-rule:manage` | Xem; thêm, sửa, ngừng quy tắc hạch toán | Xem: `CHIEF_ACCOUNTANT`, `TAX_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR`. Quản lý: `CHIEF_ACCOUNTANT` |
| `journal:view` | Xem bút toán (theo phạm vi chi nhánh) | `CHIEF_ACCOUNTANT`, `TAX_ACCOUNTANT`, `AR_ACCOUNTANT`, `CASHIER`, `DIRECTOR`, `BRANCH_DIRECTOR` |

Không có quyền tạo / sửa bút toán: bút toán chỉ sinh khi chứng từ nguồn ghi sổ (BR-ACC-03).

### 2.5 Quyền công nợ (M07, Giai đoạn 4B, migration V10)
| Quyền | Ý nghĩa | Vai trò mặc định `[Suy luận]` |
|---|---|---|
| `debt:view` | Xem khoản công nợ, tuổi nợ, sổ chi tiết công nợ (theo phạm vi chi nhánh) | `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `CASHIER`, `TAX_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `debt-opening:view`, `:create`, `:post` | Xem; lập, sửa, hủy; ghi sổ, đảo công nợ đầu kỳ | `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT` (xem: thêm `DIRECTOR`, `BRANCH_DIRECTOR`) |

Công nợ đầu kỳ không qua duyệt (D-60, V31 xóa quyền `debt-opening:approve`): người có `debt-opening:post` ghi sổ thẳng từ nháp và đảo (bắt buộc lý do).

### 2.6 Quyền quỹ, thu chi (M07, Giai đoạn 4C, migration V11)
| Quyền | Ý nghĩa | Vai trò mặc định `[Suy luận]` |
|---|---|---|
| `cash-account:view` | Xem quỹ, tài khoản ngân hàng, số dư | `CHIEF_ACCOUNTANT`, `CASHIER`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `cash-account:manage` | Thêm, sửa, ngừng quỹ / tài khoản ngân hàng | `CHIEF_ACCOUNTANT` |
| `cash-document:view` | Xem phiếu thu, phiếu chi, giấy báo có, ủy nhiệm chi, chuyển tiền | như `cash-account:view` |
| `cash-document:create` | Lập, sửa, gửi duyệt, hủy chứng từ thu chi | `CHIEF_ACCOUNTANT`, `CASHIER`, `AR_ACCOUNTANT` |
| `cash-document:approve` | Duyệt, từ chối chứng từ chi từ ngưỡng duyệt trở lên | `CHIEF_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `cash-document:post` | Ghi sổ (thực thu, thực chi) | `CHIEF_ACCOUNTANT`, `CASHIER` |
| `cash-document:reverse` | Đảo chứng từ thu chi đã ghi sổ (bắt buộc lý do) | `CHIEF_ACCOUNTANT` |
| `cash-book:view` | Sổ quỹ, sổ tiền gửi, dòng tiền thực tế | `CHIEF_ACCOUNTANT`, `CASHIER`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |

- Phạm vi: quỹ thuộc một chi nhánh; chứng từ thu chi lọc theo chi nhánh của người dùng, ngoài phạm vi trả `404`. Quỹ nhận của chuyển tiền có thể thuộc chi nhánh khác cùng công ty `[Giả định]`.
- Duyệt chi (BR-CSH-03): PC, UNC, CTN từ tham số `finance.payment-approval-threshold` (mặc định 20.000.000 `[Giả định]`) trở lên phải qua luồng duyệt; người lập không tự duyệt (`self_approval_allowed = FALSE`). Luồng mẫu dev/test (`R__dev_phase4c_cash.sql`): `CHIEF_ACCOUNTANT` duyệt một bước.
- Đảo tách quyền riêng (`cash-document:reverse`) thay vì gộp vào quyền duyệt như công nợ đầu kỳ: giám đốc duyệt chi nhưng không đảo sổ quỹ `[Suy luận]`.

### 2.7 Quyền bù trừ, điều chỉnh, đối chiếu, kiểm kê quỹ (M07, Giai đoạn 4D, migration V12)
| Quyền | Ý nghĩa | Vai trò mặc định `[Suy luận]` |
|---|---|---|
| `debt-offset:view`, `:create`, `:post` | Xem; lập, sửa, gửi, hủy; ghi sổ bù trừ công nợ | `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT` (xem: thêm `TAX_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR`) |
| `debt-offset:approve` | Duyệt bù trừ (khi doanh nghiệp cấu hình luồng; mặc định DTCN không cần duyệt) | `CHIEF_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `debt-offset:reverse` | Bỏ bù trừ (đảo) | `CHIEF_ACCOUNTANT` |
| `debt-adjustment:view`, `:create`, `:post` | Điều chỉnh công nợ (DCCN), thanh toán tạm ứng (TTTU) | `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT` (lập, xem: thêm `CASHIER`) |
| `debt-adjustment:approve` | Duyệt điều chỉnh / thanh toán tạm ứng (DCCN bắt buộc duyệt theo luồng; ngưỡng theo `min_amount` của luồng = "theo quyền hạn", BR-AR-07) | `CHIEF_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `debt-adjustment:reverse` | Đảo điều chỉnh / thanh toán tạm ứng | `CHIEF_ACCOUNTANT` |
| `debt-reconciliation:view`, `:manage` | Bảng đối chiếu; lập, ghi nhận xác nhận, hủy biên bản | xem: kế toán, giám đốc, thủ quỹ; lập: `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT` |
| `cash-count:view`, `:create`, `:post` | Kiểm kê quỹ | `CHIEF_ACCOUNTANT`, `CASHIER` (xem: thêm kế toán, giám đốc) |
| `cash-count:approve`, `:reverse` | Duyệt / đảo kiểm kê quỹ | duyệt: `CHIEF_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR`; đảo: `CHIEF_ACCOUNTANT` |

- Luồng duyệt mẫu dev/test (`R__dev_phase4d_debt.sql`): DCCN, KKQ do `CHIEF_ACCOUNTANT` duyệt một bước.
- Biên bản đối chiếu toàn công ty (`branchId` trống) chỉ người có phạm vi toàn công ty lập / xem được.

Luồng duyệt mẫu (chỉ dev/test, `R__dev_phase3_inventory.sql`): mọi loại chứng từ kho bắt buộc duyệt do vai trò `DIRECTOR` duyệt một bước; chứng từ đảo do `CHIEF_ACCOUNTANT` duyệt.

### 2.8 Quyền mua hàng (M03, V13, gộp chứng từ D-54 ở V22)
V22 xóa `purchase-requisition:*`, `purchase-receipt:*`, `purchase-invoice:*`, `purchase-order:close`; thêm `purchase-order:post`, `purchase-order:reverse`.

| Quyền | Ý nghĩa | Vai trò mặc định `[Suy luận]` |
|---|---|---|
| `purchase-order:view` | Xem đơn mua | mua hàng, kinh doanh, đấu thầu, kho, kế toán, thủ quỹ, giám đốc |
| `purchase-order:create` | Lập, sửa, gửi, hủy đơn mua (kèm hóa đơn NCC, lô / hạn / serial) | `PURCHASING_STAFF` |
| `purchase-order:approve` | Duyệt, từ chối đơn mua (từ ngưỡng) | `DIRECTOR`, `BRANCH_DIRECTOR` |
| `purchase-order:post` | Ghi sổ đơn mua (nhập kho, công nợ phải trả, bút toán) | `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`, `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT` |
| `purchase-order:reverse` | Đảo đơn mua đã ghi sổ | `CHIEF_ACCOUNTANT` |
| `purchase-return:view` | Xem trả hàng / giảm giá hàng mua | kế toán, mua hàng, kho, giám đốc |
| `purchase-return:create` | Lập, sửa, gửi, hủy | `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT`, `PURCHASING_STAFF` |
| `purchase-return:approve` | Duyệt (khi cấu hình luồng) | `CHIEF_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `purchase-return:post` | Ghi sổ (xuất trả, giảm công nợ) | `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF` |
| `purchase-return:reverse` | Đảo | `CHIEF_ACCOUNTANT` |
| `purchase-cost-allocation:view` | Xem phân bổ chi phí mua | kế toán, mua hàng, giám đốc |
| `purchase-cost-allocation:create`, `:post` | Lập, gửi, hủy; ghi sổ | `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT` (lập: không gồm mua hàng) |
| `purchase-cost-allocation:approve` | Duyệt (khi cấu hình luồng) | `CHIEF_ACCOUNTANT`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `purchase-cost-allocation:reverse` | Đảo | `CHIEF_ACCOUNTANT` |
| `purchase-ledger:view` | Sổ chi tiết mua hàng (V25, 2026-10-14) | `PURCHASING_STAFF`, `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `TAX_ACCOUNTANT` `[Giả định]` |

- Luồng duyệt mẫu dev/test (`R__dev_phase5_purchasing.sql`): DMH do `DIRECTOR` duyệt, một bước.
- Ghi sổ đơn mua cần `purchase-order:post` và phạm vi kho nhập của đơn; không cần quyền phiếu nhập kho riêng. Vai trò mặc định của `:post` `[Giả định]`, cần chủ dự án xác nhận.

### 2.9 Quyền bán hàng (M05, V17..V21, gộp chứng từ D-54 ở V23)
V23 xóa `sales-quote:*`, `sales-delivery:*`, `sales-invoice:*`, `sales-quick-sale:execute`, `sales-order:close`; thêm `sales-order:post`, `sales-order:deliver`, `sales-order:reverse`.

| Quyền | Ý nghĩa | Vai trò mặc định `[Suy luận]` |
|---|---|---|
| `sales-order:view` | Xem đơn bán, tra giá | kinh doanh, đấu thầu, kho, giao hàng, kế toán, thủ quỹ, mua hàng, giám đốc |
| `sales-order:create` | Lập, sửa, gửi, hủy, giữ / nhả hàng | `SALES_MANAGER`, `SALES_STAFF` |
| `sales-order:approve` | Duyệt đơn (giá, hạn mức tín dụng) | `SALES_MANAGER`, `DIRECTOR`, `BRANCH_DIRECTOR` |
| `sales-order:post` | Ghi sổ đơn bán (xuất kho, doanh thu, công nợ, bút toán); ghi số hóa đơn điện tử | `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`, `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT` |
| `sales-order:deliver` | Đang giao, khách nhận | `DELIVERY_STAFF`, `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`, `SALES_MANAGER`, `SALES_STAFF` |
| `sales-order:reverse` | Đảo đơn bán (lập sai, giao thất bại) | `WAREHOUSE_MANAGER`, `CHIEF_ACCOUNTANT` |
| `sales-return:*` | Trả hàng / giảm giá bán: lập (kinh doanh, kế toán), duyệt (`SALES_MANAGER`, giám đốc), ghi sổ (kế toán, kho), đảo (`CHIEF_ACCOUNTANT`) | |
| `sales-complaint:view`, `:create`, `:resolve` | Hàng lỗi khách báo: tiếp nhận (kinh doanh), kiểm tra / quyết định (`SALES_MANAGER`, `QA`, `TECHNICIAN`, giám đốc) | |
| `sales-report:view` | Báo cáo doanh số | `SALES_MANAGER`, `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT` |
| `sales-ledger:view` | Sổ chi tiết bán hàng S16-DNN (V24, 2026-10-14) | `SALES_MANAGER`, `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `TAX_ACCOUNTANT` `[Giả định]` |

- "Chỉ dữ liệu của mình" (`own_data_only`, BR-SYS-04): nhân viên chỉ chọn khách mình phụ trách, chỉ xem đơn / trả hàng / phiếu hàng lỗi mình phụ trách hoặc mình lập (ngoài phạm vi: 404).
- Luồng duyệt mẫu dev/test (`R__dev_phase6_sales.sql`): DBH do `SALES_MANAGER` duyệt một bước; chỉ áp dụng khi có lý do phải duyệt (giá, tín dụng).
- Vai trò mặc định của `:post`, `:deliver`, `:reverse` `[Giả định]`, cần chủ dự án xác nhận.

### 2.10 Quyền báo cáo (M10, Giai đoạn 10A, 10B, 10C, 10D, V26, V27, V28, V29, ADR-18)
| Quyền | Ý nghĩa | Vai trò mặc định |
|---|---|---|
| `inventory-report:view` | Báo cáo kho: thẻ kho, lịch sử serial, nhập - xuất - tồn, tồn lâu, hàng ngoại lệ, thiếu hồ sơ, truy xuất lô / serial (`/reports/...`) | `WAREHOUSE_MANAGER`, `WAREHOUSE_STAFF`, `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `QA` `[Giả định]` |
| `purchase-report:view` | Báo cáo mua: tình hình thực hiện đơn mua, lịch sử giá mua, hàng lỗi theo NCC (V27) | `PURCHASING_STAFF`, `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `TAX_ACCOUNTANT`, `QA` `[Giả định]` |
| `sales-order-report:view` | Báo cáo bán: tình hình thực hiện đơn bán, sổ nhật ký bán hàng, tồn so với đặt hàng chưa giao (V27) | `SALES_MANAGER`, `SALES_STAFF`, `WAREHOUSE_MANAGER`, `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT` `[Giả định]` |
| `dashboard:view` | Dashboard điều hành trên trang chủ: doanh thu, giá trị mua, công nợ, hàng sắp hết hạn, đơn chưa giao đủ, top hàng / khách / NCC và danh sách chi tiết (`/dashboard/...`, V28) | `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `SALES_MANAGER` (chủ dự án duyệt 2026-10-14) |
| `cash-forecast:view` | Dòng tiền dự kiến: báo cáo `/reports/cash-flow-forecast` và thẻ trên dashboard (cần thêm `dashboard:view`) (V29) | `DIRECTOR`, `BRANCH_DIRECTOR`, `CHIEF_ACCOUNTANT`, `AR_ACCOUNTANT`, `CASHIER` `[Giả định]` |

- Dashboard: một quyền xem mọi chỉ tiêu (chủ dự án chọn thay cho "mỗi thẻ theo quyền báo cáo tương ứng"); số liệu theo phạm vi chi nhánh / kho; "chỉ dữ liệu của mình" lọc chứng từ bán, phải thu theo nhân viên phụ trách / người lập, chứng từ mua, phải trả theo người lập `[Suy luận]`.
- Dòng tiền dự kiến: số dư quỹ / ngân hàng và khoản dự kiến theo phạm vi chi nhánh; người "chỉ dữ liệu của mình" chỉ thấy khoản của mình (phải thu, đơn bán theo nhân viên phụ trách / người lập; phải trả, đơn mua theo người lập), không thấy số dư quỹ `[Suy luận]`.
- Báo cáo mua / bán theo phạm vi chi nhánh (chọn chi nhánh ngoài phạm vi: 400 `branchId`); báo cáo bán áp dụng "chỉ dữ liệu của mình".
- Số liệu theo phạm vi kho của người xem (BR-RPT-12): phạm vi toàn công ty xem mọi kho; phạm vi chi nhánh xem kho của chi nhánh và kho được gán; chọn kho ngoài phạm vi: 400 `warehouseId`.
- Màn hình chọn lô / serial dùng API `/inventory/lots`, `/inventory/serials` nên người xem cần thêm `inventory:view`.

## 3. Phạm vi dữ liệu (BR-SYS-04, BR-SYS-06)
| Loại | Ý nghĩa |
|---|---|
| `COMPANY` | Toàn bộ chi nhánh của công ty ("phạm vi toàn công ty") |
| `BRANCH` | Một chi nhánh |
| `WAREHOUSE` | Một kho (chưa hỗ trợ; dùng từ Giai đoạn 2) |
| `own_data_only` (cờ người dùng) | Chỉ thấy dữ liệu do mình phụ trách; áp dụng ở module nghiệp vụ |

Quy tắc áp dụng:
- **Danh sách:** luôn lọc theo phạm vi ở server.
- **Truy cập theo ID ngoài phạm vi:** trả `404`, kể cả khi gọi API trực tiếp, để không tiết lộ dữ liệu có tồn tại.
- **Tạo chi nhánh, sửa công ty:** cần phạm vi toàn công ty. Nếu người dùng chỉ thấy công ty mà không có phạm vi toàn công ty thì trả `403`.
- **Người dùng được thấy:** những người thuộc công ty mà người thao tác có phạm vi toàn công ty, hoặc có chi nhánh mặc định nằm trong phạm vi của người thao tác.
- **Nhật ký đăng nhập:** chỉ xem được với phạm vi toàn công ty.
- **Kỳ:** xem, khóa, mở theo chi nhánh trong phạm vi; chi nhánh ngoài phạm vi trả `404` (cả yêu cầu, không chi nhánh nào bị đổi). Tạo kỳ cần phạm vi toàn công ty.
- **Tham số, cấu hình số chứng từ:** xem khi thấy công ty; sửa cần phạm vi toàn công ty (`403` nếu chỉ có phạm vi chi nhánh).
- **Tạo công ty:** người tạo được tự gán phạm vi `COMPANY` của công ty mới.

## 4. Nhật ký
- `sys_login_logs`: ghi `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `LOGOUT`, `REFRESH_REUSE_DETECTED`, `PASSWORD_CHANGED`, kèm IP, user agent, request ID.
- `sys_audit_logs`:
  - Ghi các thao tác: tạo / sửa / đổi trạng thái công ty, chi nhánh, phòng ban, người dùng, vai trò; gán vai trò, phạm vi, quyền; đặt lại và đổi mật khẩu.
  - 1C: tạo kỳ (`CREATE FISCAL_PERIOD`), khóa / mở kỳ (`LOCK_PERIOD`, `UNLOCK_PERIOD`, kèm chi nhánh, phân hệ, lý do), sửa tham số (`UPDATE PARAMETER`), đặt / hủy cấu hình số chứng từ (`CREATE`/`UPDATE`/`DELETE DOCUMENT_NUMBER_FORMAT`).
  - Lưu giá trị trước và sau. Không bao giờ chứa mật khẩu.

## 5. Người dùng mẫu (chỉ dev/test)
Mật khẩu chung: `Erp@2026` (`db/dev-data/R__dev_organization_and_users.sql`). Không tồn tại ở uat/prod.

| Tên đăng nhập | Vai trò | Phạm vi |
|---|---|---|
| `admin` | SYSTEM_ADMIN | COMPANY 1 |
| `giamdoc` | DIRECTOR | COMPANY 1 |
| `giamdoc.hn` | BRANCH_DIRECTOR | BRANCH 1 (HN) |
| `kinhdoanh.hn` | SALES_STAFF (chỉ dữ liệu của mình) | BRANCH 1 (HN) |
| `kho.hcm` | WAREHOUSE_STAFF | BRANCH 2 (HCM) |
| `ketoantruong` | CHIEF_ACCOUNTANT | COMPANY 1 (thêm ở 1C) |
| `thukho.hn` | WAREHOUSE_MANAGER | BRANCH 1 (HN) (thêm ở Giai đoạn 3) |
| `ketoancongno` | AR_ACCOUNTANT | COMPANY 1 (thêm ở Giai đoạn 4) |
| `thuquy.hn` | CASHIER | BRANCH 1 (HN) (thêm ở Giai đoạn 4) |
| `muahang.hn` | PURCHASING_STAFF | BRANCH 1 (HN) (thêm ở Giai đoạn 5) |
| `truongkd.hn` | SALES_MANAGER | BRANCH 1 (HN) (thêm ở Giai đoạn 6) |
| `giaohang.hn` | DELIVERY_STAFF | BRANCH 1 (HN) (thêm ở Giai đoạn 6) |
| `banhang.hn` | SALES_MANAGER + WAREHOUSE_MANAGER + AR_ACCOUNTANT (kiêm nhiệm) | BRANCH 1 (HN) (thêm ở Giai đoạn 6) |

Chỉ máy dev (profile `dev`, không có ở test): `db/dev-local/R__dev_admin_full_access.sql` gán `admin` vai trò `DEV_FULL_ACCESS` (mọi quyền) và mọi vai trò đang hoạt động để thử nhanh giao diện. Cần thêm `classpath:db/dev-local` vào `spring.flyway.locations` của `application-dev.yml` (người dùng tự thêm). Test phân quyền vẫn dùng `admin` chỉ có quyền M01.

## 6. Kiểm thử chứng minh
| Test | Nội dung |
|---|---|
| `AuthIntegrationTest` (8) | Đăng nhập, cookie, lỗi không phân biệt, khóa sau 5 lần, xoay vòng và phát hiện dùng lại, đăng xuất, bắt buộc đổi mật khẩu, token giả mạo |
| `AuthorizationIntegrationTest` (9) | AT-10: phạm vi chi nhánh (404), thiếu quyền (403), chống trùng khi tạo, trùng mã, sửa đồng thời, chống leo thang quyền, không tự sửa quyền, ngừng hoạt động có hiệu lực ngay, trường sắp xếp không hợp lệ |
| `PeriodLockIntegrationTest` (6) | AT-11 phần khung: kỳ khóa chặn ghi sổ đúng chi nhánh, phân hệ; thứ tự khóa/mở; quyền mở lại và lý do; chi nhánh ngoài phạm vi (404); chống trùng; khóa đồng thời |
| `ParameterIntegrationTest` (4), `ParameterServiceTest` (3) | Tham số: mặc định, sửa đồng thời, kiểm tra giá trị, quyền, khóa sau khi phát sinh số liệu ghi sổ, phạm vi toàn công ty |
| `DocumentNumberFormatIntegrationTest` (4) | Cấu hình số chứng từ: hiệu lực ngay / chu kỳ sau, hủy cấu hình đặt trước, validation, quyền |
| `ApprovalIntegrationTest` (9), `AccessGrantIntegrationTest` (2), `LoginRateLimiterTest` | Luồng duyệt nhiều bước, tách biệt nhiệm vụ, ủy quyền, duyệt đồng thời; gán vai trò vượt quyền với `access:grant-any`; giới hạn đăng nhập theo IP |
| `MasterDataIntegrationTest` (8) | Danh mục: quyền, phạm vi, ẩn giá mua, nhập Excel |
| `DebtIntegrationTest` (4) | AT-10 phần công nợ: kế toán công nợ không tự duyệt / đảo (403), người không có quyền công nợ (403), chứng từ / khoản công nợ chi nhánh khác (404, không thấy trong danh sách) |
| `AccountingIntegrationTest` (5) | AT-10 phần kế toán: kế toán công nợ không thêm được tài khoản (403), người không có `journal:view` xem bút toán (403) |
| `InventoryIntegrationTest` (8) | AT-10 phần kho: thiếu quyền lập (403), chứng từ kho chi nhánh khác (404), chưa đăng nhập (401), người không thuộc luồng duyệt (403), chỉ kho đích nhận hàng chuyển (403) |
| `UserTest`, `PasswordPolicyTest`, `AdminBootstrapTest` | Quy tắc khóa tạm, phiên bản thông tin đăng nhập, chính sách mật khẩu, secret JWT, tạo quản trị viên đầu tiên |

## 7. Hạn chế đã biết
- Giới hạn đăng nhập theo IP (1D): quá `erp.auth.ip-rate-limit.max-failures` (20) lần sai trong `window` (15 phút) từ một IP thì trả `429 TOO_MANY_REQUESTS`. Bộ đếm nằm trong bộ nhớ của một tiến trình: chạy nhiều máy chủ hoặc khởi động lại thì đếm lại từ đầu `[Suy luận]`. IP lấy từ `remoteAddr`; sau reverse proxy cần cấu hình `server.forward-headers-strategy` để lấy IP thật.
- Hai request làm mới phiên gửi đồng thời với cùng một refresh token sẽ bị coi là dùng lại và thu hồi phiên. Frontend đã gộp các lần làm mới trong một tab thành một (`refreshSession`, test `httpClient.test.ts`); mở nhiều tab cùng làm mới đúng thời điểm vẫn có thể đăng xuất một tab `[Suy luận]`.
- Xác thực hai lớp là P2 (BR-SYS-24).
