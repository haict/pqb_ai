-- Sổ chi tiết mua hàng (BR-RPT, H-06, docs/HienTrang 14-15): quyền xem riêng.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-ledger:view', 'PURCHASING', 'Xem sổ chi tiết mua hàng theo hàng hóa, nhà cung cấp', 350);

-- Gán mặc định cho vai trò mua hàng, lãnh đạo, kế toán (docs/07 mục 2.8). [Suy luận] Doanh nghiệp điều chỉnh ở màn
-- hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (
    p.code = 'purchase-ledger:view' AND r.code IN ('PURCHASING_STAFF', 'DIRECTOR', 'BRANCH_DIRECTOR',
        'CHIEF_ACCOUNTANT', 'TAX_ACCOUNTANT'))
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
