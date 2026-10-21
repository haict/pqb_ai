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
