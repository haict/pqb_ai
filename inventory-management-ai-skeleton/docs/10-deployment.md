# Triển khai

Tài liệu bắt buộc số 11 (`.claude/rules/documentation.md`). Phiên bản 0.1 (2026-11-09).

| Mục | Nội dung | Trạng thái |
|---|---|---|
| 1 | Yêu cầu hệ thống | Chưa viết |
| 2 | Build WAR và frontend | Chưa viết |
| 3 | Cấu hình Tomcat 10.1, triển khai frontend | Chưa viết |
| 4 | Flyway, kiểm tra sau triển khai, rollback | Chưa viết |
| 5 | AI Service: nhận dạng chứng từ (OCR) trên máy chủ Windows | Có |

Biến môi trường backend: `docs/08-environment.md` mục 4, 6. Thiết kế AI / OCR: `docs/14-ai-assistant-ocr.md`.

---

## 5. AI Service: nhận dạng chứng từ (OCR) trên máy chủ Windows

Dành cho người cài đặt tại máy khách. Phạm vi: **chỉ OCR**, không cài Ollama, không bật trợ lý hỏi đáp. OCR không cần
Ollama: thiếu Ollama thì nút trợ lý tự ẩn, nút "Nhận dạng chứng từ" vẫn hoạt động (docs/14 mục 2, BR-AI-09).

### 5.1 Kiến trúc trên máy khách
```
Trình duyệt ──> Tomcat (erp.war) ──127.0.0.1:8090──> AI Service (Python, Task Scheduler) ──> PaddleOCR (model trên đĩa)
```
- AI Service chỉ nghe `127.0.0.1`: chỉ Tomcat trên cùng máy gọi được; trình duyệt không gọi trực tiếp (BR-AI-01).
- Không gửi dữ liệu ra Internet, không lưu file chứng từ, không ghi nội dung chứng từ vào log (BR-AI-10).

### 5.2 Yêu cầu
| Hạng mục | Yêu cầu |
|---|---|
| Hệ điều hành | Windows 10 / 11 / Server 2016 trở lên, 64-bit |
| Python | **3.11** 64-bit (đã đo với 3.11.9) |
| RAM trống thêm | Khoảng **1,2 GB** lúc nhận dạng (đo trên máy dev, docs/14 mục 9.4). `[Đề xuất]` Chỉ bật khi RAM trống lúc cao điểm còn ≥ 3 GB |
| CPU | 4 nhân trở lên; một trang khoảng 4–7 giây (đo trên Core i5-10400) |
| Đĩa | Khoảng 2 GB (thư viện Python + model) |
| Internet | Không cần trên máy khách nếu chuẩn bị gói cài sẵn (mục 5.3) |
| ERP | Đã chạy migration V39 (quyền `ocr:use`); license còn hiệu lực (license hết hạn thì OCR trả `LICENSE_INACTIVE`) |

### 5.3 Chuẩn bị gói cài trên máy có Internet
Làm trên máy Windows 64-bit có **cùng phiên bản Python 3.11** với máy khách.

```powershell
# 1. Thư viện Python (bánh xe .whl) để cài ngoại tuyến
cd <repo>\ai-service
py -3.11 -m pip download -r requirements.txt -d C:\ERP-setup\wheels

# 2. Mã nguồn AI Service: chỉ app\, deploy\, requirements.txt (không chép tests\, tools\, .venv\)
robocopy app C:\ERP-setup\ai-service\app /E /XD __pycache__
robocopy deploy C:\ERP-setup\ai-service\deploy /E
copy requirements.txt C:\ERP-setup\ai-service\

# 3. Model PaddleOCR (đã tải sẵn trên máy dev) và checksum
robocopy $HOME\.paddlex\official_models\PP-OCRv5_mobile_det C:\ERP-setup\ai-models\PP-OCRv5_mobile_det /E
robocopy $HOME\.paddlex\official_models\latin_PP-OCRv5_mobile_rec C:\ERP-setup\ai-models\latin_PP-OCRv5_mobile_rec /E
Get-ChildItem C:\ERP-setup\ai-models -Recurse -File | Get-FileHash | Export-Csv C:\ERP-setup\ai-models-sha256.csv -NoTypeInformation
```
Kèm bộ cài Python 3.11 64-bit. Chép cả thư mục `C:\ERP-setup` sang máy khách.

### 5.4 Cài trên máy khách
Thư mục gợi ý (đổi được, nhớ truyền tham số tương ứng ở mục 5.6):

| Thư mục | Nội dung |
|---|---|
| `C:\ERP\ai-service` | Mã nguồn + môi trường ảo `.venv` |
| `C:\ERP\ai-models` | Hai thư mục model |
| `C:\ERP\config\ai-service.env` | Cấu hình, **có secret** |
| `C:\ERP\logs` | Log `ai-service.log` |

```powershell
# PowerShell chạy bằng quyền Administrator
# 1. Cài Python 3.11 64-bit (chọn "Add python.exe to PATH" không bắt buộc; lệnh dưới dùng py launcher)
# 2. Chép mã nguồn và model
robocopy C:\ERP-setup\ai-service C:\ERP\ai-service /E
robocopy C:\ERP-setup\ai-models C:\ERP\ai-models /E

# 3. Môi trường ảo + thư viện, cài ngoại tuyến. Thư mục cài phải có đường dẫn NGẮN (ví dụ C:\ERP\ai-service):
#    paddlepaddle có file nằm sâu, đường dẫn dài vượt giới hạn 260 ký tự của Windows thì pip báo lỗi
#    "No such file or directory ... Long Path". Bắt buộc cài thư mục dài: bật LongPathsEnabled (cần Administrator).
py -3.11 -m venv C:\ERP\ai-service\.venv
C:\ERP\ai-service\.venv\Scripts\python -m pip install --no-index --find-links C:\ERP-setup\wheels -r C:\ERP\ai-service\requirements.txt

# 4. Đối chiếu checksum model với file đã lập ở mục 5.3 (không có dòng nào là đúng)
$expected = Import-Csv C:\ERP-setup\ai-models-sha256.csv
$actual = Get-ChildItem C:\ERP\ai-models -Recurse -File | Get-FileHash
Compare-Object ($expected.Hash) ($actual.Hash)
```

### 5.5 Cấu hình
**a) Sinh khóa dùng chung giữa Tomcat và AI Service** (mỗi máy khách một khóa riêng, không dùng lại khóa dev):
```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes($bytes)
-join ($bytes | ForEach-Object { $_.ToString('x2') })
```

**b) `C:\ERP\config\ai-service.env`**
```ini
AI_SERVICE_HOST=127.0.0.1
AI_SERVICE_PORT=8090
AI_SERVICE_API_KEY=<khóa ở bước a>
AI_SERVICE_ENABLE_DOCS=false
OCR_MODEL_DIR=C:\ERP\ai-models
OCR_CPU_THREADS=4
LOG_LEVEL=INFO
```
- **Không khai báo** `RAG_INDEX_DIR`, `OLLAMA_*`: trợ lý tắt, AI Service không gọi Ollama.
- Máy yếu / RAM, CPU đang cao: giảm `OCR_CPU_THREADS` xuống `2` (chậm hơn, nhẹ máy hơn) `[Chưa kiểm chứng]` mức chậm.
- Các biến khác giữ mặc định: `docs/08-environment.md` mục 6.
- Giới hạn quyền đọc file (chỉ Administrators; script mục 5.6 cấp thêm quyền đọc cho tài khoản chạy dịch vụ):
  ```powershell
  icacls C:\ERP\config\ai-service.env /inheritance:r /grant "Administrators:F"
  ```

**c) Backend (Tomcat)**: ba biến, giá trị khóa trùng bước a.

| Cách chạy Tomcat | Đặt ở đâu |
|---|---|
| `startup.bat` | `%CATALINA_BASE%\bin\setenv.bat`: `set "AI_ENABLED=true"`, `set "AI_SERVICE_BASE_URL=http://127.0.0.1:8090"`, `set "AI_SERVICE_API_KEY=<khóa>"` |
| Windows service (`Tomcat10w.exe`) | Tab **Java** › Java Options, mỗi dòng một tham số: `-Dai.enabled=true`, `-Dai.service.base-url=http://127.0.0.1:8090`, `-Dai.service.api-key=<khóa>` |

Khởi động lại Tomcat sau khi đổi. Thiếu khóa khi `ai.enabled=true` thì backend **không khởi động** (cố ý, `AiProperties`).

### 5.6 Đăng ký chạy nền (Task Scheduler)
`[Đề xuất]` Dùng Task Scheduler có sẵn của Windows (không thêm công cụ ngoài như NSSM / WinSW): tự chạy khi khởi động
máy, tự chạy lại sau 1 phút nếu dừng do lỗi, độ ưu tiên CPU thấp hơn Tomcat / MySQL (docs/14 mục 9.2). Docs/14 mục 12
để ngỏ công cụ này; đổi công cụ phải cập nhật mục này.

```powershell
# PowerShell chạy bằng quyền Administrator
cd C:\ERP\ai-service\deploy\windows
powershell -ExecutionPolicy Bypass -File .\install-ai-service-task.ps1
# Cài thư mục khác: -AiHome D:\ERP\ai-service -EnvFile D:\ERP\config\ai-service.env -LogDir D:\ERP\logs
```
- Script: `ai-service/deploy/windows/install-ai-service-task.ps1` (đăng ký tác vụ "ERP AI Service", cấp quyền thư mục,
  khởi động ngay) và `run-ai-service.cmd` (chạy `python -m app`, ghi log `C:\ERP\logs\ai-service.log`).
- Tài khoản chạy mặc định: `NT AUTHORITY\LOCALSERVICE` (quyền thấp). `[Chưa kiểm chứng]` Đã chạy thử
  `run-ai-service.cmd` trên máy dev (OCR sẵn sàng, ghi log đúng), **chưa** chạy thử đăng ký Task Scheduler và tài
  khoản LOCALSERVICE. Nếu tác vụ không lên (mục 5.7 bước 1 không đạt, log trống), cài lại với `-RunAs SYSTEM`.
- Gỡ: `.\install-ai-service-task.ps1 -Uninstall`.
- Dừng / chạy tay: `Stop-ScheduledTask -TaskName 'ERP AI Service'` / `Start-ScheduledTask -TaskName 'ERP AI Service'`.

**Firewall**: AI Service chỉ nghe `127.0.0.1` nên máy khác không gọi được; không cần mở cổng 8090. Không mở cổng này
trên firewall.

### 5.7 Kiểm tra sau cài
| # | Kiểm tra | Đạt khi |
|---|---|---|
| 1 | `Get-Content C:\ERP\logs\ai-service.log -Tail 20` (đợi 20–30 giây sau khi khởi động) | Có dòng `OCR sẵn sàng: det=PP-OCRv5_mobile_det rec=latin_PP-OCRv5_mobile_rec` |
| 2 | `Invoke-RestMethod http://127.0.0.1:8090/internal/v1/health -Headers @{ 'X-AI-Service-Key' = '<khóa>' }` | `status` = `UP`, `ocr.ready` = `True`; `chat.ready` = `False` là đúng (không cài trợ lý) |
| 3 | Đăng nhập ERP bằng người có quyền lập đơn mua, mở form **Đơn mua** › chọn chi nhánh | Có nút **Nhận dạng chứng từ** (backend kiểm tra AI Service 30 giây một lần: chờ tối đa 30 giây sau khi AI Service lên) |
| 4 | Nhận dạng một phiếu mẫu (ảnh JPG / PNG / PDF ≤ 10 MB) | Form được điền, có ghi chú các trường cần kiểm tra; **không** tự lưu đơn |
| 5 | Theo dõi RAM 1–2 ngày (`Get-Process python, java, mysqld`) | RAM trống lúc cao điểm không xuống dưới khoảng 1 GB; người dùng không thấy ERP chậm |

### 5.8 Xử lý sự cố
| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Không có nút "Nhận dạng chứng từ" | Tomcat chưa bật `ai.enabled`; khóa hai bên khác nhau (AI Service trả 401); AI Service chưa chạy / đang nạp model; người dùng thiếu quyền `ocr:use` hoặc `purchase-order:create`; chức năng bị tắt theo khách hàng (`sys_ui_features`, khóa `purchase-order.ocr`, D-72) | Kiểm tra mục 5.7 bước 1, 2; so khóa; xem quyền vai trò; khởi động lại Tomcat sau khi sửa |
| Log có `Thiếu model` / dừng ngay khi khởi động | `OCR_MODEL_DIR` sai, thiếu thư mục model | Đường dẫn trỏ tới thư mục **chứa** hai thư mục model |
| Báo "AI đang bận" (`AI_BUSY`) | Một lần chỉ nhận dạng một chứng từ (`ai.ocr.max-concurrent` = 1) | Thử lại sau vài giây |
| Báo quá thời gian (`AI_TIMEOUT`) | Máy chậm, PDF nhiều trang | Chia nhỏ PDF; tăng `ai.ocr.timeout` (mặc định 60 giây) trong cấu hình Tomcat |
| Báo bản quyền (`LICENSE_INACTIVE`) | License ERP hết hạn | Nạp license mới (D-59) |
| ERP chậm khi đang nhận dạng | Thiếu RAM / CPU | Giảm `OCR_CPU_THREADS`; tắt OCR (mục 5.9) |

### 5.9 Cập nhật, tắt, gỡ
- **Cập nhật** phiên bản AI Service: `Stop-ScheduledTask` › sao lưu thư mục `C:\ERP\ai-service\app` hiện tại › chép
  `app\` mới (và cài lại thư viện nếu `requirements.txt` đổi) › `Start-ScheduledTask` › kiểm tra mục 5.7. Cập nhật
  cùng đợt với `erp.war` và frontend khi thay đổi có liên quan (ví dụ thêm loại chứng từ).
- **Rollback**: dừng tác vụ, chép lại thư mục `app` đã sao lưu, chạy lại.
- **Tắt OCR nhanh**: dừng tác vụ (nút tự ẩn sau tối đa 30 giây); tắt hẳn: `AI_ENABLED=false` rồi khởi động lại Tomcat.
- **Gỡ**: `install-ai-service-task.ps1 -Uninstall`, xóa `C:\ERP\ai-service`, `C:\ERP\ai-models`, file env.
- **Sao lưu**: chỉ cần file `ai-service.env` (giữ như secret). Model chép lại được từ bộ cài; AI Service không lưu dữ
  liệu nghiệp vụ. Bảng `ai_ocr_requests` (metadata) nằm trong sao lưu MySQL hiện có.
- **Log**: `ai-service.log` chỉ chứa metadata nhưng không tự xoay vòng `[Chưa kiểm chứng]` tốc độ tăng; xóa / nén định
  kỳ khi dừng tác vụ.
