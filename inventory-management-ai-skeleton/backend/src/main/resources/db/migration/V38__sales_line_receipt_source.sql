-- D-63 (2026-10-21): dòng đơn bán chọn chứng từ nhập (F8, xuất đích danh như MISA): lưu dòng sổ nhật ký kho chiều
-- nhập đã chọn và số chứng từ nhập (chụp tại thời điểm chọn) để Giai đoạn 8 tính giá vốn đích danh. Trống: kho chọn
-- lô theo FEFO như trước.
ALTER TABLE sales_order_lines
    ADD COLUMN receipt_transaction_id BIGINT      NULL AFTER lot_override_reason,
    ADD COLUMN receipt_no             VARCHAR(50) NULL AFTER receipt_transaction_id,
    ADD CONSTRAINT fk_sales_order_lines_receipt FOREIGN KEY (receipt_transaction_id)
        REFERENCES inventory_transactions (id);
