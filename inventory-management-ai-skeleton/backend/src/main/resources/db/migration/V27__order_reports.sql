-- Giai đoạn 10B - báo cáo mua / bán (BR-RPT-25, 26, ADR-18): quyền xem.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-report:view',    'REPORTING', 'Xem báo cáo mua hàng: tình hình thực hiện đơn mua, lịch sử giá mua, hàng lỗi theo NCC', 910),
    ('sales-order-report:view', 'REPORTING', 'Xem báo cáo bán hàng: tình hình thực hiện đơn bán, sổ nhật ký bán hàng, tồn so với đặt hàng chưa giao', 920);

-- Gán mặc định (docs/07 mục 2.10). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (
    (p.code = 'purchase-report:view' AND r.code IN ('PURCHASING_STAFF', 'DIRECTOR', 'BRANCH_DIRECTOR',
        'CHIEF_ACCOUNTANT', 'TAX_ACCOUNTANT', 'QA')) OR
    (p.code = 'sales-order-report:view' AND r.code IN ('SALES_MANAGER', 'SALES_STAFF', 'WAREHOUSE_MANAGER',
        'DIRECTOR', 'BRANCH_DIRECTOR', 'CHIEF_ACCOUNTANT', 'AR_ACCOUNTANT')))
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
