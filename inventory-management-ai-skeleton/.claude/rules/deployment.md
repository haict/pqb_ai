# Quy tắc build, môi trường và triển khai

## 1. Phiên bản
- Java 17, Spring Boot 3.x, Tomcat 10.1, MySQL 8.x, Maven 3.9+.
- Không tự nâng Java 21 hoặc Spring Boot 4. Nâng phiên bản minor/patch phải nêu lý do và chạy lại toàn bộ test.

## 2. Đóng gói backend
- `packaging=war`; lớp khởi động kế thừa `SpringBootServletInitializer`.
- `spring-boot-starter-tomcat` scope `provided`.
- Tên artifact cố định (ví dụ `erp.war`) để triển khai lên Tomcat ngoài.
- WAR chạy được bằng `java -jar` cho phát triển.
- Đổi sang executable JAR phải ghi lý do trong `docs/10-deployment.md` và được duyệt (D-39).

## 3. Cấu hình theo môi trường
- Profile: `dev`, `test`, `uat`, `prod`.
- Giá trị theo môi trường (URL DB, tài khoản, secret JWT, đường dẫn lưu file, CORS) qua biến môi trường hoặc file cấu hình ngoài; không commit giá trị thật.
- Có `.env.example` và bảng biến môi trường trong `docs/08-environment.md`.
- Ở `prod`: tắt dữ liệu mẫu (`db/dev-data`), tắt hoặc bảo vệ Swagger UI, `ddl-auto=validate`, log mức `INFO`.

## 4. Môi trường phát triển (không dùng Docker, D-44)
- MySQL 8 cài trên máy; schema dev và schema test (hậu tố `_test`) riêng.
- Backend chạy bằng Eclipse + Tomcat 10.1, context path `/erp` (`m2e.wtp.contextRoot` trong `pom.xml`); hoặc `mvn spring-boot:run`.
- Frontend chạy `npm run dev`; Vite chuyển tiếp `/api`, `/actuator` sang `ERP_BACKEND_URL` (mặc định `http://localhost:8080/erp`).
- Cấu hình riêng máy ở `~/.erp/application-local.yml` (ngoài repo, không đóng gói vào WAR).

## 5. Logging
- Logback, cấu hình `logback-spring.xml` theo profile.
- Log file xoay vòng theo kích thước/ngày ở `uat`, `prod`.
- Có `requestId` trong mỗi dòng log.

## 6. Sao lưu và khôi phục
- Sao lưu MySQL hằng ngày, sao lưu thư mục file đính kèm.
- Quy trình khôi phục được kiểm thử định kỳ và ghi ở `docs/09-backup-restore.md`.
- Mục tiêu RPO/RTO theo D-30.

## 7. Tài liệu triển khai
`docs/10-deployment.md` phải có:
- Yêu cầu hệ thống.
- Build WAR và frontend.
- Cấu hình Tomcat 10.1: context path, JVM options, biến môi trường, kết nối DB.
- Triển khai frontend (web server tĩnh hoặc reverse proxy).
- Chạy Flyway.
- Kiểm tra sau triển khai.
- Rollback.
