-- Tham số cảnh báo tồn kho (BR-INV-40): số ngày trước hạn dùng bắt đầu cảnh báo hàng sắp hết hạn.
-- Giá trị mặc định 90 ngày là [Suy luận]; doanh nghiệp chỉnh ở màn hình tham số.
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('inventory.expiry-warning-days', 'INVENTORY', 'Số ngày cảnh báo trước hạn dùng',
     'BR-INV-40. Lô còn tồn có hạn dùng trong số ngày này được đưa vào cảnh báo sắp hết hạn.', 'INTEGER', '90', 1,
     3650, NULL, FALSE, 100);
