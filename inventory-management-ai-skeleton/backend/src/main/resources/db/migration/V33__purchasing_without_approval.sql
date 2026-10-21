-- D-61 (2026-10-21), đợt 2 - mua hàng: đơn mua, trả hàng / giảm giá, phân bổ chi phí không qua duyệt (Nháp -> Ghi sổ).
-- Lịch sử duyệt cũ giữ nguyên để tra cứu; chứng từ cũ đang chờ duyệt chỉ còn hủy, đã duyệt vẫn ghi sổ được.
-- Đơn mua nháp không cộng hàng đang về (ghi sổ là nhập kho ngay, D-54); đơn đã duyệt trước D-61 giữ phần đã cộng đến
-- khi ghi sổ hoặc hủy.

UPDATE sys_document_types SET approval_mode = 'NONE' WHERE code IN ('DMH', 'THN', 'GGM', 'PBC');
UPDATE approval_workflows SET status = 'INACTIVE' WHERE document_type IN ('DMH', 'THN', 'GGM', 'PBC');

-- BR-PUR-04: đơn mua từ ngưỡng trở lên chỉ người có quyền ghi sổ vượt ngưỡng được ghi sổ (thay bước duyệt theo ngưỡng).
-- Cấp cho các vai trò đang có quyền duyệt đơn mua; các vai trò này chưa có quyền ghi sổ đơn mua nên cấp thêm để người
-- có thẩm quyền tự ghi sổ được. [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-order:post-over-threshold', 'PURCHASING', 'Ghi sổ đơn mua từ ngưỡng giá trị trở lên', 315);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM sys_role_permissions rp
    JOIN sys_permissions p_old ON p_old.id = rp.permission_id AND p_old.code = 'purchase-order:approve'
    JOIN sys_permissions p_new ON p_new.code IN ('purchase-order:post-over-threshold', 'purchase-order:post')
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = p_new.id);

UPDATE sys_parameter_definitions
SET name = 'Ngưỡng đơn mua cần quyền ghi sổ vượt ngưỡng (đồng)',
    description = 'BR-PUR-04, D-61. Đơn mua có tổng tiền (gồm thuế) từ ngưỡng trở lên chỉ người có quyền '
        'purchase-order:post-over-threshold được ghi sổ.'
WHERE param_key = 'purchasing.order-approval-threshold';

DELETE rp FROM sys_role_permissions rp
    JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code IN ('purchase-order:approve', 'purchase-return:approve', 'purchase-cost-allocation:approve');
DELETE FROM sys_permissions
WHERE code IN ('purchase-order:approve', 'purchase-return:approve', 'purchase-cost-allocation:approve');

UPDATE sys_permissions SET name = REPLACE(name, 'gửi duyệt, ', '')
WHERE code IN ('purchase-order:create', 'purchase-return:create', 'purchase-cost-allocation:create');
