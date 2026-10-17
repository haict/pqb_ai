-- Bản quyền phần mềm (D-59): license ký số Ed25519 do nhà cung cấp cấp, kiểm tra bằng khóa công khai nhúng trong ứng dụng.
-- Mỗi lần nạp thêm một dòng (lịch sử); license đang dùng là dòng mới nhất. Chỉ INSERT, không sửa / xóa qua ứng dụng.
CREATE TABLE sys_licenses (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    license_id    VARCHAR(100)  NOT NULL,
    key_id        VARCHAR(50)   NOT NULL,
    customer_name VARCHAR(300)  NULL,
    tax_codes     VARCHAR(1000) NOT NULL,
    valid_to      DATE          NOT NULL,
    grace_days    INT           NOT NULL,
    issued_at     DATETIME(3)   NOT NULL,
    license_text  TEXT          NOT NULL,
    installed_by  BIGINT        NULL,
    installed_at  DATETIME(3)   NOT NULL,
    CONSTRAINT pk_sys_licenses PRIMARY KEY (id),
    CONSTRAINT fk_sys_licenses_sys_users FOREIGN KEY (installed_by) REFERENCES sys_users (id),
    INDEX idx_sys_licenses_license_id (license_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Mốc thời điểm lớn nhất hệ thống từng thấy, kèm mã kiểm tra, để phát hiện lùi đồng hồ máy chủ. Một dòng (id = 1).
CREATE TABLE sys_license_clock (
    id          TINYINT     NOT NULL,
    max_seen_at DATETIME(3) NOT NULL,
    checksum    VARCHAR(64) NOT NULL,
    CONSTRAINT pk_sys_license_clock PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('license:manage', 'SYSTEM', 'Xem chi tiết và nạp bản quyền phần mềm (license)', 95);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code = 'license:manage'
WHERE r.code = 'SYSTEM_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
