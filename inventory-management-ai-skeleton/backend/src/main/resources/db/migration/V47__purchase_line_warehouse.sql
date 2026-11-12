-- D-76: kho nhận theo từng dòng đơn mua (cùng một đơn có thể nhận vào nhiều kho); bỏ kho ở đầu đơn mua và đầu phiếu
-- trả hàng / giảm giá mua (dòng trả lấy kho của dòng đơn mua gốc). Dữ liệu cũ: dòng hàng hóa lấy kho ở đầu đơn.
ALTER TABLE purchase_order_lines ADD COLUMN warehouse_id BIGINT NULL AFTER service_line;
UPDATE purchase_order_lines l JOIN purchase_orders h ON h.id = l.order_id
SET l.warehouse_id = h.warehouse_id
WHERE l.service_line = FALSE;
ALTER TABLE purchase_order_lines
    ADD CONSTRAINT fk_purchase_order_lines_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    ADD CONSTRAINT ck_purchase_order_lines_warehouse CHECK (service_line OR warehouse_id IS NOT NULL),
    ADD INDEX idx_purchase_order_lines_warehouse (warehouse_id);

ALTER TABLE purchase_orders DROP FOREIGN KEY fk_purchase_orders_warehouses;
ALTER TABLE purchase_orders DROP COLUMN warehouse_id;

ALTER TABLE purchase_returns DROP CHECK ck_purchase_returns_kind;
ALTER TABLE purchase_returns DROP FOREIGN KEY fk_purchase_returns_warehouses;
ALTER TABLE purchase_returns DROP COLUMN warehouse_id;
