-- DỮ LIỆU MẪU CHỈ CHO dev/test (Giai đoạn 3 - kho). Không bao giờ chạy ở uat/prod (database.md mục 2).
-- Không có tồn đầu kỳ mẫu: tồn chỉ phát sinh từ chứng từ đã ghi sổ (CLAUDE.md mục 1); lập phiếu nhập trên giao diện.

-- Thủ kho Hà Nội (WAREHOUSE_MANAGER), mật khẩu dev Erp@2026.
INSERT IGNORE INTO sys_users
    (company_id, default_branch_id, username, full_name, password_hash, status, own_data_only,
     credentials_changed_at, created_at, updated_at)
VALUES
    (1, 1, 'thukho.hn', 'Quản lý kho Hà Nội (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u JOIN sys_roles r ON r.code = 'WAREHOUSE_MANAGER'
WHERE u.username = 'thukho.hn';

INSERT IGNORE INTO sys_user_data_scopes (user_id, scope_type, scope_id)
SELECT id, 'BRANCH', 1 FROM sys_users WHERE username = 'thukho.hn';

-- Luồng duyệt một bước cho các loại chứng từ kho bắt buộc duyệt (D-04). [Suy luận] người duyệt mẫu:
-- Giám đốc cho chứng từ kho, Kế toán trưởng cho chứng từ đảo; doanh nghiệp cấu hình lại ở màn hình Luồng duyệt.
INSERT INTO approval_workflows (company_id, document_type, name, branch_id, priority, status, created_at, updated_at)
SELECT 1, t.code, CONCAT('Mẫu - ', t.name), NULL, 0, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_document_types t
WHERE t.code IN ('PXK', 'PXH', 'PDC', 'PCL', 'PDK', 'PKK', 'PTH', 'CSX')
  AND NOT EXISTS (SELECT 1 FROM approval_workflows w
                  WHERE w.company_id = 1 AND w.document_type = t.code AND w.name = CONCAT('Mẫu - ', t.name));

INSERT IGNORE INTO approval_workflow_steps (workflow_id, step_no, approver_type, role_id, user_id)
SELECT w.id, 1, 'ROLE', r.id, NULL
FROM approval_workflows w
JOIN sys_document_types t ON t.code = w.document_type
JOIN sys_roles r ON r.code = CASE WHEN w.document_type = 'PDK' THEN 'CHIEF_ACCOUNTANT' ELSE 'DIRECTOR' END
WHERE w.company_id = 1 AND w.name = CONCAT('Mẫu - ', t.name)
  AND w.document_type IN ('PXK', 'PXH', 'PDC', 'PCL', 'PDK', 'PKK', 'PTH', 'CSX');
