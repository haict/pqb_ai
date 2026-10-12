-- CHỈ CHO MÁY DEV (profile dev). Không nằm trong profile test (test phân quyền cần admin chỉ có quyền M01)
-- và không bao giờ cấu hình ở uat/prod (database.md mục 2).
-- Mục đích: tài khoản mẫu 'admin' có toàn quyền để thử nhanh mọi chức năng trên giao diện.
-- Chạy lại mỗi lần khởi động (placeholder thời gian đổi checksum) để admin nhận cả quyền của migration mới:
-- ${flyway:timestamp}

INSERT IGNORE INTO sys_roles (code, name, system_role, status, created_at, updated_at) VALUES
    ('DEV_FULL_ACCESS', 'Toàn quyền (chỉ dev)', FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r CROSS JOIN sys_permissions p
WHERE r.code = 'DEV_FULL_ACCESS';

-- Gán mọi vai trò để admin là người duyệt hợp lệ ở mọi bước luồng duyệt (bước duyệt theo vai trò).
INSERT IGNORE INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u CROSS JOIN sys_roles r
WHERE u.username = 'admin' AND r.status = 'ACTIVE';

INSERT IGNORE INTO sys_user_data_scopes (user_id, scope_type, scope_id)
SELECT u.id, 'COMPANY', u.company_id FROM sys_users u WHERE u.username = 'admin';
