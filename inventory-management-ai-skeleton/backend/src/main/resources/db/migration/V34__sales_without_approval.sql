-- D-61 (2026-10-21), đợt 3 - bán hàng: đơn bán, trả hàng / giảm giá hàng bán không qua duyệt (Nháp -> Ghi sổ).
-- Lịch sử duyệt cũ giữ nguyên để tra cứu; chứng từ cũ đang chờ duyệt chỉ còn hủy, đã duyệt vẫn ghi sổ được.
-- Đơn nháp giữ hàng được (khi lưu nếu chọn giữ hàng, hoặc theo lệnh giữ hàng); nhả khi ghi sổ / hủy.

UPDATE sys_document_types SET approval_mode = 'NONE' WHERE code IN ('DBH', 'TLB', 'GGB');
UPDATE approval_workflows SET status = 'INACTIVE' WHERE document_type IN ('DBH', 'TLB', 'GGB');

-- BR-SAL-03, BR-SAL-14, D-22: giá dưới sàn / chiết khấu vượt ngưỡng, vượt hạn mức tín dụng / nợ quá hạn chỉ người có
-- quyền vượt tương ứng được ghi sổ (thay bước duyệt). Cấp cho các vai trò đang có quyền duyệt đơn bán; các vai trò này
-- chưa có quyền ghi sổ đơn bán nên cấp thêm để người có thẩm quyền tự ghi sổ được. [Suy luận] Doanh nghiệp điều chỉnh
-- ở màn hình vai trò.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-order:override-price',  'SALES', 'Ghi sổ đơn bán giá dưới sàn / chiết khấu vượt ngưỡng',       416),
    ('sales-order:override-credit', 'SALES', 'Ghi sổ đơn bán vượt hạn mức tín dụng / khách còn nợ quá hạn', 417);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM sys_role_permissions rp
    JOIN sys_permissions p_old ON p_old.id = rp.permission_id AND p_old.code = 'sales-order:approve'
    JOIN sys_permissions p_new
        ON p_new.code IN ('sales-order:override-price', 'sales-order:override-credit', 'sales-order:post')
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = p_new.id);

UPDATE sys_parameter_definitions
SET description = 'BR-SAL-03, D-61. Chiết khấu một dòng vượt ngưỡng (hoặc giá dưới giá sàn) thì chỉ người có quyền '
    'sales-order:override-price được ghi sổ đơn bán.'
WHERE param_key = 'sales.discount-approval-percent';

DELETE rp FROM sys_role_permissions rp
    JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code IN ('sales-order:approve', 'sales-return:approve');
DELETE FROM sys_permissions WHERE code IN ('sales-order:approve', 'sales-return:approve');

UPDATE sys_permissions SET name = REPLACE(name, 'gửi duyệt, ', '')
WHERE code IN ('sales-order:create', 'sales-return:create');
