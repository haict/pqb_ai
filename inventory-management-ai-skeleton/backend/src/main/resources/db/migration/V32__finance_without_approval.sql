-- D-61 (2026-10-21), đợt 1 - công nợ, quỹ: chứng từ không qua duyệt (Nháp -> Ghi sổ).
-- Lịch sử duyệt cũ (approval_requests, approval_actions) giữ nguyên để tra cứu; chứng từ cũ đang chờ duyệt chỉ còn hủy,
-- đã duyệt vẫn ghi sổ được.

UPDATE sys_document_types SET approval_mode = 'NONE'
WHERE code IN ('PT', 'PC', 'BC', 'UNC', 'CTN', 'DTCN', 'DCCN', 'TTTU', 'BBDC', 'KKQ');
UPDATE approval_workflows SET status = 'INACTIVE'
WHERE document_type IN ('PT', 'PC', 'BC', 'UNC', 'CTN', 'DTCN', 'DCCN', 'TTTU', 'BBDC', 'KKQ');

-- BR-CSH-03: chứng từ chi từ ngưỡng trở lên chỉ người có quyền ghi sổ vượt ngưỡng được ghi sổ (thay bước duyệt chi).
-- Cấp cho các vai trò đang có quyền duyệt chi.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('cash-document:post-over-threshold', 'FINANCE', 'Ghi sổ chứng từ chi từ ngưỡng chi trở lên', 735);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT rp.role_id, p_new.id
FROM sys_role_permissions rp
    JOIN sys_permissions p_old ON p_old.id = rp.permission_id AND p_old.code = 'cash-document:approve'
    JOIN sys_permissions p_new ON p_new.code = 'cash-document:post-over-threshold';

UPDATE sys_parameter_definitions
SET name = 'Ngưỡng chi cần quyền ghi sổ vượt ngưỡng (đồng)',
    description = 'BR-CSH-03, D-61. Phiếu chi, ủy nhiệm chi, chuyển tiền nội bộ có số tiền từ ngưỡng trở lên chỉ người '
        'có quyền cash-document:post-over-threshold được ghi sổ.'
WHERE param_key = 'finance.payment-approval-threshold';

DELETE rp FROM sys_role_permissions rp
    JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code IN ('cash-document:approve', 'debt-offset:approve', 'debt-adjustment:approve', 'cash-count:approve');
DELETE FROM sys_permissions
WHERE code IN ('cash-document:approve', 'debt-offset:approve', 'debt-adjustment:approve', 'cash-count:approve');

UPDATE sys_permissions SET name = REPLACE(name, 'gửi duyệt, ', '')
WHERE code IN ('cash-document:create', 'debt-offset:create', 'debt-adjustment:create', 'cash-count:create');
