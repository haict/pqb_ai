-- Giai đoạn 10C - dashboard điều hành trên trang chủ (BR-RPT-01, 02, 04..06, 10..13, ADR-18): quyền xem.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('dashboard:view', 'REPORTING', 'Xem dashboard điều hành: doanh thu, công nợ, hàng sắp hết hạn, đơn chưa giao đủ, top hàng / khách / NCC', 930);

-- Gán mặc định (docs/07 mục 2.10, chủ dự án duyệt 2026-10-14). Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code = 'dashboard:view'
WHERE r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR', 'CHIEF_ACCOUNTANT', 'SALES_MANAGER')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
