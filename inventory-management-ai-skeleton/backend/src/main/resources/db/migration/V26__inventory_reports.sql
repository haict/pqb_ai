-- Giai đoạn 10A - báo cáo kho (BR-RPT-21..24, ADR-18): quyền xem và tham số hàng tồn lâu.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('inventory-report:view', 'REPORTING', 'Xem báo cáo kho: thẻ kho, nhập - xuất - tồn, tồn lâu, ngoại lệ, truy xuất lô / serial', 900);

-- Gán mặc định (docs/07 mục 2.10). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (
    p.code = 'inventory-report:view' AND r.code IN ('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'DIRECTOR',
        'BRANCH_DIRECTOR', 'CHIEF_ACCOUNTANT', 'QA'))
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);

-- BR-RPT-23: hàng còn tồn không xuất trong số ngày này là tồn lâu. Mặc định 180 ngày là [Giả định]; doanh nghiệp
-- chỉnh ở màn hình tham số.
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('inventory.slow-moving-days', 'INVENTORY', 'Số ngày không xuất để coi là tồn lâu',
     'BR-RPT-23. Hàng còn tồn tại kho mà không có phát sinh xuất trong số ngày này được đưa vào báo cáo tồn lâu.',
     'INTEGER', '180', 1, 3650, NULL, FALSE, 110);
