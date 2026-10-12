-- Giai đoạn 6F: quyền bán nhanh (BR-SAL-42) và báo cáo doanh số (BR-SAL-51).
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-quick-sale:execute', 'SALES', 'Bán nhanh: đơn bán, giao hàng, hóa đơn một thao tác (cần cả quyền từng bước)', 460),
    ('sales-report:view',        'SALES', 'Xem báo cáo doanh số, công nợ theo nhân viên / khách / hàng / chi nhánh',        461);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (
    (p.code = 'sales-quick-sale:execute' AND r.code IN ('SALES_MANAGER', 'WAREHOUSE_MANAGER', 'AR_ACCOUNTANT',
        'CHIEF_ACCOUNTANT')) OR
    (p.code = 'sales-report:view' AND r.code IN ('SALES_MANAGER', 'DIRECTOR', 'BRANCH_DIRECTOR', 'CHIEF_ACCOUNTANT',
        'AR_ACCOUNTANT')));
