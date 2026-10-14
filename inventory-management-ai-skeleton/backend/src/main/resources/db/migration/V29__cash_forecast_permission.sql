-- Giai đoạn 10D - dòng tiền dự kiến (BR-RPT-09, BR-RPT-30, BR-CSH-06, ADR-18): quyền xem.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('cash-forecast:view', 'REPORTING', 'Xem dòng tiền dự kiến: số dư quỹ / ngân hàng, dự thu, dự chi theo kỳ', 940);

-- Gán mặc định (docs/07 mục 2.10). [Giả định] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code = 'cash-forecast:view'
WHERE r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR', 'CHIEF_ACCOUNTANT', 'AR_ACCOUNTANT', 'CASHIER')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
