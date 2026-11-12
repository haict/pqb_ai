-- D-76: kho xuất theo từng dòng đơn bán (cùng một đơn có thể xuất từ nhiều kho); bỏ kho ở đầu đơn bán và đầu phiếu
-- trả hàng / giảm giá bán (dòng trả nhập lại kho của dòng đơn bán gốc). Dữ liệu cũ: dòng hàng hóa lấy kho ở đầu đơn.
ALTER TABLE sales_order_lines ADD COLUMN warehouse_id BIGINT NULL AFTER service_line;
UPDATE sales_order_lines l JOIN sales_orders h ON h.id = l.order_id
SET l.warehouse_id = h.warehouse_id
WHERE l.service_line = FALSE;
ALTER TABLE sales_order_lines
    ADD CONSTRAINT fk_sales_order_lines_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    ADD CONSTRAINT ck_sales_order_lines_warehouse CHECK (service_line OR warehouse_id IS NOT NULL),
    ADD INDEX idx_sales_order_lines_warehouse (warehouse_id, product_id);

ALTER TABLE sales_orders DROP FOREIGN KEY fk_sales_orders_warehouses;
ALTER TABLE sales_orders DROP COLUMN warehouse_id;

ALTER TABLE sales_returns DROP CHECK ck_sales_returns_kind;
ALTER TABLE sales_returns DROP FOREIGN KEY fk_sales_returns_warehouses;
ALTER TABLE sales_returns DROP COLUMN warehouse_id;
