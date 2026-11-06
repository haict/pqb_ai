-- Tình trạng máy mới / máy cũ (D-70, BR-INV-43). Hàng thuộc nhóm máy (nhóm có mã theo tham số
-- inventory.machine-category-code và các nhóm con) khai báo tình trạng khi nhập kho. Tồn máy mới / cũ tách theo
-- serial hoặc lô (mỗi serial / lô đã là một dòng tồn riêng), không thêm vào khóa tồn.

-- Tình trạng hiện tại của lô: cố định khi tạo lô; nhập thêm vào lô đã có phải cùng tình trạng.
ALTER TABLE inventory_lots
    ADD COLUMN item_condition VARCHAR(10) NOT NULL DEFAULT 'NEW' AFTER expiry_date,
    ADD CONSTRAINT ck_inventory_lots_item_condition CHECK (item_condition IN ('NEW', 'USED'));

-- Tình trạng hiện tại của serial: theo lần nhập kho gần nhất (bản đọc nhanh, lịch sử ở sổ nhật ký kho).
ALTER TABLE inventory_serials
    ADD COLUMN item_condition VARCHAR(10) NOT NULL DEFAULT 'NEW' AFTER lot_id,
    ADD CONSTRAINT ck_inventory_serials_item_condition CHECK (item_condition IN ('NEW', 'USED'));

-- Tình trạng khai báo trên dòng nhập; NULL: dòng xuất, hàng không thuộc nhóm máy, hoặc giữ tình trạng serial đã có.
ALTER TABLE inventory_document_lines
    ADD COLUMN item_condition VARCHAR(10) NULL AFTER expiry_date,
    ADD CONSTRAINT ck_inventory_document_lines_item_condition
        CHECK (item_condition IS NULL OR item_condition IN ('NEW', 'USED'));

-- Tình trạng tại thời điểm ghi sổ của dòng sổ có serial hoặc dòng nhập có khai báo; dòng đảo nhập lại serial lấy
-- tình trạng của dòng gốc để khôi phục tình trạng serial.
ALTER TABLE inventory_transactions
    ADD COLUMN item_condition VARCHAR(10) NULL AFTER serial_id,
    ADD CONSTRAINT ck_inventory_transactions_item_condition
        CHECK (item_condition IS NULL OR item_condition IN ('NEW', 'USED'));

INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('inventory.machine-category-code', 'INVENTORY', 'Mã nhóm hàng máy (theo dõi máy mới / cũ)',
     'D-70, BR-INV-43. Hàng thuộc nhóm có mã này hoặc nhóm con của nó khai báo máy mới / máy cũ khi nhập kho.',
     'STRING', 'MAY', NULL, NULL, NULL, FALSE, 110);
