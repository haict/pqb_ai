# Xác thực và phân quyền

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.3 (Giai đoạn 1A, 1C, 1D, 2, 3) |
| Ngày cập nhật | 2026-10-10 |
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

Luồng duyệt mẫu (chỉ dev/test, `R__dev_phase3_inventory.sql`): mọi loại chứng từ kho bắt buộc duyệt do vai trò `DIRECTOR` duyệt một bước; chứng từ đảo do `CHIEF_ACCOUNTANT` duyệt.

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
| `InventoryIntegrationTest` (8) | AT-10 phần kho: thiếu quyền lập (403), chứng từ kho chi nhánh khác (404), chưa đăng nhập (401), người không thuộc luồng duyệt (403), chỉ kho đích nhận hàng chuyển (403) |
| `UserTest`, `PasswordPolicyTest`, `AdminBootstrapTest` | Quy tắc khóa tạm, phiên bản thông tin đăng nhập, chính sách mật khẩu, secret JWT, tạo quản trị viên đầu tiên |

## 7. Hạn chế đã biết
- Giới hạn đăng nhập theo IP (1D): quá `erp.auth.ip-rate-limit.max-failures` (20) lần sai trong `window` (15 phút) từ một IP thì trả `429 TOO_MANY_REQUESTS`. Bộ đếm nằm trong bộ nhớ của một tiến trình: chạy nhiều máy chủ hoặc khởi động lại thì đếm lại từ đầu `[Suy luận]`. IP lấy từ `remoteAddr`; sau reverse proxy cần cấu hình `server.forward-headers-strategy` để lấy IP thật.
- Hai request làm mới phiên gửi đồng thời với cùng một refresh token sẽ bị coi là dùng lại và thu hồi phiên. Frontend đã gộp các lần làm mới trong một tab thành một (`refreshSession`, test `httpClient.test.ts`); mở nhiều tab cùng làm mới đúng thời điểm vẫn có thể đăng xuất một tab `[Suy luận]`.
- Xác thực hai lớp là P2 (BR-SYS-24).
