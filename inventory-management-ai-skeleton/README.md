# ERP Thiết bị - Vật tư y tế

Hệ thống ERP cho doanh nghiệp thương mại máy móc, thiết bị, vật tư y tế, hóa chất - thuốc thử. Phạm vi: mua hàng, kho (lô, hạn dùng, serial), bán hàng, đấu thầu và hợp đồng, công nợ, thu chi, kế toán và giá vốn, thiết bị và bảo hành, báo cáo.

> **Trạng thái:** đang làm Giai đoạn 0 (nền tảng). Chưa có chức năng nghiệp vụ. Chi tiết: [docs/12-feature-status.md](docs/12-feature-status.md).

## Công nghệ
| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Data JPA, Spring Security, Flyway, Maven; đóng gói WAR cho Tomcat 10.1 |
| Frontend | React 18, TypeScript, Vite, Ant Design 5, TanStack Query |
| Database | MySQL 8.x |
| Kiểm thử | JUnit 5, Mockito, Spring Boot Test (MySQL thật), ArchUnit, Vitest, Testing Library |
| Môi trường phát triển | MySQL local, Eclipse + Tomcat 10.1, Vite dev server (không dùng Docker) |

## Cấu trúc thư mục
```
backend/     Spring Boot (WAR), gói gốc com.company.erp; config/ chứa mẫu cấu hình máy local
frontend/    React + TypeScript
docs/        tài liệu
.github/     CI
CLAUDE.md    hướng dẫn cho Claude Code
.claude/     quy tắc lập trình
```

## Chạy nhanh
Yêu cầu: JDK 17, Maven 3.9+, Node.js 20, MySQL 8, Eclipse + Tomcat 10.1. Chi tiết: [docs/08-environment.md](docs/08-environment.md).

1. Tạo schema `inventory_db`, `inventory_db_test` và tài khoản MySQL (docs/08 mục 5.1).
2. Sao chép `backend/config/application-local.example.yml` thành `~/.erp/application-local.yml`, điền tài khoản DB.
3. Backend: import `backend/` vào Eclipse (Maven), chạy trên Tomcat 10.1 → `http://localhost:8080/erp/actuator/health`.
4. Frontend:
   ```bash
   cd frontend
   npm ci
   npm run dev          # http://localhost:5173
   ```

## Kiểm thử
```bash
cd backend  && mvn clean verify
cd frontend && npm run lint && npm test && npm run build
```
Test tích hợp backend chạy trên MySQL 8 local, schema `inventory_db_test` (cấu hình trong `~/.erp/application-local.yml`; CI dùng `ERP_TEST_DB_URL`). Thiếu cấu hình thì test tích hợp bị bỏ qua và **chưa được tính là đạt**.

## Tài liệu
| File | Nội dung |
|---|---|
| [docs/01-business-requirements.md](docs/01-business-requirements.md) | Yêu cầu nghiệp vụ, quyết định, kịch bản nghiệm thu |
| [docs/02-system-architecture.md](docs/02-system-architecture.md) | Kiến trúc tổng thể |
| [docs/03-database-design.md](docs/03-database-design.md) | ERD, data dictionary |
| [docs/04-api-specification.md](docs/04-api-specification.md) | Đặc tả API |
| [docs/05-workflow.md](docs/05-workflow.md) | Luồng nghiệp vụ |
| [docs/06-development-plan.md](docs/06-development-plan.md) | Kế hoạch phát triển |
| [docs/08-environment.md](docs/08-environment.md) | Cấu hình môi trường |
| [docs/12-feature-status.md](docs/12-feature-status.md) | Trạng thái tính năng |
| [CLAUDE.md](CLAUDE.md), [.claude/rules/](.claude/rules/) | Quy tắc kiến trúc, lập trình, DB, API, bảo mật, kiểm thử, frontend, triển khai, tài liệu |

Tài liệu sẽ lập theo giai đoạn: 07 phân quyền (GĐ 1), 09 backup/restore, 10 triển khai, 11 kiểm thử và nghiệm thu (GĐ 11).

## Làm việc với Claude Code
1. Mở Claude Code tại thư mục gốc; yêu cầu đọc `CLAUDE.md`, `.claude/rules/*`, `docs/*`.
2. Làm theo từng giai đoạn / module trong `docs/06-development-plan.md`.
3. Không yêu cầu sinh toàn bộ hệ thống trong một lần.
4. Mọi quyết định ở `docs/01-business-requirements.md` mục 20 đã chốt; quy tắc kế toán/pháp lý làm dạng cấu hình, kế toán trưởng / pháp chế rà soát trước UAT.
