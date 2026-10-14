-- Sổ chi tiết bán hàng (mẫu S16-DNN, BR-RPT-26, H-04): quyền xem riêng với báo cáo doanh số.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-ledger:view', 'SALES', 'Xem sổ chi tiết bán hàng theo hàng hóa (S16-DNN)', 462);

-- Gán mặc định cho các vai trò đang xem báo cáo doanh số (docs/07 mục 2.9). [Suy luận] Doanh nghiệp điều chỉnh ở
-- màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (
    p.code = 'sales-ledger:view' AND r.code IN ('SALES_MANAGER', 'DIRECTOR', 'BRANCH_DIRECTOR', 'CHIEF_ACCOUNTANT',
        'AR_ACCOUNTANT', 'TAX_ACCOUNTANT'))
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
