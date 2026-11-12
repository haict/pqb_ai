-- D-76: phiếu kho sinh từ đơn mua / đơn bán / trả hàng có kho theo từng dòng (một phiếu cho cả đơn).
-- NULL: dòng dùng kho ở đầu phiếu (phiếu lập tay, chuyển kho, kiểm kê, dữ liệu trước D-76).
ALTER TABLE inventory_document_lines ADD COLUMN warehouse_id BIGINT NULL AFTER direction;
ALTER TABLE inventory_document_lines ADD CONSTRAINT fk_inventory_document_lines_warehouses
    FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
