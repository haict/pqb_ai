-- DỮ LIỆU MẪU CHỈ CHO dev/test (database.md mục 2). Giai đoạn 5 - mua hàng.
-- Luồng duyệt mẫu: đề nghị mua do Giám đốc chi nhánh duyệt; đơn mua từ ngưỡng purchasing.order-approval-threshold do
-- Giám đốc duyệt; phiếu nhận hàng vượt dung sai do Giám đốc chi nhánh duyệt. [Giả định] Doanh nghiệp cấu hình lại ở màn hình luồng duyệt.

-- Nhân viên mua hàng chi nhánh Hà Nội; mật khẩu dev như các người dùng mẫu khác.
INSERT IGNORE INTO sys_users
    (company_id, default_branch_id, username, full_name, password_hash, status, own_data_only,
     credentials_changed_at, created_at, updated_at)
VALUES
    (1, 1, 'muahang.hn', 'Nhân viên mua hàng HN (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u JOIN sys_roles r ON r.code = 'PURCHASING_STAFF' WHERE u.username = 'muahang.hn';

INSERT IGNORE INTO sys_user_data_scopes (user_id, scope_type, scope_id)
SELECT id, 'BRANCH', 1 FROM sys_users WHERE username = 'muahang.hn';

INSERT INTO approval_workflows (company_id, document_type, name, branch_id, priority, status, created_at, updated_at)
SELECT 1, t.code, CONCAT('Mẫu - Duyệt ', t.code), NULL, 0, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT 'DNM' AS code UNION ALL SELECT 'DMH' UNION ALL SELECT 'PNH') t
WHERE NOT EXISTS (SELECT 1 FROM approval_workflows w
                  WHERE w.company_id = 1 AND w.document_type = t.code AND w.name = CONCAT('Mẫu - Duyệt ', t.code));

INSERT IGNORE INTO approval_workflow_steps (workflow_id, step_no, approver_type, role_id, user_id)
SELECT w.id, 1, 'ROLE', r.id, NULL
FROM approval_workflows w JOIN sys_roles r
    ON (w.document_type IN ('DNM', 'PNH') AND r.code = 'BRANCH_DIRECTOR')
    OR (w.document_type = 'DMH' AND r.code = 'DIRECTOR')
WHERE w.company_id = 1 AND w.document_type IN ('DNM', 'DMH', 'PNH') AND w.name LIKE 'Mẫu - Duyệt %';

-- Quy tắc hạch toán mẫu hóa đơn mua (D-34) [Chưa kiểm chứng]: hàng hóa Nợ 1561, dịch vụ Nợ 6422, thuế GTGT Nợ 1331,
-- đối ứng Có 331. Khi phiếu nhập kho có bút toán (Giai đoạn 8), vế Nợ hàng hóa đổi sang tài khoản trung gian
-- "hàng về chưa có hóa đơn" để không ghi trùng [Suy luận]; kế toán trưởng cấu hình lại trước UAT.
INSERT IGNORE INTO posting_rules (company_id, document_type, transaction_code, product_type, partner_role,
    debit_account_id, credit_account_id, valid_from, valid_to, description, status, created_at, updated_at)
SELECT 1, 'HDM', t.transaction_code, t.product_type, NULL, d.id, c.id, '2020-01-01', NULL, t.description, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (
    SELECT 'PURCHASE' AS transaction_code, NULL AS product_type, '1561' AS debit_code, '331' AS credit_code,
           'Mẫu - Mua hàng hóa theo hóa đơn' AS description
    UNION ALL SELECT 'PURCHASE', 'SERVICE', '6422', '331', 'Mẫu - Mua dịch vụ theo hóa đơn'
    UNION ALL SELECT 'PURCHASE_VAT', NULL, '1331', '331', 'Mẫu - Thuế GTGT đầu vào'
) t
JOIN chart_of_accounts d ON d.company_id = 1 AND d.code = t.debit_code
JOIN chart_of_accounts c ON c.company_id = 1 AND c.code = t.credit_code;

-- Dịch vụ mua không qua kho (BR-PUR-16), thuế mặc định 10%.
INSERT IGNORE INTO products (company_id, code, name, category_id, product_type, base_unit_id, lot_tracked,
                             expiry_tracked, serial_tracked, default_tax_rate_id, status, created_at, updated_at)
SELECT 1, 'DV_VC', 'Dịch vụ vận chuyển (mẫu)', c.id, 'SERVICE', u.id, FALSE, FALSE, FALSE, t.id, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM product_categories c, units u, tax_rates t
WHERE c.company_id = 1 AND c.code = 'VT' AND u.company_id = 1 AND u.code = 'CAI'
  AND t.company_id = 1 AND t.code = 'VAT10';

-- Giai đoạn 5D [Chưa kiểm chứng]: trả hàng NCC (THN) Nợ 331 / Có 1561, thuế Có 1331; giảm giá hàng mua (GGM) Nợ 331 /
-- Có 1561 (dịch vụ Có 6422), thuế Có 1331; phân bổ chi phí mua (PBC) chuyển chi phí dịch vụ vào giá trị hàng Nợ 1562 /
-- Có 6422 (cùng tài khoản đã ghi khi ghi sổ hóa đơn dịch vụ). Kế toán trưởng cấu hình lại trước UAT.
INSERT IGNORE INTO posting_rules (company_id, document_type, transaction_code, product_type, partner_role,
    debit_account_id, credit_account_id, valid_from, valid_to, description, status, created_at, updated_at)
SELECT 1, t.document_type, t.transaction_code, t.product_type, NULL, d.id, c.id, '2020-01-01', NULL, t.description,
       'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (
    SELECT 'THN' AS document_type, 'PURCHASE_RETURN' AS transaction_code, NULL AS product_type, '331' AS debit_code,
           '1561' AS credit_code, 'Mẫu - Trả hàng nhà cung cấp' AS description
    UNION ALL SELECT 'THN', 'PURCHASE_RETURN_VAT', NULL, '331', '1331', 'Mẫu - Thuế GTGT hàng trả lại'
    UNION ALL SELECT 'GGM', 'PURCHASE_DISCOUNT', NULL, '331', '1561', 'Mẫu - Giảm giá hàng mua'
    UNION ALL SELECT 'GGM', 'PURCHASE_DISCOUNT', 'SERVICE', '331', '6422', 'Mẫu - Giảm giá dịch vụ mua'
    UNION ALL SELECT 'GGM', 'PURCHASE_DISCOUNT_VAT', NULL, '331', '1331', 'Mẫu - Thuế GTGT được giảm'
    UNION ALL SELECT 'PBC', 'LANDED_COST', NULL, '1562', '6422', 'Mẫu - Phân bổ chi phí mua hàng'
) t
JOIN chart_of_accounts d ON d.company_id = 1 AND d.code = t.debit_code
JOIN chart_of_accounts c ON c.company_id = 1 AND c.code = t.credit_code;
