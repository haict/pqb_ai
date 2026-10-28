# ERP Thiết bị - Vật tư y tế - Hướng dẫn cho Claude Code

## 1. Dự án
ERP cho doanh nghiệp thương mại máy móc, thiết bị, vật tư y tế, hóa chất - thuốc thử; có kinh doanh qua đấu thầu.

Ba chuỗi nghiệp vụ chính (chi tiết: `docs/01-business-requirements.md`):
- **Mua hàng:** Đơn mua (kèm hóa đơn NCC; ghi sổ = nhập kho + công nợ NCC) → Thanh toán (D-54).
- **Bán hàng:** Khách hàng → Đơn bán (kiểm tra tồn; ghi sổ = xuất kho + doanh thu + công nợ khách; số hóa đơn điện tử) → Giao hàng (theo dõi) → Thu tiền (D-54).
- **Đấu thầu:** Gói thầu → Hồ sơ dự thầu → Kết quả trúng thầu → Hợp đồng → Phân bổ hàng → Giao theo tiến độ → Nghiệm thu → Đối soát và thu tiền.

Nguyên tắc cốt lõi:

    Chứng từ nghiệp vụ -> Bút toán sổ phụ (kho / công nợ / quỹ / kế toán) -> Số dư -> Báo cáo

Không số dư nào (tồn kho, công nợ, quỹ, giá vốn) được thay đổi mà không có chứng từ nghiệp vụ tương ứng. Một giao dịch ghi sổ phải cập nhật đúng mọi dữ liệu liên quan theo ma trận ảnh hưởng chứng từ (BR mục 5.3).

## 2. Công nghệ bắt buộc
| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17, Spring Boot 3.x (Spring MVC, Spring Data JPA/Hibernate, Spring Security, Bean Validation) |
| Frontend | ReactJS + TypeScript, Ant Design, responsive |
| Database | MySQL 8.x, Flyway |
| API | RESTful JSON, OpenAPI/Swagger (springdoc-openapi) |
| Bảo mật | Spring Security, JWT access token + refresh token xoay vòng (D-38) |
| Build | Maven 3.9+ (backend); npm/pnpm (frontend) |
| Kiểm thử | JUnit 5, Mockito, Spring Boot Test, MySQL 8 thật (schema `*_test` trên máy local), ArchUnit (ranh giới module) |
| Frontend data | TanStack Query (cache dữ liệu server) |
| Logging | SLF4J + Logback |
| Quản lý mã nguồn | Git |
| Tiện ích | Lombok (dùng có kiểm soát) |
| AI Service (D-64) | Python 3, FastAPI, PaddleOCR (OCR), Ollama (LLM chạy nội bộ); chỉ backend gọi, nghe `127.0.0.1`. Thiết kế: `docs/14-ai-assistant-ocr.md` |

- **Không** tự nâng lên Java 21 hoặc Spring Boot 4. Mọi dependency phải tương thích Java 17 và Spring Boot 3.x; ưu tiên phiên bản do Spring Boot BOM quản lý.
- Không thêm dependency mới khi chưa giải thích lý do, phạm vi dùng và lựa chọn thay thế.

## 3. Đóng gói và triển khai
- Backend đóng gói **WAR**, triển khai được trên **Tomcat 10.1** ngoài (Jakarta EE 10, tương thích Spring Boot 3):
  - Lớp khởi động kế thừa `SpringBootServletInitializer`.
  - `spring-boot-starter-tomcat` khai báo `scope=provided`.
  - WAR vẫn chạy được bằng `java -jar` khi phát triển.
- Lý do chọn WAR (đã chốt, D-39): yêu cầu triển khai trên Tomcat 10.1 của doanh nghiệp. Nếu đổi sang executable JAR phải ghi lý do vào `docs/10-deployment.md` và được duyệt.
- Cấu hình theo môi trường qua biến môi trường hoặc file ngoài (`spring.config.import`), profile `dev`, `test`, `uat`, `prod`.
- Không dùng Docker (D-44). Môi trường phát triển: MySQL 8 cài trên máy (local), backend chạy bằng Eclipse + Tomcat 10.1 (context `/erp`), frontend bằng `npm run dev`.
- Cấu hình riêng từng máy (tài khoản DB, profile) đặt ở `~/.erp/application-local.yml`, ngoài repo; mẫu `backend/config/application-local.example.yml`.
- Hướng dẫn đầy đủ: `docs/10-deployment.md`, `docs/08-environment.md`. Quy tắc: `.claude/rules/deployment.md`.

## 4. Cấu trúc repository (đã chốt, D-33)
```
/
├── backend/            Maven, Spring Boot WAR
├── frontend/           React + TypeScript + Ant Design
├── ai-service/         Python FastAPI: OCR, trợ lý AI (D-64)
├── docs/               tài liệu nghiệp vụ, kiến trúc, ERD, API, vận hành
├── CLAUDE.md
└── .claude/rules/
```

## 5. Kiến trúc: Modular Monolith
Một ứng dụng triển khai, chia **module theo nghiệp vụ**. Không tách microservices khi chưa có quyết định được duyệt.

Gói gốc: `com.company.erp` (D-43; đặt lại khi có tên chính thức).

| Gói | Phân hệ |
|---|---|
| `common` | Hạ tầng dùng chung: lỗi, phản hồi API, phân trang, audit base, idempotency, đánh số chứng từ |
| `system` | M01 Quản trị hệ thống: tổ chức, người dùng, phân quyền, phê duyệt, kỳ, nhật ký |
| `masterdata` | M02 Danh mục dùng chung |
| `purchasing` | M03 Mua hàng và nhà cung cấp |
| `inventory` | M04 Quản lý kho |
| `sales` | M05 Bán hàng và phân phối |
| `tender` | M06 Đấu thầu và hợp đồng |
| `finance` | M07 Công nợ, thu chi, ngân hàng |
| `accounting` | M08 Kế toán và giá vốn |
| `equipment` | M09 Thiết bị, bảo hành, hồ sơ sản phẩm |
| `reporting` | M10 Báo cáo và dashboard |
| `ai` | M11 Trợ lý AI và OCR (D-64): chỉ đọc / gợi ý, không ghi chứng từ; không module nào phụ thuộc `ai` |

Cấu trúc trong mỗi module (chỉ tạo tầng thực sự cần):
```
<module>/
├── api/             Controller (nhận HTTP, phân quyền endpoint)
├── application/     Service / use case, transaction boundary, điều phối; dto/ request/response
├── domain/          Entity, enum trạng thái, quy tắc nghiệp vụ, domain service, exception nghiệp vụ
└── infrastructure/  Repository, truy vấn, client bên ngoài
```

Quy tắc ranh giới (chi tiết: `.claude/rules/architecture.md`):
- Module khác chỉ gọi **API công khai của tầng `application`** (ví dụ `InventoryPostingService`), không gọi repository hay entity của module khác.
- Mọi biến động tồn kho đi qua module `inventory`; mọi biến động công nợ/quỹ qua `finance`; mọi bút toán qua `accounting`.
- Không có Service khổng lồ: tách theo use case (ví dụ `GoodsReceiptPostingService`, `SalesOrderApprovalService`).
- Controller không chứa nghiệp vụ.

## 6. Quy tắc lập trình bắt buộc
- Java 17, gói `jakarta.*`.
- **Constructor injection**; không dùng field injection (`@Autowired` trên field).
- **Không dùng wildcard import**.
- **Không hard-code** thông tin nhạy cảm, cấu hình môi trường, quy tắc nghiệp vụ (ngưỡng, thuế suất, số ngày...) → đưa vào cấu hình hoặc bảng tham số.
- DTO request/response riêng; **không trả JPA entity ra API**.
- Validation hai tầng: Bean Validation ở API, quy tắc nghiệp vụ ở application/domain.
- Transaction rõ ràng bằng `@Transactional` ở tầng application.
- Xử lý exception tập trung (`@RestControllerAdvice`), lỗi trả về nhất quán.
- Phân trang, sắp xếp, lọc ở server.
- Không dùng `@Data` trên entity; tránh quan hệ hai chiều và `EAGER` không cần thiết; chống N+1.
- Enum cho trạng thái và loại.
- Log bằng SLF4J; không log mật khẩu, token, secret, dữ liệu nhạy cảm.
- Tên có nghĩa, phương thức ngắn và tập trung. Không để code chết, code bị comment, TODO không lý do.

Chi tiết: `.claude/rules/coding-standard.md`.

## 7. Quy tắc API
- Tiền tố `/api/v1`; REST nhất quán; OpenAPI tự sinh và cập nhật.
- Lệnh thay đổi trạng thái là endpoint hành động: `POST /{resource}/{id}/submit|approve|reject|post|reverse|cancel`.
- Lệnh tạo và lệnh hành động **chống xử lý trùng** bằng header `Idempotency-Key`.
- Danh sách trả kết quả phân trang (`page`, `size`, `sort`, bộ lọc).
- Lỗi theo định dạng thống nhất, có mã lỗi; không lộ stack trace, SQL, thông tin nội bộ.
- Thay đổi API phải xem xét tương thích ngược.

Chi tiết: `.claude/rules/api.md`.

## 8. Quy tắc nghiệp vụ trọng yếu
- **Trạng thái chứng từ**:
  - Nhóm ghi sổ: `DRAFT → SUBMITTED → APPROVED → POSTED`, `SUBMITTED → REJECTED → DRAFT`, `CANCELLED` khi chưa ghi sổ, `REVERSED` qua chứng từ đảo.
  - Nhóm điều phối (đơn mua, đơn bán, hợp đồng...): thêm `IN_PROGRESS`, `COMPLETED`, `CLOSED`.
  - Chi tiết: BR mục 5.1.
- Chỉ chứng từ **POSTED** ảnh hưởng tồn thực tế và số liệu tài chính.
- **Không xóa cứng** chứng từ đã phát sinh giao dịch; hủy bằng chứng từ đảo, không sửa bút toán lịch sử. Riêng đơn mua / đơn bán được **bỏ ghi sổ** về nháp giữ số để sửa (D-62): sinh dòng ngược theo ngày hạch toán gốc, không sửa / xóa dòng sổ phụ cũ.
- Phân biệt **tồn thực tế, tồn giữ chỗ, tồn khả dụng, hàng đang về**. Tồn thực tế không bao giờ âm; xuất vượt tồn khả dụng chỉ khi có chính sách được duyệt.
- Theo D-54: ghi sổ đơn mua = nhập kho + công nợ phải trả + bút toán; ghi sổ đơn bán = xuất kho + doanh thu + công nợ phải thu + bút toán. Phiếu kho vẫn là chứng từ riêng sinh tự động, liên kết hai chiều với đơn; thanh toán / thu tiền là chứng từ riêng. Không gộp thêm bước nào khác ngoài D-54.
- Giao hàng theo hợp đồng thầu không vượt số còn lại.
- Không ghi sổ, đảo, sửa chứng từ thuộc **kỳ đã khóa**; không đổi giá vốn kỳ đã khóa.
- Duyệt, ghi sổ, hủy, đảo, điều chỉnh: kiểm tra quyền server-side và ghi audit (người, thời điểm, lý do, trước/sau).
- Truy vết hai chiều chứng từ nguồn ↔ chứng từ đích.
- Quy tắc kế toán/thuế/pháp lý (D-02, D-14, D-26, D-28, D-34, D-35) đã chốt nhưng phải triển khai dưới dạng **quy tắc hạch toán / tham số cấu hình**, không hard-code; kế toán trưởng / pháp chế rà soát trước UAT. Không tự khẳng định hệ thống tuân thủ pháp luật.
- Mọi quyết định ở BR mục 20 đã chốt. Yêu cầu mới không có trong BR hoặc mâu thuẫn với quyết định đã chốt: dừng và hỏi, không tự đoán.

Chi tiết: `.claude/rules/business-integrity.md`.

## 9. Transaction và đồng thời
Use case ghi sổ (nhập, xuất, chuyển, điều chỉnh, hóa đơn, thu, chi) theo thứ tự:
1. Validate yêu cầu và kiểm tra idempotency.
2. Mở transaction.
3. Khóa chứng từ và các dòng số dư liên quan theo thứ tự xác định (tránh deadlock).
4. Kiểm tra trạng thái chứng từ, kỳ, quyền, tồn khả dụng / số còn lại.
5. Ghi sổ phụ (kho, công nợ, quỹ) và bút toán kế toán.
6. Cập nhật số dư và chỉ tiêu chứng từ nguồn.
7. Ghi audit.
8. Commit. Lỗi ở bất kỳ bước nào thì rollback toàn bộ.

Chiến lược khóa: khóa bi quan (`SELECT ... FOR UPDATE`) trên dòng số dư khi ghi sổ, kết hợp optimistic locking (`@Version`) cho chứng từ và danh mục. Thay đổi chiến lược phải ghi vào `docs/02-system-architecture.md` trước khi làm.

## 10. Database
- MySQL 8.x, Flyway là nguồn duy nhất của schema. **Không sửa migration đã chạy**; thay đổi bằng migration mới.
- Thiết kế ERD trước khi tạo bảng; tên bảng snake_case số nhiều.
- PK/FK/unique/index rõ ràng theo truy vấn thực tế.
- DECIMAL cho tiền và số lượng; không FLOAT/DOUBLE.
- Cột audit `created_by, created_at, updated_by, updated_at`; `version` cho optimistic locking khi cần.
- Lưu thời gian UTC.
- Dữ liệu mẫu tách riêng, chỉ chạy ở `dev`/`test`; **không bao giờ chạy ở production**.

Chi tiết: `.claude/rules/database.md`.

## 11. Bảo mật
Spring Security; RBAC + phạm vi dữ liệu theo công ty/chi nhánh/kho; kiểm tra quyền cho **từng API**; không tin frontend; mật khẩu băm; không log thông tin nhạy cảm; secret theo môi trường; giới hạn file tải lên; audit log.

Chi tiết: `.claude/rules/security.md`.

## 12. Frontend
React + TypeScript (strict) + Ant Design; giao diện tiếng Việt; nút thao tác theo trạng thái và quyền; **không có nút/màn hình giả** — chức năng đã công bố phải gọi API và database thật.

Chi tiết: `.claude/rules/frontend.md`.

## 13. Kiểm thử
- Unit test và integration test cho mọi nghiệp vụ trọng yếu.
- Bắt buộc phủ các kịch bản nghiệm thu AT-01..AT-13 (BR mục 18), gồm: đồng thời, rollback, đảo chứng từ, khóa kỳ, truy cập trái quyền, chống xử lý trùng.
- Trước khi báo hoàn thành: `mvn clean verify` (backend) và `npm run lint && npm test && npm run build` (frontend); với thay đổi DB, chạy Flyway trên database trống.

Chi tiết: `.claude/rules/testing.md`.

## 14. Tài liệu bắt buộc
README, kiến trúc, sơ đồ module và luồng, ERD và data dictionary, API, phân quyền, cấu hình môi trường, backup/restore, triển khai, kế hoạch kiểm thử và báo cáo nghiệm thu, danh sách tính năng hoàn thành/chưa hoàn thành/giả định.

Danh sách và vị trí: BR mục 19. Quy tắc: `.claude/rules/documentation.md`.

## 15. Quy trình làm việc của Claude Code
Với task không đơn giản:
1. Đọc `CLAUDE.md`, các file liên quan trong `.claude/rules/`, `docs/`.
2. Kiểm tra mã hiện có.
3. Trình bày kế hoạch: phạm vi, file thay đổi, mã BR liên quan, rủi ro.
4. **Chờ duyệt** khi thay đổi kiến trúc, schema DB, hợp đồng API, nhiều module, hoặc quy tắc nghiệp vụ.
5. Làm thay đổi nhỏ nhất nhưng trọn vẹn (một module / một use case mỗi lần).
6. Chạy build và test.
7. Rà soát diff.
8. Báo cáo theo mẫu ở mục 17.

Không sinh toàn bộ ứng dụng trong một bước.

## 16. Giới hạn thay đổi
- Không sửa module không liên quan; không viết lại code đang chạy chỉ vì phong cách.
- Không đổi hợp đồng API hoặc DB khi chưa nêu tác động.
- Không âm thầm đổi quy tắc nghiệp vụ.
- Yêu cầu mâu thuẫn hoặc thiếu: **DỪNG và hỏi**, không tự đoán. Nội dung suy luận phải gắn nhãn `[Suy luận]`.

## 17. Định nghĩa hoàn thành (Definition of Done)
Một task chỉ hoàn thành khi:
- Mã biên dịch được; lint không lỗi.
- Test liên quan chạy và đạt; kịch bản nghiệm thu liên quan được phủ.
- Không vi phạm kiến trúc/ranh giới module.
- Có Flyway migration nếu đổi schema; đã chạy trên DB trống.
- OpenAPI và tài liệu liên quan được cập nhật.
- Chức năng giao diện đã công bố gọi API thật.

**Không tuyên bố hoàn thành khi chỉ biên dịch thành công.** Báo cáo cuối mỗi task gồm:
- File thay đổi.
- Mã BR / AT được đáp ứng.
- Số lượng test, số đạt / lỗi, lệnh đã chạy.
- Lỗi còn tồn tại, chức năng chưa triển khai, giả định đã dùng.
