# Quy tắc toàn vẹn nghiệp vụ

Áp dụng cho mọi module có chứng từ. Nguồn: `docs/01-business-requirements.md` mục 5.

## 1. Trạng thái chứng từ
- Chuyển trạng thái chỉ qua phương thức domain (ví dụ `document.approve(actor, clock)`), kiểm tra trạng thái nguồn hợp lệ; không gán `status` trực tiếp từ request.
- Nhóm ghi sổ: `DRAFT → SUBMITTED → APPROVED → POSTED`; `SUBMITTED → REJECTED → DRAFT`; `CANCELLED` khi chưa ghi sổ; `REVERSED` qua chứng từ đảo.
- Nhóm điều phối: thêm `IN_PROGRESS`, `COMPLETED`, `CLOSED`.
- Chỉ sửa được ở `DRAFT`, `REJECTED`.
- Mỗi lần chuyển trạng thái lưu: người, thời điểm, lý do (bắt buộc với từ chối, hủy, đảo, đóng).

## 2. Ghi sổ
- Chỉ chứng từ `POSTED` ảnh hưởng tồn thực tế, công nợ, quỹ, doanh thu, giá vốn, bút toán.
- Ghi sổ trong **một transaction**: chứng từ + sổ phụ + bút toán + số dư + chỉ tiêu nguồn + audit.
- Sổ phụ (kho, công nợ, quỹ, bút toán) chỉ INSERT; không UPDATE/DELETE.
- Mỗi dòng sổ phụ tham chiếu chứng từ và dòng chứng từ nguồn.
- Kiểm tra kỳ: ngày hạch toán không thuộc kỳ đã khóa.

## 3. Đảo và hủy
- Không xóa cứng chứng từ đã gửi duyệt hoặc đã ghi sổ.
- Đảo = tạo chứng từ đảo tham chiếu gốc, sinh dòng sổ phụ ngược đúng từng dòng (cùng lô, serial, giá trị); chứng từ gốc chuyển `REVERSED`.
- Chặn đảo khi có chứng từ phụ thuộc chưa đảo; trả danh sách chứng từ phụ thuộc.
- Đảo cập nhật lại chỉ tiêu chứng từ nguồn (đã nhận, đã giao, đã thu, còn lại).
- Bỏ ghi sổ (D-62, chỉ đơn mua / đơn bán): đảo sổ phụ của lần ghi sổ đó bằng dòng ngược theo ngày hạch toán gốc (kỳ gốc phải mở), chứng từ về `DRAFT` giữ số; điều kiện chặn như đảo. Không áp dụng cho chứng từ khác.

## 4. Tồn kho
- Bốn chỉ tiêu: **tồn thực tế, tồn giữ chỗ, tồn khả dụng, hàng đang về**; định nghĩa ở BR mục 5.4.
- Tồn thực tế không bao giờ âm ở bất kỳ cấp nào.
- Xuất vượt tồn khả dụng chỉ khi có chính sách được duyệt; ghi audit.
- Kiểm tra tồn khả dụng **tại thời điểm ghi sổ**, sau khi đã khóa dòng số dư.
- FEFO cho hàng có hạn dùng; FIFO khi cấu hình; chọn lô khác phải có lý do.
- Lô hết hạn, không đạt, cách ly, thu hồi không được xuất bán.
- Không controller hay module nào cập nhật `inventory_balances` trực tiếp; chỉ `InventoryPostingService`.

## 5. Khóa dữ liệu và đồng thời
- Khi ghi sổ: khóa bi quan (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`) các dòng số dư và dòng chỉ tiêu bị trừ (tồn, số còn được giao của hợp đồng, số còn phải thu của hóa đơn, số dư quỹ).
- Khóa theo **thứ tự xác định** (ví dụ sắp xếp theo khóa số dư) để tránh deadlock.
- Chứng từ, danh mục: optimistic locking `@Version`; xung đột trả `409`.
- Đánh số chứng từ an toàn đồng thời (bảng bộ đếm có khóa dòng), không dùng `MAX()+1`.

## 6. Chống xử lý trùng
- Lệnh tạo và hành động dùng `Idempotency-Key` (xem `.claude/rules/api.md`).
- Tác vụ nền (tính giá vốn, cảnh báo, gửi email) idempotent: chạy lại không tạo kết quả trùng.

## 7. Liên kết chứng từ
- Mỗi chứng từ lưu chứng từ nguồn (loại, ID, dòng) và kế thừa chiều phân tích (công ty, chi nhánh, hợp đồng, gói thầu, nhân viên kinh doanh).
- Chỉ tiêu theo dòng chứng từ nguồn được cập nhật trong cùng transaction với chứng từ đích (ví dụ dòng đơn mua: đã nhận, đã nhập, đã nhận hóa đơn; dòng hợp đồng: đã đặt, đã giao, đã nghiệm thu, đã xuất hóa đơn, đã thu).
- Không cho chứng từ đích vượt số còn lại của nguồn trừ khi có dung sai/chính sách được duyệt.
- Cung cấp truy vết hai chiều nguồn ↔ đích.

## 8. Tách sự kiện nghiệp vụ
- Mua (D-54): đơn mua là chứng từ ghi sổ (mang hóa đơn NCC); phiếu nhập kho sinh khi ghi sổ đơn và liên kết; chứng từ thanh toán riêng.
- Bán (D-54): đơn bán là chứng từ ghi sổ (xuất kho, doanh thu, công nợ, hóa đơn cùng lúc); phiếu xuất kho sinh khi ghi sổ đơn và liên kết; giao hàng là trạng thái theo dõi trên đơn; phiếu thu riêng.
- Chứng từ sinh tự động (phiếu kho) vẫn riêng biệt, liên kết hai chiều với chứng từ nguồn, đảo theo chứng từ nguồn.

## 9. Kỳ kế toán, kỳ kho, giá vốn
- Không ghi sổ, đảo, sửa chứng từ thuộc kỳ đã khóa.
- Không thay đổi giá vốn kỳ đã khóa; chênh lệch phát sinh sau khóa ghi vào kỳ hiện hành.
- Khóa kỳ kho sau khi tính giá vốn; khóa kỳ kế toán sau khóa kỳ kho.
- Mở lại kỳ phải được duyệt và ghi audit.

## 10. Kế toán và thuế
- Quy tắc hạch toán, thuế suất, cách ghi nhận doanh thu/giá vốn/công nợ triển khai theo quyết định đã chốt (D-02, D-26, D-34, D-35) dưới dạng **cấu hình** (`posting_rules`, tham số), không hard-code; kế toán trưởng rà soát và ký xác nhận trước UAT.
- Không ghi trong mã, tài liệu hay giao diện rằng hệ thống "tuân thủ đầy đủ quy định" hoặc "thay thế phần mềm kế toán được chứng nhận".
- Bút toán phải cân Nợ = Có; bút toán tự sinh không sửa tay.

## 11. Truy ngược số liệu
Mọi số tổng hợp (báo cáo, dashboard) phải tính từ sổ phụ/chứng từ và có API chi tiết trả về danh sách chứng từ nguồn cấu thành số đó.
