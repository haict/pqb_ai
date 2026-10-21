-- D-62 (2026-10-21): bỏ ghi sổ đơn mua / đơn bán để sửa lại. Sổ phụ không sửa / xóa: hệ thống sinh dòng ngược ngày
-- hạch toán gốc, đơn về nháp giữ số. Quyền mới gán cho vai trò đang có quyền đảo đơn tương ứng.

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-order:unpost', 'PURCHASING', 'Bỏ ghi sổ đơn mua để sửa lại (về nháp)', 316),
    ('sales-order:unpost',    'SALES',      'Bỏ ghi sổ đơn bán để sửa lại (về nháp)', 418);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM sys_role_permissions rp
    JOIN sys_permissions p_old ON p_old.id = rp.permission_id
    JOIN sys_permissions p_new ON (p_old.code = 'purchase-order:reverse' AND p_new.code = 'purchase-order:unpost')
        OR (p_old.code = 'sales-order:reverse' AND p_new.code = 'sales-order:unpost')
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = p_new.id);

-- Ghi sổ lại sau khi bỏ ghi sổ sinh khoản công nợ / bút toán gốc mới cho cùng chứng từ nguồn: ràng buộc duy nhất chỉ
-- áp dụng cho bản còn hiệu lực (active_key = NULL khi đã đảo; MySQL không coi NULL là trùng).
ALTER TABLE debt_items
    ADD COLUMN active_key TINYINT AS (IF(status = 'REVERSED', NULL, 1)) STORED,
    DROP INDEX uk_debt_items_source,
    ADD CONSTRAINT uk_debt_items_source
        UNIQUE (company_id, debt_type, source_type, source_id, source_line_key, active_key);

ALTER TABLE journal_entries
    ADD COLUMN active_key TINYINT AS (IF(status = 'REVERSED', NULL, 1)) STORED,
    DROP INDEX uk_journal_entries_source,
    ADD CONSTRAINT uk_journal_entries_source UNIQUE (company_id, source_type, source_id, reversal_key, active_key);
