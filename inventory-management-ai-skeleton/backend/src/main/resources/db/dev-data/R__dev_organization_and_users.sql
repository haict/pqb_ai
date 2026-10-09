-- DỮ LIỆU MẪU CHỈ CHO dev/test. Không bao giờ chạy ở uat/prod (database.md mục 2).
-- Mọi người dùng mẫu có mật khẩu dev: Erp@2026 (băm BCrypt). Không dùng mật khẩu này ở nơi khác.
-- Chi nhánh là dữ liệu giả lập; danh sách chi nhánh thật do doanh nghiệp cung cấp (D-23).

INSERT IGNORE INTO companies (id, code, name, status, created_at, updated_at) VALUES
    (1, 'B2K', 'Công ty TNHH Công nghệ B2K (dữ liệu mẫu)', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO branches (id, company_id, code, name, status, created_at, updated_at) VALUES
    (1, 1, 'HN',  'Chi nhánh Hà Nội (mẫu)',         'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (2, 1, 'HCM', 'Chi nhánh Hồ Chí Minh (mẫu)',    'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO departments (id, branch_id, code, name, status, created_at, updated_at) VALUES
    (1, 1, 'KD',  'Phòng kinh doanh', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (2, 1, 'KT',  'Phòng kế toán',    'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (3, 2, 'KHO', 'Kho Hồ Chí Minh',  'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_users
    (id, company_id, default_branch_id, username, full_name, password_hash, status, own_data_only,
     credentials_changed_at, created_at, updated_at)
VALUES
    (1, 1, 1, 'admin',        'Quản trị hệ thống (mẫu)',   '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (2, 1, 1, 'giamdoc',      'Giám đốc (mẫu)',            '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (3, 1, 1, 'kinhdoanh.hn', 'Nhân viên kinh doanh HN (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', TRUE,  UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (4, 1, 2, 'kho.hcm',      'Thủ kho HCM (mẫu)',         '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (5, 1, 1, 'giamdoc.hn',   'Giám đốc chi nhánh HN (mẫu)', '{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO', 'ACTIVE', FALSE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u JOIN sys_roles r ON
    (u.username = 'admin'        AND r.code = 'SYSTEM_ADMIN') OR
    (u.username = 'giamdoc'      AND r.code = 'DIRECTOR') OR
    (u.username = 'kinhdoanh.hn' AND r.code = 'SALES_STAFF') OR
    (u.username = 'kho.hcm'      AND r.code = 'WAREHOUSE_STAFF') OR
    (u.username = 'giamdoc.hn'   AND r.code = 'BRANCH_DIRECTOR');

INSERT IGNORE INTO sys_user_data_scopes (user_id, scope_type, scope_id) VALUES
    (1, 'COMPANY', 1),
    (2, 'COMPANY', 1),
    (3, 'BRANCH',  1),
    (4, 'BRANCH',  2),
    (5, 'BRANCH',  1);
