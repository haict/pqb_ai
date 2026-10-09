# Quy tắc frontend

## 1. Công nghệ
- ReactJS + **TypeScript** (`strict: true`), build bằng Vite.
- Ant Design làm thư viện giao diện chính (ngôn ngữ `vi_VN`).
- Gọi API qua một lớp client dùng chung (axios hoặc fetch bọc lại) với kiểu dữ liệu sinh từ OpenAPI hoặc khai báo thủ công khớp DTO.
- Quản lý dữ liệu server bằng TanStack Query (đã chốt D-42).
- ESLint + Prettier; không để cảnh báo lint.
- Không thêm thư viện khi chưa nêu lý do.

## 2. Cấu trúc
```
frontend/src/
├── app/          khởi tạo, router, layout, provider
├── shared/       api client, component dùng chung, hook, tiện ích định dạng
└── modules/      theo phân hệ: system, masterdata, purchasing, inventory, sales,
                  tender, finance, accounting, equipment, reporting
```
Mỗi module: `pages/`, `components/`, `api/`, `types/`.

## 3. Bố cục và giao diện (BR-UI-01..09)
- Giao diện **tiếng Việt**, nhất quán, phong cách ERP doanh nghiệp.
- Sidebar phân hệ; thanh tiêu đề có người dùng, chi nhánh đang làm việc, thông báo; dashboard tổng quan.
- Ưu tiên desktop (từ 1366×768), responsive cho tablet/mobile với màn hình phù hợp.
- Trang danh sách: tìm kiếm, bộ lọc, phân trang **gọi API server-side**; không tải toàn bộ dữ liệu rồi lọc ở client.
- Form: validation rõ tại trường; thông báo lỗi tiếng Việt; hiển thị lỗi từ server theo `errors[].field`.
- Trang chi tiết chứng từ: thông tin chung, danh sách hàng hóa, chứng từ liên quan, lịch sử xử lý.
- Nút thao tác hiển thị theo **trạng thái chứng từ và quyền** người dùng (chỉ hỗ trợ trải nghiệm; server vẫn kiểm tra).
- Hộp thoại xác nhận cho duyệt, ghi sổ, hủy, đảo, xóa, khóa kỳ; hủy/đảo/từ chối bắt buộc nhập lý do.
- Thông báo thành công/thất bại dễ hiểu; không hiển thị stack trace hay lỗi kỹ thuật.
- Định dạng số, tiền, ngày theo Việt Nam; hiển thị giờ UTC+7.

## 4. Chức năng thật
- **Không tạo mockup hoặc nút không có chức năng.** Mọi màn hình, nút đã công bố phải gọi API và database thật.
- Chức năng chưa triển khai thì ẩn khỏi menu, không để nút "đang phát triển".
- Không dùng dữ liệu giả cứng trong mã; dữ liệu mẫu chỉ đến từ backend `dev`.

## 5. Chống thao tác trùng
- Vô hiệu hóa nút khi đang gửi.
- Sinh `Idempotency-Key` (UUID) cho mỗi thao tác tạo/hành động; dùng lại key khi tự thử lại cùng thao tác.
- Gửi `version` khi sửa; xử lý `409` bằng thông báo "dữ liệu đã thay đổi, vui lòng tải lại".

## 6. Bảo mật
- Không tin quyền ở client cho quyết định bảo mật; chỉ dùng để ẩn/hiện.
- Lưu token theo cơ chế đã duyệt (D-38); không ghi token vào log, URL, `console`.
- Không đưa secret vào mã frontend hoặc biến `VITE_*`.
- Không render HTML chưa làm sạch (`dangerouslySetInnerHTML`).

## 7. Tiền và số lượng
Không tính toán tiền bằng `number` để lấy kết quả cuối cùng; tổng tiền chính thức do server tính. Client chỉ hiển thị ước tính khi cần và dùng thư viện số thập phân nếu tính.

## 8. Kiểm thử và build
`npm run lint && npm test && npm run build` phải đạt trước khi báo hoàn thành.
