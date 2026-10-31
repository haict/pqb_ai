# Cấu hình môi trường

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.3 |
| Ngày cập nhật | 2026-10-14 |
| Quy tắc | `.claude/rules/deployment.md`, `.claude/rules/security.md` |
| Thay đổi | Bỏ Docker (D-44): dev/test dùng MySQL local, backend chạy Eclipse + Tomcat 10.1. 0.3: ngoại lệ dữ liệu mẫu trên máy chủ UAT (mục 2.1) |

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

Thư mục `db/dev-local` (chỉ dev, tùy chọn): dữ liệu tiện thử nghiệm trên máy cá nhân (admin toàn quyền); không nằm trong profile `test`, không bao giờ ở `uat`/`prod`. File dùng placeholder `${flyway:timestamp}` để chạy lại mỗi lần khởi động `[Chưa kiểm chứng]`.

Cấu hình gốc (`application.yml`) không có profile mặc định và an toàn cho production (không dữ liệu mẫu, không Swagger).

### 2.1 Ngoại lệ: dữ liệu mẫu trên máy chủ UAT (2026-10-14)
Lệch khỏi bảng mục 2 (`uat`: không dữ liệu mẫu). Chủ dự án yêu cầu, áp dụng tạm thời cho đợt UAT hiện tại.

| Mục | Nội dung |
|---|---|
| Phạm vi | Máy chủ UAT: service Windows `Tomcat10`, database `erp`, file cấu hình `D:\erp\config\application-prod.yml` (`ERP_CONFIG_FILE`) |
| Hiện trạng | Database `erp` đã chạy các script `db/dev-data/R__dev_*` (lần khởi động trước bằng profile `dev`). Service chạy profile `prod`, không có `db/dev-data` trong `spring.flyway.locations`, nên Flyway báo `Detected applied migration not resolved locally` và ứng dụng không khởi động |
| Xử lý | Chỉ bỏ qua migration có trong lịch sử nhưng không có trong classpath, đặt trong file cấu hình ngoài (không sửa mã, không sửa migration):<br>`spring.flyway.ignore-migration-patterns: "*:missing"` `[Chưa kiểm chứng]` trên máy chủ |
| Không dùng | `spring.flyway.validate-on-migrate: false` (tắt cả kiểm tra checksum migration `V*`), `flyway repair` |
| Rủi ro | Người dùng mẫu (`admin`, `giamdoc`, `kinhdoanh.hn`, `kho.hcm`...) dùng chung mật khẩu, mã băm công khai trong `R__dev_organization_and_users.sql` → đổi mật khẩu hoặc khóa ngay sau khi triển khai nếu máy chủ truy cập được qua mạng.<br>Dữ liệu mẫu không tự cập nhật: profile `prod`/`uat` không chạy lại `R__dev_*` |
| Điều kiện kết thúc | Trước UAT chính thức hoặc production: tạo database trống, bỏ `ignore-migration-patterns`, khởi động bằng `uat`/`prod` để Flyway chỉ chạy `db/migration` |

Không áp dụng ngoại lệ này cho production: `db/dev-data` không bao giờ được cấu hình ở `prod` (`.claude/rules/database.md` mục 2).

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
| `ERP_LICENSE_FILE` | Không | — | Đường dẫn file license nạp tự động khi khởi động nếu mới hơn license đang dùng (D-59), ví dụ `file:/opt/erp/license.lic`. Trống: nạp qua màn hình Quản trị › Bản quyền phần mềm. Không có biến nào tắt kiểm tra license |
| `ERP_REFRESH_COOKIE_SECURE` | Không | `true` (`dev`: `false`) | Cờ `Secure` của cookie refresh token; bắt buộc `true` khi chạy HTTPS |
| `ERP_BOOTSTRAP_ADMIN_USERNAME` | Không | `admin` | Tên quản trị viên đầu tiên |
| `ERP_BOOTSTRAP_ADMIN_PASSWORD` | Lần cài đầu (uat/prod) | — | Tạo quản trị viên đầu tiên khi DB chưa có người dùng; bỏ biến này sau khi cài |

Thời hạn phiên, chính sách mật khẩu, khóa tài khoản, giới hạn đăng nhập theo IP (`erp.auth.ip-rate-limit.max-failures`, `window`): `erp.auth.*` trong `application.yml` (docs/07 mục 1). Profile `test` đặt `max-failures` rất lớn để các test đăng nhập sai không chặn nhau.

Tham số nghiệp vụ (không phải biến môi trường) nằm trong bảng `sys_parameters`, sửa ở màn hình Tham số hệ thống: `decimal.*` (D-05), `inventory.expiry-warning-days` (cảnh báo hạn dùng, V7), `inventory.slow-moving-days` (số ngày không xuất để coi là tồn lâu, mặc định 180 `[Giả định]`, V26).

Múi giờ nghiệp vụ `erp.calendar.time-zone` (mặc định `Asia/Ho_Chi_Minh`, D-15): xác định "hôm nay" cho ngày chứng từ và chu kỳ số chứng từ. Thời điểm vẫn lưu UTC.

### 4.1 Tra cứu mã số thuế (`erp.tax-code-lookup.*`, D-66)
| Biến môi trường / khóa | Ý nghĩa | Mặc định |
|---|---|---|
| `TAX_CODE_LOOKUP_ENABLED` / `erp.tax-code-lookup.enabled` | Bật nút tra cứu MST khi thêm / sửa đối tác; máy chủ không ra được Internet thì đặt `false` (API trả 503, vẫn nhập tay) | `true` |
| `TAX_CODE_LOOKUP_BASE_URL` / `erp.tax-code-lookup.base-url` | Địa chỉ nguồn tra cứu; mã số thuế nối vào cuối đường dẫn. Máy chủ phải ra được `https://api.xinvoice.vn` (cổng 443) | `https://api.xinvoice.vn/gdt-api/tax-payer` |
| `TAX_CODE_LOOKUP_CLIENT_ID` / `erp.tax-code-lookup.client-id` | `client-id` cấp khi đăng ký API Xinvoice; trống thì không gửi header | trống |
| `TAX_CODE_LOOKUP_API_KEY` / `erp.tax-code-lookup.api-key` | `api-key` đi cùng `client-id` (secret, không commit); trống thì không gửi header | trống |
| `erp.tax-code-lookup.connect-timeout`, `erp.tax-code-lookup.read-timeout` | Chờ kết nối; chờ phản hồi | `3s`, `8s` |

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

## 6. AI Service (D-64, `ai-service/`)
Python 3.11, chạy bằng `python -m app` trong venv `ai-service/.venv`. Cấu hình bằng biến môi trường; máy dev đặt trong `~/.erp/ai-service.env` (ngoài repo) và trỏ `AI_SERVICE_ENV_FILE` tới file đó. Mẫu: `ai-service/.env.example`. Hướng dẫn cài: `ai-service/README.md`.

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `AI_SERVICE_ENV_FILE` | Đường dẫn file biến môi trường (chỉ dùng ở dev) | không có |
| `AI_SERVICE_HOST`, `AI_SERVICE_PORT` | Địa chỉ lắng nghe; chỉ `127.0.0.1` trừ khi tách máy AI (Q-08) | `127.0.0.1`, `8090` |
| `AI_SERVICE_API_KEY` | Secret header `X-AI-Service-Key`, trùng `ai.service.api-key` của Spring; ≥ 16 ký tự | bắt buộc |
| `AI_SERVICE_ENABLE_DOCS` | Bật `/docs` (Swagger); chỉ dev | `false` |
| `OCR_MODEL_DIR` | Thư mục model cài sẵn, mỗi model một thư mục con | bắt buộc |
| `OCR_DET_MODEL`, `OCR_REC_MODEL` | Tên model (docs/14 mục 9.4) | `PP-OCRv5_mobile_det`, `latin_PP-OCRv5_mobile_rec` |
| `OCR_CPU_THREADS` | Số luồng CPU của PaddleOCR | `4` |
| `OCR_MAX_FILE_SIZE` | Dung lượng tối đa (byte) | `10485760` |
| `OCR_MAX_PDF_PAGES` | Số trang PDF tối đa | `5` |
| `OCR_MAX_IMAGE_PIXELS` | Số điểm ảnh tối đa mỗi trang | `40000000` |
| `OCR_MAX_IMAGE_SIDE` | Cạnh dài tối đa sau khi thu nhỏ (px) | `2500` |
| `OCR_PDF_DPI` | Độ phân giải render PDF | `144` |
| `OCR_MIN_CONFIDENCE` | Dưới ngưỡng thì trường `LOW_CONFIDENCE` | `0.85` |
| `OCR_TEXT_DIACRITICS_RELIABLE` | `false`: trường văn bản luôn `LOW_CONFIDENCE` (model thiếu chữ có dấu) | `false` |
| `OLLAMA_BASE_URL` | Ollama nội bộ (AI-3) | `http://127.0.0.1:11434` |
| `CHAT_MODEL` | Model chat cài sẵn trên Ollama (không tự tải) | `qwen2.5:3b` |
| `CHAT_TIMEOUT_SECONDS` | Chờ Ollama trả lời; ngắn hơn `ai.chat.timeout` của Spring | `80` |
| `CHAT_MAX_ANSWER_TOKENS`, `CHAT_TEMPERATURE`, `CHAT_CONTEXT_TOKENS` | Giới hạn câu trả lời, độ ngẫu nhiên, ngữ cảnh model | `400`, `0.2`, `4096` |
| `CHAT_MAX_QUESTION_CHARS` | Độ dài câu hỏi tối đa | `2000` |
| `RAG_INDEX_DIR` | Thư mục chỉ mục tài liệu hướng dẫn (`guide-index.json`); thiếu thì trợ lý không khả dụng, OCR vẫn chạy | không có |
| `RAG_TOP_K`, `RAG_MIN_SCORE`, `RAG_MAX_CONTEXT_CHARS` | Số đoạn tài liệu tối đa; điểm BM25 tối thiểu (thấp hơn thì trả "chưa có trong tài liệu", không gọi LLM); tổng ký tự đoạn đưa vào prompt | `3`, `5.0`, `6000` |
| `LOG_LEVEL` | Mức log | `INFO` |

### 6.1 Backend gọi AI Service (`ai.*` trong `application.yml`, AI-1b)
| Biến môi trường / khóa | Ý nghĩa | Mặc định |
|---|---|---|
| `AI_ENABLED` / `ai.enabled` | Bật chức năng AI; tắt thì nút nhận dạng ẩn, API trả 503 | `false` |
| `AI_SERVICE_BASE_URL` / `ai.service.base-url` | Địa chỉ AI Service | `http://127.0.0.1:8090` |
| `AI_SERVICE_API_KEY` / `ai.service.api-key` | Trùng `AI_SERVICE_API_KEY` của AI Service; bắt buộc khi bật (≥ 16 ký tự, không khởi động được nếu thiếu) | trống |
| `ai.service.connect-timeout`, `ai.service.health-interval` | Chờ kết nối; chu kỳ kiểm tra AI Service | `2s`, `30s` |
| `ai.ocr.timeout`, `ai.ocr.max-file-size`, `ai.ocr.max-image-pixels` | Thời gian chờ nhận dạng; giới hạn file | `60s`, `10MB`, `40000000` |
| `ai.ocr.max-concurrent`, `ai.ocr.acquire-timeout` | Số yêu cầu OCR cùng lúc; chờ suất trước khi trả `AI_BUSY` | `1`, `3s` |
| `ai.ocr.amount-tolerance`, `ai.ocr.max-candidates`, `ai.ocr.name-similarity` | Dung sai kiểm tra tiền (đồng); số ứng viên danh mục; độ giống tối thiểu của tên hàng / tên đối tác | `1`, `10`, `0.85` |
| `ai.chat.timeout`, `ai.chat.max-concurrent`, `ai.chat.acquire-timeout` | Chờ một câu trả lời; số câu hỏi xử lý cùng lúc; chờ suất trước khi trả `AI_BUSY` (AI-3) | `90s`, `1`, `3s` |
| `ai.chat.max-message-chars`, `ai.chat.max-history-turns`, `ai.chat.max-context-chars` | Độ dài câu hỏi; số tin nhắn trước gửi kèm; tổng ký tự gửi kèm | `2000`, `10`, `12000` |
| `ai.chat.retention-days`, `ai.chat.retention-cron` | Giữ hội thoại (Q-05); lịch xóa hội thoại quá hạn (`-` để tắt) | `90`, `0 30 2 * * *` |
| `ai.rate-limit.per-user-per-minute` | Số câu hỏi tối đa của một người trong 60 giây (đếm trong bộ nhớ, một máy chủ) | `10` |

Máy dev: đặt `ai.enabled: true`, `ai.service.base-url`, `ai.service.api-key` trong `~/.erp/application-local.yml` rồi chạy AI Service (`ai-service/README.md`). Test tự động (`application-test.yml`) bật AI với máy chủ giả lập trong test ở cổng `18090`, không cần Python.

Chỉ mục tài liệu hướng dẫn cho trợ lý (AI-3), chạy lại mỗi khi `docs/13-huong-dan-su-dung.docx` thay đổi rồi khởi động lại AI Service:
```
cd ai-service
.venv\Scripts\python -m tools.build_rag_index --source ..\docs\13-huong-dan-su-dung.docx --out %USERPROFILE%\.erp\ai-rag --exclude 4,13
```
Mặc định bỏ chương 4 (việc chờ duyệt, ủy quyền duyệt: đã bỏ theo D-61) và chương 13 (chức năng chưa có). Máy dev đặt `RAG_INDEX_DIR` trong `~/.erp/ai-service.env`.

Test: `cd ai-service && .venv\Scripts\python -m pytest`. Test model thật cần thêm `OCR_MODEL_DIR` và `AI_SAMPLES_DIR` (bộ chứng từ giả lập `~/.erp/ai-samples/synthetic`); thiếu thì bị bỏ qua và **chưa được tính là đạt**.
