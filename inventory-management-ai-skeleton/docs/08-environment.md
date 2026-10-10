# Cấu hình môi trường

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 |
| Ngày cập nhật | 2026-10-09 |
| Quy tắc | `.claude/rules/deployment.md`, `.claude/rules/security.md` |
| Thay đổi | Bỏ Docker (D-44): dev/test dùng MySQL local, backend chạy Eclipse + Tomcat 10.1 |

## 1. Phiên bản nền tảng
| Thành phần | Phiên bản |
|---|---|
| JDK | 17 |
| Spring Boot | 3.5.16 |
| Maven | 3.9+ |
| Tomcat | 10.1 (Eclipse khi phát triển, máy chủ khi triển khai) |
| MySQL | 8.4 (dòng 8.x) |
| Node.js | 20 |

## 2. Profile
| Profile | Dùng cho | Dữ liệu mẫu `db/dev-data` | Swagger UI | Log |
|---|---|---|---|---|
| `dev` | Máy lập trình viên | Có | Mở, không cần đăng nhập | Console, `DEBUG` cho `com.company.erp` |
| `test` | Test tự động | Có | API docs bật | Console |
| `uat` | Nghiệm thu | **Không** | Chỉ khi `API_DOCS_ENABLED=true`, cần đăng nhập | Console + file xoay vòng |
| `prod` | Production | **Không** (cấm cứng) | Tắt cứng | Console + file xoay vòng |

Cấu hình gốc (`application.yml`) không có profile mặc định và an toàn cho production (không dữ liệu mẫu, không Swagger).

## 3. File cấu hình riêng của máy
`application.yml` nạp thêm (nếu có) file `~/.erp/application-local.yml` — Windows: `C:\Users\<tên>\.erp\application-local.yml`; đổi vị trí bằng biến `ERP_CONFIG_FILE`.
- File nằm **ngoài repo**: mật khẩu không bị commit, không bị đóng gói vào `erp.war`.
- Mẫu: `backend/config/application-local.example.yml`.
- Phần đầu file: profile `dev` + datasource chạy ứng dụng. Phần `on-profile: test`: datasource cho test tích hợp.
- Máy production **không** đặt file này; cấu hình bằng biến môi trường (mục 4).
- File theo profile (`application-dev.yml`...) có độ ưu tiên **cao hơn** file import này, nên không khai báo datasource trong `application-dev.yml`/`application-test.yml`, nếu không sẽ ghi đè cấu hình máy local.

## 4. Biến môi trường backend (UAT/production, hoặc ghi đè file local)
| Biến | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Có (uat/prod) | — | `dev`, `test`, `uat`, `prod` |
| `DB_URL` | Có, trừ khi có file local | — | JDBC URL, kèm `connectionTimeZone=UTC&characterEncoding=UTF-8` |
| `DB_USERNAME`, `DB_PASSWORD` | Có, trừ khi có file local | — | Tài khoản ứng dụng |
| `DB_POOL_SIZE` | Không | `20` | Số kết nối tối đa |
| `DB_LOCK_WAIT_TIMEOUT_SECONDS` | Không | `10` | Thời gian chờ khóa dòng trước khi trả `423 LOCK_TIMEOUT` |
| `CORS_ALLOWED_ORIGINS` | Khi frontend khác origin | `dev`: `http://localhost:5173` | Domain frontend, phân tách dấu phẩy |
| `API_DOCS_ENABLED` | Không | `false` | Bật OpenAPI/Swagger ở `uat` (luôn tắt ở `prod`) |
| `UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE` | Không | `20MB` / `25MB` | D-41 |
| `ERP_LOG_DIR` | Không | `logs` | Thư mục log file ở `uat`/`prod` |
| `ERP_CONFIG_FILE` | Không | `~/.erp/application-local.yml` | Vị trí file cấu hình riêng máy |
| `ERP_JWT_SECRET` | Có | — | Secret ký JWT (≥ 32 byte, ngẫu nhiên, riêng từng môi trường). Dev đặt trong file local `erp.auth.jwt-secret` |
| `ERP_REFRESH_COOKIE_SECURE` | Không | `true` (`dev`: `false`) | Cờ `Secure` của cookie refresh token; bắt buộc `true` khi chạy HTTPS |
| `ERP_BOOTSTRAP_ADMIN_USERNAME` | Không | `admin` | Tên quản trị viên đầu tiên |
| `ERP_BOOTSTRAP_ADMIN_PASSWORD` | Lần cài đầu (uat/prod) | — | Tạo quản trị viên đầu tiên khi DB chưa có người dùng; bỏ biến này sau khi cài |

Thời hạn phiên, chính sách mật khẩu, khóa tài khoản, giới hạn đăng nhập theo IP (`erp.auth.ip-rate-limit.max-failures`, `window`): `erp.auth.*` trong `application.yml` (docs/07 mục 1). Profile `test` đặt `max-failures` rất lớn để các test đăng nhập sai không chặn nhau.

Tham số nghiệp vụ (không phải biến môi trường) nằm trong bảng `sys_parameters`, sửa ở màn hình Tham số hệ thống: `decimal.*` (D-05), `inventory.expiry-warning-days` (cảnh báo hạn dùng, V7).

Múi giờ nghiệp vụ `erp.calendar.time-zone` (mặc định `Asia/Ho_Chi_Minh`, D-15): xác định "hôm nay" cho ngày chứng từ và chu kỳ số chứng từ. Thời điểm vẫn lưu UTC.

## 5. Thiết lập máy phát triển
### 5.1 MySQL local
Chạy bằng tài khoản quản trị MySQL (một lần):
```sql
CREATE DATABASE IF NOT EXISTS inventory_db      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS inventory_db_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'inventory_db'@'localhost' IDENTIFIED BY '<mật khẩu>';
GRANT ALL PRIVILEGES ON inventory_db.*      TO 'inventory_db'@'localhost';
GRANT ALL PRIVILEGES ON inventory_db_test.* TO 'inventory_db'@'localhost';
FLUSH PRIVILEGES;
```
- `inventory_db`: chạy ứng dụng; Flyway tạo bảng khi khởi động.
- `inventory_db_test`: test tích hợp **xóa sạch** schema này mỗi lần chạy. Test từ chối chạy nếu tên schema không có hậu tố `_test`.

### 5.2 Backend bằng Eclipse + Tomcat 10.1
1. Import: *File → Import → Maven → Existing Maven Projects* → chọn `backend/`.
2. JDK của project: 17.
3. Thêm server *Apache Tomcat v10.1* (JRE 17) trong view *Servers*, *Add and Remove…* → thêm `erp`.
4. Context path: `/erp` (cố định bằng `m2e.wtp.contextRoot` trong `pom.xml`).
5. Start server. Kiểm tra:
   - Health: `http://localhost:8080/erp/actuator/health` → `{"status":"UP"}`
   - Swagger UI (dev): `http://localhost:8080/erp/swagger-ui.html`

Cách khác: *Run As → Spring Boot App / Java Application* trên `ErpApplication`, hoặc `cd backend && mvn spring-boot:run`. Tomcat nhúng cũng dùng context `/erp` (`server.servlet.context-path` trong `application.yml`), nên frontend không cần đổi cấu hình.

### 5.3 Frontend
```bash
cd frontend
npm ci
npm run dev        # http://localhost:5173
```
Frontend gọi `/erp/api/v1/...` cùng origin như production; Vite chuyển tiếp `/erp` sang `ERP_BACKEND_URL` (mặc định `http://localhost:8080`). Nhờ vậy cookie refresh token (`Path=/erp/api/v1/auth`) khớp đường dẫn trình duyệt.
Proxy giữ nguyên header `Host` (`changeOrigin: false`), giống reverse proxy production: backend thấy `Origin` trùng `Host` nên coi là cùng origin, không áp CORS; mở bằng `localhost`, `127.0.0.1` hay IP LAN đều được. Reverse proxy ở UAT/production cũng phải giữ nguyên `Host` (hoặc cấu hình `CORS_ALLOWED_ORIGINS` đúng domain), nếu không backend trả `403` cho request có `Origin`.

### 5.4 Test
```bash
cd backend  && mvn clean verify          # đọc datasource test từ ~/.erp/application-local.yml
cd frontend && npm run lint && npm test && npm run build
```
Không có file local và không có `ERP_TEST_DB_URL` thì test tích hợp bị bỏ qua và **chưa được tính là đạt**. CI dùng `ERP_TEST_DB_URL`, `ERP_TEST_DB_USERNAME`, `ERP_TEST_DB_PASSWORD`.
