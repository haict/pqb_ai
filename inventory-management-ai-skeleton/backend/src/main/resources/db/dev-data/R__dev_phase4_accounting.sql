-- DỮ LIỆU MẪU CHỈ CHO dev/test (Giai đoạn 4A - kế toán lõi). Không bao giờ chạy ở uat/prod (database.md mục 2).
-- [Chưa kiểm chứng] Một phần hệ thống tài khoản theo TT133/2016/TT-BTC (hiện trạng H-04) để thử nghiệm; production
-- nạp hệ thống tài khoản bằng nhập Excel sau khi kế toán trưởng xác nhận chế độ kế toán áp dụng (BR mục 13).

INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '111', 'Tiền mặt', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1111', 'Tiền Việt Nam', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '111';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1112', 'Ngoại tệ', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '111';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '112', 'Tiền gửi ngân hàng', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1121', 'Tiền Việt Nam', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '112';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1122', 'Ngoại tệ', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '112';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '131', 'Phải thu của khách hàng', NULL, 1, 'BOTH', TRUE, TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '133', 'Thuế GTGT được khấu trừ', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1331', 'Thuế GTGT được khấu trừ của hàng hóa, dịch vụ', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '133';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '138', 'Phải thu khác', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1388', 'Phải thu khác', p.id, p.account_level + 1, 'DEBIT', TRUE, TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '138';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '141', 'Tạm ứng', NULL, 1, 'DEBIT', TRUE, TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '152', 'Nguyên liệu, vật liệu', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '153', 'Công cụ, dụng cụ', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '154', 'Chi phí sản xuất, kinh doanh dở dang', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '156', 'Hàng hóa', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1561', 'Giá mua hàng hóa', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '156';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '1562', 'Chi phí thu mua hàng hóa', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '156';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '157', 'Hàng gửi đi bán', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '211', 'Tài sản cố định', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '214', 'Hao mòn tài sản cố định', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '242', 'Chi phí trả trước', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '331', 'Phải trả cho người bán', NULL, 1, 'BOTH', TRUE, TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '333', 'Thuế và các khoản phải nộp Nhà nước', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '3331', 'Thuế giá trị gia tăng phải nộp', p.id, p.account_level + 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '333';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '3334', 'Thuế thu nhập doanh nghiệp', p.id, p.account_level + 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '333';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '334', 'Phải trả người lao động', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '335', 'Chi phí phải trả', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '338', 'Phải trả, phải nộp khác', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '3388', 'Phải trả, phải nộp khác', p.id, p.account_level + 1, 'CREDIT', TRUE, TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '338';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '341', 'Vay và nợ thuê tài chính', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '411', 'Vốn đầu tư của chủ sở hữu', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '4111', 'Vốn góp của chủ sở hữu', p.id, p.account_level + 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '411';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '421', 'Lợi nhuận sau thuế chưa phân phối', NULL, 1, 'BOTH', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '4211', 'Lợi nhuận sau thuế chưa phân phối năm trước', p.id, p.account_level + 1, 'BOTH', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '421';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '4212', 'Lợi nhuận sau thuế chưa phân phối năm nay', p.id, p.account_level + 1, 'BOTH', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '421';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '511', 'Doanh thu bán hàng và cung cấp dịch vụ', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '5111', 'Doanh thu bán hàng hóa', p.id, p.account_level + 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '511';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '5113', 'Doanh thu cung cấp dịch vụ', p.id, p.account_level + 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '511';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '515', 'Doanh thu hoạt động tài chính', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '632', 'Giá vốn hàng bán', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '635', 'Chi phí tài chính', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '642', 'Chi phí quản lý kinh doanh', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '6421', 'Chi phí bán hàng', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '642';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
SELECT 1, '6422', 'Chi phí quản lý doanh nghiệp', p.id, p.account_level + 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM chart_of_accounts p WHERE p.company_id = 1 AND p.code = '642';
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '711', 'Thu nhập khác', NULL, 1, 'CREDIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '811', 'Chi phí khác', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '821', 'Chi phí thuế thu nhập doanh nghiệp', NULL, 1, 'DEBIT', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO chart_of_accounts (company_id, code, name, parent_id, account_level, balance_side, postable,
    partner_required, status, created_at, updated_at)
VALUES (1, '911', 'Xác định kết quả kinh doanh', NULL, 1, 'BOTH', TRUE, FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- Tài khoản có tài khoản con là tài khoản tổng hợp, không hạch toán trực tiếp.
UPDATE chart_of_accounts p JOIN chart_of_accounts c ON c.parent_id = p.id SET p.postable = FALSE
WHERE p.company_id = 1;

-- Kế toán công nợ, thủ quỹ mẫu (Giai đoạn 4), mật khẩu dev Erp@2026.
INSERT IGNORE INTO sys_users
    (company_id, default_branch_id, username, full_name, password_hash, status, own_data_only,
     credentials_changed_at, created_at, updated_at)
VALUES
    (1, 1, 'ketoancongno', 'Kế toán công nợ (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 1, 'thuquy.hn', 'Thủ quỹ Hà Nội (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u JOIN sys_roles r
    ON (u.username = 'ketoancongno' AND r.code = 'AR_ACCOUNTANT') OR (u.username = 'thuquy.hn' AND r.code = 'CASHIER');

INSERT IGNORE INTO sys_user_data_scopes (user_id, scope_type, scope_id)
SELECT id, 'COMPANY', 1 FROM sys_users WHERE username = 'ketoancongno'
UNION ALL SELECT id, 'BRANCH', 1 FROM sys_users WHERE username = 'thuquy.hn';

-- Luồng duyệt mẫu công nợ đầu kỳ (Giai đoạn 4B): Kế toán trưởng duyệt một bước. [Suy luận] doanh nghiệp cấu hình lại.
INSERT INTO approval_workflows (company_id, document_type, name, branch_id, priority, status, created_at, updated_at)
SELECT 1, 'CNDK', 'Mẫu - Chứng từ công nợ đầu kỳ', NULL, 0, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM approval_workflows w
                  WHERE w.company_id = 1 AND w.document_type = 'CNDK' AND w.name = 'Mẫu - Chứng từ công nợ đầu kỳ');

INSERT IGNORE INTO approval_workflow_steps (workflow_id, step_no, approver_type, role_id, user_id)
SELECT w.id, 1, 'ROLE', r.id, NULL
FROM approval_workflows w JOIN sys_roles r ON r.code = 'CHIEF_ACCOUNTANT'
WHERE w.company_id = 1 AND w.document_type = 'CNDK' AND w.name = 'Mẫu - Chứng từ công nợ đầu kỳ';
