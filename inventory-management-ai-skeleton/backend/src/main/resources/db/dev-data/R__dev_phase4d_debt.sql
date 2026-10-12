-- DỮ LIỆU MẪU CHỈ CHO dev/test (Giai đoạn 4D - bù trừ, điều chỉnh công nợ, tạm ứng, kiểm kê quỹ). Không bao giờ chạy ở
-- uat/prod. [Chưa kiểm chứng] Tài khoản theo hiện trạng TT133; kế toán trưởng cấu hình lại trước UAT.

-- Quy tắc hạch toán mẫu: vế để trống do chứng từ quyết định (tài khoản đối ứng theo dòng, tài khoản tiền của quỹ).
INSERT IGNORE INTO posting_rules (company_id, document_type, transaction_code, product_type, partner_role,
    debit_account_id, credit_account_id, valid_from, valid_to, description, status, created_at, updated_at)
SELECT 1, t.document_type, t.transaction_code, NULL, NULL, d.id, c.id, '2020-01-01', NULL, t.description, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (
    SELECT 'DTCN' AS document_type, 'AR_AP_OFFSET' AS transaction_code, '331' AS debit_code, '131' AS credit_code,
           'Mẫu - Bù trừ phải thu với phải trả' AS description
    UNION ALL SELECT 'DCCN', 'RECEIVABLE_INCREASE', '131', NULL, 'Mẫu - Điều chỉnh tăng phải thu'
    UNION ALL SELECT 'DCCN', 'RECEIVABLE_DECREASE', NULL, '131', 'Mẫu - Điều chỉnh giảm phải thu'
    UNION ALL SELECT 'DCCN', 'PAYABLE_INCREASE', NULL, '331', 'Mẫu - Điều chỉnh tăng phải trả'
    UNION ALL SELECT 'DCCN', 'PAYABLE_DECREASE', '331', NULL, 'Mẫu - Điều chỉnh giảm phải trả'
    UNION ALL SELECT 'DCCN', 'ADVANCE_INCREASE', '141', NULL, 'Mẫu - Điều chỉnh tăng tạm ứng'
    UNION ALL SELECT 'DCCN', 'ADVANCE_DECREASE', NULL, '141', 'Mẫu - Điều chỉnh giảm tạm ứng'
    UNION ALL SELECT 'TTTU', 'ADVANCE_DECREASE', NULL, '141', 'Mẫu - Thanh toán tạm ứng (chi phí theo dòng)'
    UNION ALL SELECT 'PC', 'EMPLOYEE_ADVANCE', '141', NULL, 'Mẫu - Chi tạm ứng bằng tiền mặt'
    UNION ALL SELECT 'UNC', 'EMPLOYEE_ADVANCE', '141', NULL, 'Mẫu - Chi tạm ứng bằng chuyển khoản'
    UNION ALL SELECT 'PT', 'ADVANCE_RETURN', NULL, '141', 'Mẫu - Nhân viên hoàn ứng bằng tiền mặt'
    UNION ALL SELECT 'BC', 'ADVANCE_RETURN', NULL, '141', 'Mẫu - Nhân viên hoàn ứng bằng chuyển khoản'
    UNION ALL SELECT 'KKQ', 'CASH_SURPLUS', NULL, '711', 'Mẫu - Kiểm kê quỹ thừa'
    UNION ALL SELECT 'KKQ', 'CASH_SHORTAGE', '811', NULL, 'Mẫu - Kiểm kê quỹ thiếu'
) t
LEFT JOIN chart_of_accounts d ON d.company_id = 1 AND d.code = t.debit_code
LEFT JOIN chart_of_accounts c ON c.company_id = 1 AND c.code = t.credit_code;

-- Luồng duyệt mẫu: điều chỉnh công nợ, kiểm kê quỹ do Kế toán trưởng duyệt một bước. [Suy luận] cấu hình lại.
INSERT INTO approval_workflows (company_id, document_type, name, branch_id, priority, status, created_at, updated_at)
SELECT 1, t.code, CONCAT('Mẫu - Duyệt ', t.code), NULL, 0, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT 'DCCN' AS code UNION ALL SELECT 'KKQ') t
WHERE NOT EXISTS (SELECT 1 FROM approval_workflows w
                  WHERE w.company_id = 1 AND w.document_type = t.code AND w.name = CONCAT('Mẫu - Duyệt ', t.code));

INSERT IGNORE INTO approval_workflow_steps (workflow_id, step_no, approver_type, role_id, user_id)
SELECT w.id, 1, 'ROLE', r.id, NULL
FROM approval_workflows w JOIN sys_roles r ON r.code = 'CHIEF_ACCOUNTANT'
WHERE w.company_id = 1 AND w.document_type IN ('DCCN', 'KKQ') AND w.name LIKE 'Mẫu - Duyệt %';
