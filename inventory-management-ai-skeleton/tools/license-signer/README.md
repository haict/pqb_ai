# Công cụ ký license (D-59)

Công cụ **chỉ dành cho nhà cung cấp**, chạy trên máy của nhà cung cấp. Không đóng gói vào WAR, không gửi cho khách.
Cần Java 17; không cần build: `java LicenseSigner.java <lệnh> ...`.

## Nguyên tắc
- **Private key** ký license: chỉ nhà cung cấp giữ. Cất ngoài repo (ví dụ ổ mã hóa, USB két sắt), sao lưu ít nhất 2 nơi.
  Mất private key thì không cấp được license mới cho các bản đã cài; lộ private key thì ai cũng tự cấp license được.
- **Public key** kiểm tra license: nằm trong `backend/src/main/resources/license/public-keys.properties`, đóng gói cùng WAR.
- Không commit file `*.private.key` (đã chặn trong `.gitignore`; ngoại lệ duy nhất là khóa `test` của test tự động).

## 1. Tạo cặp khóa (một lần)
```
java LicenseSigner.java keygen --key-id vendor-2026 --out D:/secure/license-keys
```
Thêm dòng `vendor-2026=...` in ra vào `public-keys.properties`, build lại WAR.

Đổi khóa: tạo khóa mới (`vendor-2027`), **thêm** dòng mới vào `public-keys.properties` (giữ khóa cũ đến khi các license cũ hết hạn).

## 2. Ký license cho khách (mỗi kỳ thu phí)
Tạo file tham số UTF-8, ví dụ `pqb-2026-11.properties`:
```
key-id=vendor-2026
license-id=PQB-2026-11
customer=CÔNG TY TNHH CÔNG NGHỆ Y TẾ & GIÁO DỤC PQB
tax-codes=0106901068,0108267224
valid-to=2026-11-30
grace-days=15
```
```
java LicenseSigner.java sign --params pqb-2026-11.properties --private-key D:/secure/license-keys/vendor-2026.private.key --out pqb-2026-11.lic
java LicenseSigner.java verify --public-key D:/secure/license-keys/vendor-2026.public.key --license pqb-2026-11.lic
```
Dùng file tham số khi có tiếng Việt (tham số dòng lệnh trên Windows có thể sai mã ký tự).

- `tax-codes`: mã số thuế mọi công ty khách được dùng; công ty đang hoạt động ngoài danh sách làm hệ thống chỉ đọc.
- `valid-to`: ngày hết hạn (theo lịch Việt Nam); `grace-days`: số ngày ân hạn sau đó vẫn ghi được (mặc định 15).
- Ngày cấp (`issuedAt`) lấy theo giờ máy ký; ký trên máy có giờ đúng.

## 3. Khách nạp license
- Quản trị hệ thống: menu **Quản trị hệ thống › Bản quyền phần mềm** › chọn file `.lic` › **Nạp license**; hoặc
- Đặt biến môi trường `ERP_LICENSE_FILE=file:/đường/dẫn/license.lic` (nạp khi khởi động nếu mới hơn license đang dùng).

## Hành vi khi license không hiệu lực
Hệ thống **chỉ đọc**: vẫn đăng nhập, xem, in, xuất dữ liệu, nạp license, ngừng hoạt động công ty; mọi thao tác ghi khác
trả `403 LICENSE_INACTIVE`. Nạp license hợp lệ là mở lại ngay, không mất dữ liệu.

[Suy luận] Người có quyền quản trị máy chủ và kỹ năng dịch ngược vẫn có thể vượt qua cơ chế này; hợp đồng dịch vụ là căn
cứ chính để thu phí.
