-- D-73: chế độ hạch toán theo bản cài đặt, sửa bằng SQL tại khách (docs/08-environment.md mục 4.3).
-- FULL (mặc định): ghi sổ đơn mua / đơn bán = kho + công nợ + bút toán (D-54).
-- INVENTORY_ONLY: chỉ ghi kho; không công nợ, bút toán; đơn giá không bắt buộc (mặc định 0).
INSERT INTO sys_app_settings (setting_key, setting_value, description) VALUES
('posting.mode', 'FULL',
 'Chế độ hạch toán: FULL (kho + công nợ + bút toán) hoặc INVENTORY_ONLY (chỉ kho). Áp dụng cho lần ghi sổ sau khi đổi.');

-- Lần ghi sổ hiện hành của đơn có ghi công nợ + bút toán hay không: bỏ ghi sổ / đảo / trả hàng gỡ đúng phần đã ghi
-- dù chế độ đã đổi. Dữ liệu trước D-73 đều ghi theo FULL.
ALTER TABLE purchase_orders ADD COLUMN financial_posted BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sales_orders ADD COLUMN financial_posted BOOLEAN NOT NULL DEFAULT TRUE;
