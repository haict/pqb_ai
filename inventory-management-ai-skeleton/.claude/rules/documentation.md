# Quy tắc tài liệu

## 1. Ngôn ngữ
Tài liệu dự án viết bằng **tiếng Việt**. Thuật ngữ kỹ thuật giữ tiếng Anh khi đó là tên chuẩn (API, DTO, Flyway, JWT...). Mã nguồn, tên lớp, tên bảng dùng tiếng Anh.

## 2. Danh sách tài liệu bắt buộc
| # | Tài liệu | File |
|---|---|---|
| 1 | Hướng dẫn cài đặt và chạy | `README.md` |
| 2 | Yêu cầu nghiệp vụ | `docs/01-business-requirements.md` |
| 3 | Kiến trúc tổng thể | `docs/02-system-architecture.md` |
| 4 | ERD và data dictionary | `docs/03-database-design.md` |
| 5 | Đặc tả API | `docs/04-api-specification.md` |
| 6 | Sơ đồ module và luồng nghiệp vụ | `docs/05-workflow.md` |
| 7 | Kế hoạch phát triển | `docs/06-development-plan.md` |
| 8 | Phân quyền (ma trận vai trò × quyền, phạm vi dữ liệu) | `docs/07-authorization.md` |
| 9 | Cấu hình môi trường | `docs/08-environment.md` |
| 10 | Sao lưu và khôi phục | `docs/09-backup-restore.md` |
| 11 | Triển khai | `docs/10-deployment.md` |
| 12 | Kế hoạch kiểm thử và báo cáo nghiệm thu | `docs/11-test-plan.md` |
| 13 | Tính năng hoàn thành / chưa hoàn thành / giả định | `docs/12-feature-status.md` |

## 3. Khi nào cập nhật
| Thay đổi | Tài liệu phải cập nhật |
|---|---|
| Quy tắc nghiệp vụ | 01 (kèm mã BR) |
| Module, ranh giới, chiến lược khóa | 02, 05 |
| Bảng, cột, ràng buộc | 03 (ERD, data dictionary) cùng migration |
| Endpoint, DTO, mã lỗi | 04 (và OpenAPI) |
| Quyền, vai trò | 07 |
| Biến môi trường, profile | 08 |
| Quy trình triển khai, backup | 09, 10 |
| Hoàn thành / dời chức năng | 06, 12 |
| Kết quả test, nghiệm thu | 11 |

## 4. Nội dung
- Sơ đồ dùng Mermaid hoặc ASCII để xem được trong Git.
- Gắn mã yêu cầu (`BR-…`, `AT-…`, `D-…`) để truy vết.
- Gắn nhãn `[Suy luận]`, `[Chưa kiểm chứng]` cho nội dung chưa được xác nhận.
- Không ghi secret, mật khẩu, dữ liệu khách hàng thật.
- Không khẳng định hệ thống tuân thủ pháp luật kế toán/thuế khi chưa được chuyên gia xác nhận.

## 5. Báo cáo trạng thái tính năng
`docs/12-feature-status.md` liệt kê theo mã BR:
- Trạng thái: Hoàn thành / Đang làm / Chưa làm / Dời giai đoạn.
- Test chứng minh.
- Giả định đã dùng.
- Hạn chế đã biết.
