-- DỮ LIỆU MẪU CHỈ CHO dev/test (database.md mục 2). Giai đoạn 6 - bán hàng.
-- [Giả định] Doanh nghiệp cấu hình lại bảng giá, quy tắc hạch toán ở màn hình tương ứng.

-- Trưởng phòng kinh doanh HN (ghi sổ đơn giá dưới sàn / vượt hạn mức, D-61), nhân viên giao hàng HN; mật khẩu dev như người dùng mẫu khác.
INSERT IGNORE INTO sys_users
    (company_id, default_branch_id, username, full_name, password_hash, status, own_data_only,
     credentials_changed_at, created_at, updated_at)
VALUES
    (1, 1, 'truongkd.hn', 'Trưởng phòng kinh doanh HN (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 1, 'giaohang.hn', 'Nhân viên giao hàng HN (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u JOIN sys_roles r
    ON (u.username = 'truongkd.hn' AND r.code = 'SALES_MANAGER') OR (u.username = 'giaohang.hn' AND r.code = 'DELIVERY_STAFF');

INSERT IGNORE INTO sys_user_data_scopes (user_id, scope_type, scope_id)
SELECT id, 'BRANCH', 1 FROM sys_users WHERE username IN ('truongkd.hn', 'giaohang.hn');

-- Khách phòng khám do nhân viên kinh doanh HN phụ trách (chỉ xem dữ liệu của mình), hạn mức 50 triệu, thanh toán 30 ngày;
-- đại lý thuộc nhóm DAI_LY, chiết khấu đại lý 5%.
INSERT IGNORE INTO partners (company_id, code, name, partner_kind, tax_code, address, customer_type, sales_user_id,
                             payment_term_days, credit_limit, min_remaining_shelf_life_days, status, created_at,
                             updated_at)
SELECT 1, 'KH_PK01', 'Phòng khám đa khoa mẫu', 'ORGANIZATION', '0100000002', 'Hà Nội', 'PRIVATE_CLINIC', u.id, 30,
       50000000, 90, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_users u WHERE u.username = 'kinhdoanh.hn';

INSERT IGNORE INTO partners (company_id, code, name, partner_kind, tax_code, address, customer_type, partner_group_id,
                             payment_term_days, credit_limit, agent_discount_percent, status, created_at, updated_at)
SELECT 1, 'KH_DL01', 'Đại lý thiết bị y tế mẫu', 'ORGANIZATION', '0100000003', 'Hải Phòng', 'AGENT', g.id, 45,
       500000000, 5, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM partner_groups g WHERE g.company_id = 1 AND g.code = 'DAI_LY';

INSERT IGNORE INTO partner_roles (partner_id, role)
SELECT id, 'CUSTOMER' FROM partners WHERE company_id = 1 AND code IN ('KH_PK01', 'KH_DL01')
UNION ALL SELECT id, 'AGENT' FROM partners WHERE company_id = 1 AND code = 'KH_DL01';

-- Bảng giá mẫu cho VT_ONG_01 (đơn vị Hộp): niêm yết 800.000, giá sàn 600.000, giá đại lý 700.000 (BR-SAL-02).
INSERT IGNORE INTO price_lists (company_id, code, name, price_type, target_type, partner_group_id, partner_id,
                                currency_code, valid_from, valid_to, status, created_at, updated_at)
VALUES (1, 'GIA_NY', 'Giá niêm yết (mẫu)', 'LIST', 'ALL', NULL, NULL, 'VND', '2020-01-01', NULL, 'ACTIVE',
        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
       (1, 'GIA_SAN', 'Giá sàn (mẫu)', 'FLOOR', 'ALL', NULL, NULL, 'VND', '2020-01-01', NULL, 'ACTIVE',
        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO price_lists (company_id, code, name, price_type, target_type, partner_group_id, partner_id,
                                currency_code, valid_from, valid_to, status, created_at, updated_at)
SELECT 1, 'GIA_DL', 'Giá đại lý (mẫu)', 'SALES', 'PARTNER_GROUP', g.id, NULL, 'VND', '2020-01-01', NULL, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM partner_groups g WHERE g.company_id = 1 AND g.code = 'DAI_LY';

INSERT IGNORE INTO price_list_items (price_list_id, product_id, unit_id, min_quantity, unit_price, created_at,
                                     updated_at)
SELECT l.id, p.id, u.id, 0, t.price, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT 'GIA_NY' AS code, 800000 AS price UNION ALL SELECT 'GIA_SAN', 600000 UNION ALL SELECT 'GIA_DL', 700000) t
JOIN price_lists l ON l.company_id = 1 AND l.code = t.code
JOIN products p ON p.company_id = 1 AND p.code = 'VT_ONG_01'
JOIN units u ON u.company_id = 1 AND u.code = 'HOP';

-- Quy tắc hạch toán mẫu ghi sổ đơn bán (D-35, D-54) [Chưa kiểm chứng]: doanh thu hàng hóa Nợ 131 / Có 5111, dịch vụ Có
-- 5113, thuế GTGT đầu ra Có 3331. Giá vốn ghi ở Giai đoạn 8. Kế toán trưởng cấu hình lại trước UAT.
INSERT IGNORE INTO posting_rules (company_id, document_type, transaction_code, product_type, partner_role,
    debit_account_id, credit_account_id, valid_from, valid_to, description, status, created_at, updated_at)
SELECT 1, t.document_type, t.transaction_code, t.product_type, NULL, d.id, c.id, '2020-01-01', NULL, t.description,
       'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (
    SELECT 'DBH' AS document_type, 'SALES' AS transaction_code, NULL AS product_type, '131' AS debit_code,
           '5111' AS credit_code, 'Mẫu - Doanh thu bán hàng hóa' AS description
    UNION ALL SELECT 'DBH', 'SALES', 'SERVICE', '131', '5113', 'Mẫu - Doanh thu cung cấp dịch vụ'
    UNION ALL SELECT 'DBH', 'SALES_VAT', NULL, '131', '3331', 'Mẫu - Thuế GTGT đầu ra'
) t
JOIN chart_of_accounts d ON d.company_id = 1 AND d.code = t.debit_code
JOIN chart_of_accounts c ON c.company_id = 1 AND c.code = t.credit_code;

-- Quy tắc hạch toán mẫu trả hàng bán (TLB) / giảm giá hàng bán (GGB) [Chưa kiểm chứng]: giảm doanh thu Nợ 5111 (dịch vụ
-- 5113) / Có 131, giảm thuế GTGT đầu ra Nợ 3331 / Có 131 (TT133 không dùng tài khoản giảm trừ doanh thu riêng).
INSERT IGNORE INTO posting_rules (company_id, document_type, transaction_code, product_type, partner_role,
    debit_account_id, credit_account_id, valid_from, valid_to, description, status, created_at, updated_at)
SELECT 1, t.document_type, t.transaction_code, t.product_type, NULL, d.id, c.id, '2020-01-01', NULL, t.description,
       'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (
    SELECT 'TLB' AS document_type, 'SALES_RETURN' AS transaction_code, NULL AS product_type, '5111' AS debit_code,
           '131' AS credit_code, 'Mẫu - Hàng bán bị trả lại' AS description
    UNION ALL SELECT 'TLB', 'SALES_RETURN_VAT', NULL, '3331', '131', 'Mẫu - Thuế GTGT hàng bán bị trả lại'
    UNION ALL SELECT 'GGB', 'SALES_DISCOUNT', NULL, '5111', '131', 'Mẫu - Giảm giá hàng bán'
    UNION ALL SELECT 'GGB', 'SALES_DISCOUNT', 'SERVICE', '5113', '131', 'Mẫu - Giảm giá dịch vụ'
    UNION ALL SELECT 'GGB', 'SALES_DISCOUNT_VAT', NULL, '3331', '131', 'Mẫu - Thuế GTGT được giảm'
) t
JOIN chart_of_accounts d ON d.company_id = 1 AND d.code = t.debit_code
JOIN chart_of_accounts c ON c.company_id = 1 AND c.code = t.credit_code;

-- Nhân viên bán hàng cửa hàng HN kiêm nhiệm kinh doanh, kho, kế toán công nợ (D-08): lập, ghi sổ đơn bán một mình.
INSERT IGNORE INTO sys_users
    (company_id, default_branch_id, username, full_name, password_hash, status, own_data_only,
     credentials_changed_at, created_at, updated_at)
VALUES
    (1, 1, 'banhang.hn', 'Bán hàng kiêm kho, kế toán HN (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u JOIN sys_roles r ON r.code IN ('SALES_MANAGER', 'WAREHOUSE_MANAGER', 'AR_ACCOUNTANT')
WHERE u.username = 'banhang.hn';

INSERT IGNORE INTO sys_user_data_scopes (user_id, scope_type, scope_id)
SELECT id, 'BRANCH', 1 FROM sys_users WHERE username = 'banhang.hn';
