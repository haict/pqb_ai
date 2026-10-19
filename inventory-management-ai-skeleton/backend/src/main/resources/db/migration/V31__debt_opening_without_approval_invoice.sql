-- D-60 (2026-10-18): chứng từ công nợ đầu kỳ không qua duyệt (Nháp -> Ghi sổ) và không theo dõi số / ngày hóa đơn.
-- Dòng chỉ còn đối tượng, hạn thanh toán, số tiền, ghi chú. Dữ liệu cũ giữ nguyên số / ngày hóa đơn để tra cứu.

ALTER TABLE debt_opening_lines DROP CHECK ck_debt_opening_lines_dates;
ALTER TABLE debt_opening_lines MODIFY invoice_no VARCHAR(50) NULL;
ALTER TABLE debt_opening_lines MODIFY invoice_date DATE NULL;

UPDATE sys_document_types SET approval_mode = 'NONE' WHERE code = 'CNDK';
UPDATE approval_workflows SET status = 'INACTIVE' WHERE document_type = 'CNDK';

-- Đảo dùng quyền ghi sổ; bỏ quyền duyệt công nợ đầu kỳ.
DELETE rp FROM sys_role_permissions rp
    JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code = 'debt-opening:approve';
DELETE FROM sys_permissions WHERE code = 'debt-opening:approve';
UPDATE sys_permissions SET name = 'Ghi sổ, đảo công nợ đầu kỳ' WHERE code = 'debt-opening:post';
UPDATE sys_permissions SET name = 'Lập, sửa, hủy công nợ đầu kỳ' WHERE code = 'debt-opening:create';
