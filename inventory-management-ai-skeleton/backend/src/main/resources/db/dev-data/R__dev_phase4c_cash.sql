-- DỮ LIỆU MẪU CHỈ CHO dev/test (Giai đoạn 4C - quỹ, ngân hàng). Không bao giờ chạy ở uat/prod (database.md mục 2).
-- [Chưa kiểm chứng] Quy tắc hạch toán mẫu theo hiện trạng TT133 để thử nghiệm; kế toán trưởng cấu hình lại trước UAT.

-- Quỹ tiền mặt, tài khoản ngân hàng mẫu (số tài khoản giả).
INSERT IGNORE INTO cash_accounts (company_id, branch_id, code, name, account_type, gl_account_id, bank_name,
    bank_account_no, currency_code, balance, status, created_at, updated_at)
SELECT 1, 1, 'QTM-HN', 'Quỹ tiền mặt Hà Nội (mẫu)', 'CASH', a.id, NULL, NULL, 'VND', 0, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts a WHERE a.company_id = 1 AND a.code = '1111';
INSERT IGNORE INTO cash_accounts (company_id, branch_id, code, name, account_type, gl_account_id, bank_name,
    bank_account_no, currency_code, balance, status, created_at, updated_at)
SELECT 1, 1, 'NH-HN-01', 'Tài khoản ngân hàng Hà Nội (mẫu)', 'BANK', a.id, 'Ngân hàng mẫu', '0000000001', 'VND', 0,
       'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts a WHERE a.company_id = 1 AND a.code = '1121';
INSERT IGNORE INTO cash_accounts (company_id, branch_id, code, name, account_type, gl_account_id, bank_name,
    bank_account_no, currency_code, balance, status, created_at, updated_at)
SELECT 1, 2, 'QTM-HCM', 'Quỹ tiền mặt Hồ Chí Minh (mẫu)', 'CASH', a.id, NULL, NULL, 'VND', 0, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts a WHERE a.company_id = 1 AND a.code = '1111';

-- Quy tắc hạch toán mẫu: vế tiền do quỹ của chứng từ quyết định (để trống); thu chi khác, chuyển tiền để trống cả
-- hai vế (tài khoản đối ứng theo dòng chứng từ).
INSERT IGNORE INTO posting_rules (company_id, document_type, transaction_code, product_type, partner_role,
    debit_account_id, credit_account_id, valid_from, valid_to, description, status, created_at, updated_at)
SELECT 1, t.document_type, t.transaction_code, NULL, NULL, d.id, c.id, '2020-01-01', NULL, t.description, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (
    SELECT 'PT' AS document_type, 'CUSTOMER_RECEIPT' AS transaction_code, NULL AS debit_code, '131' AS credit_code,
           'Mẫu - Thu tiền khách hàng bằng tiền mặt' AS description
    UNION ALL SELECT 'BC', 'CUSTOMER_RECEIPT', NULL, '131', 'Mẫu - Khách hàng chuyển khoản'
    UNION ALL SELECT 'PC', 'SUPPLIER_PAYMENT', '331', NULL, 'Mẫu - Trả nhà cung cấp bằng tiền mặt'
    UNION ALL SELECT 'UNC', 'SUPPLIER_PAYMENT', '331', NULL, 'Mẫu - Trả nhà cung cấp bằng chuyển khoản'
    UNION ALL SELECT 'PT', 'OTHER_RECEIPT', NULL, NULL, 'Mẫu - Thu khác, tài khoản đối ứng theo dòng'
    UNION ALL SELECT 'BC', 'OTHER_RECEIPT', NULL, NULL, 'Mẫu - Thu khác qua ngân hàng'
    UNION ALL SELECT 'PC', 'OTHER_PAYMENT', NULL, NULL, 'Mẫu - Chi khác, tài khoản đối ứng theo dòng'
    UNION ALL SELECT 'UNC', 'OTHER_PAYMENT', NULL, NULL, 'Mẫu - Chi khác qua ngân hàng'
    UNION ALL SELECT 'CTN', 'INTERNAL_TRANSFER', NULL, NULL, 'Mẫu - Chuyển tiền giữa các quỹ'
) t
LEFT JOIN chart_of_accounts d ON d.company_id = 1 AND d.code = t.debit_code
LEFT JOIN chart_of_accounts c ON c.company_id = 1 AND c.code = t.credit_code;
