-- Giai đoạn 1A: tổ chức, người dùng, phân quyền, phiên đăng nhập (docs/03 mục 2 M01, docs/07).

CREATE TABLE companies (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    tax_code    VARCHAR(20)  NULL,
    address     VARCHAR(500) NULL,
    phone       VARCHAR(30)  NULL,
    email       VARCHAR(150) NULL,
    status      VARCHAR(20)  NOT NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME(3)  NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT uk_companies_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE branches (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(500) NULL,
    phone       VARCHAR(30)  NULL,
    status      VARCHAR(20)  NOT NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME(3)  NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_branches PRIMARY KEY (id),
    CONSTRAINT uk_branches_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_branches_companies FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    branch_id   BIGINT       NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME(3)  NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT uk_departments_branch_code UNIQUE (branch_id, code),
    CONSTRAINT fk_departments_branches FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sys_users (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    company_id             BIGINT       NOT NULL,
    default_branch_id      BIGINT       NULL,
    username               VARCHAR(50)  NOT NULL,
    full_name              VARCHAR(150) NOT NULL,
    email                  VARCHAR(150) NULL,
    phone                  VARCHAR(30)  NULL,
    password_hash          VARCHAR(255) NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    own_data_only          BOOLEAN      NOT NULL DEFAULT FALSE,
    must_change_password   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count     INT          NOT NULL DEFAULT 0,
    locked_until           DATETIME(3)  NULL,
    credentials_changed_at DATETIME(3)  NOT NULL,
    last_login_at          DATETIME(3)  NULL,
    created_by             BIGINT       NULL,
    created_at             DATETIME(3)  NOT NULL,
    updated_by             BIGINT       NULL,
    updated_at             DATETIME(3)  NOT NULL,
    version                INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_sys_users PRIMARY KEY (id),
    CONSTRAINT uk_sys_users_username UNIQUE (username),
    CONSTRAINT fk_sys_users_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sys_users_branches FOREIGN KEY (default_branch_id) REFERENCES branches (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sys_roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(20)  NOT NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME(3)  NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_sys_roles PRIMARY KEY (id),
    CONSTRAINT uk_sys_roles_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Danh mục quyền do hệ thống định nghĩa (dữ liệu tham chiếu, không sửa qua ứng dụng).
CREATE TABLE sys_permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(80)  NOT NULL,
    module      VARCHAR(30)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INT          NOT NULL,
    CONSTRAINT pk_sys_permissions PRIMARY KEY (id),
    CONSTRAINT uk_sys_permissions_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_sys_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sys_user_roles_users FOREIGN KEY (user_id) REFERENCES sys_users (id),
    CONSTRAINT fk_sys_user_roles_roles FOREIGN KEY (role_id) REFERENCES sys_roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_sys_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_sys_role_permissions_roles FOREIGN KEY (role_id) REFERENCES sys_roles (id),
    CONSTRAINT fk_sys_role_permissions_permissions FOREIGN KEY (permission_id) REFERENCES sys_permissions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Phạm vi dữ liệu (BR-SYS-04): COMPANY = mọi chi nhánh của công ty; BRANCH = một chi nhánh;
-- WAREHOUSE = một kho (FK tới warehouses được thêm khi tạo bảng kho ở Giai đoạn 2).
CREATE TABLE sys_user_data_scopes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_id   BIGINT      NOT NULL,
    CONSTRAINT pk_sys_user_data_scopes PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_data_scopes UNIQUE (user_id, scope_type, scope_id),
    CONSTRAINT fk_sys_user_data_scopes_users FOREIGN KEY (user_id) REFERENCES sys_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Refresh token chỉ lưu dạng băm SHA-256. Mỗi phiên đăng nhập là một family; xoay vòng mỗi lần làm mới.
CREATE TABLE sys_refresh_tokens (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    family_id         CHAR(36)    NOT NULL,
    token_hash        CHAR(64)    NOT NULL,
    issued_at         DATETIME(3) NOT NULL,
    session_expires_at DATETIME(3) NOT NULL,
    idle_expires_at   DATETIME(3) NOT NULL,
    used_at           DATETIME(3) NULL,
    revoked_at        DATETIME(3) NULL,
    revoke_reason     VARCHAR(30) NULL,
    created_ip        VARCHAR(45) NULL,
    user_agent        VARCHAR(255) NULL,
    CONSTRAINT pk_sys_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_sys_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_sys_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES sys_users (id),
    INDEX idx_sys_refresh_tokens_family (family_id),
    INDEX idx_sys_refresh_tokens_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Chỉ INSERT qua ứng dụng (docs/03 mục 1.4).
CREATE TABLE sys_login_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(3)  NOT NULL,
    event       VARCHAR(30)  NOT NULL,
    username    VARCHAR(50)  NULL,
    user_id     BIGINT       NULL,
    ip_address  VARCHAR(45)  NULL,
    user_agent  VARCHAR(255) NULL,
    request_id  VARCHAR(64)  NULL,
    CONSTRAINT pk_sys_login_logs PRIMARY KEY (id),
    CONSTRAINT fk_sys_login_logs_users FOREIGN KEY (user_id) REFERENCES sys_users (id),
    INDEX idx_sys_login_logs_occurred_at (occurred_at),
    INDEX idx_sys_login_logs_user (user_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- FK cho các bảng của Giai đoạn 0.
ALTER TABLE document_sequences
    ADD CONSTRAINT fk_document_sequences_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    ADD CONSTRAINT fk_document_sequences_branches FOREIGN KEY (branch_id) REFERENCES branches (id);

ALTER TABLE sys_idempotency_keys
    ADD CONSTRAINT fk_sys_idempotency_keys_users FOREIGN KEY (user_id) REFERENCES sys_users (id);

ALTER TABLE sys_audit_logs
    ADD CONSTRAINT fk_sys_audit_logs_users FOREIGN KEY (actor_user_id) REFERENCES sys_users (id),
    ADD CONSTRAINT fk_sys_audit_logs_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    ADD CONSTRAINT fk_sys_audit_logs_branches FOREIGN KEY (branch_id) REFERENCES branches (id);

-- Danh mục quyền M01 (docs/07-authorization.md). Module sau thêm quyền bằng migration của mình.
INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('company:view',           'SYSTEM', 'Xem công ty',                        100),
    ('company:create',         'SYSTEM', 'Tạo công ty',                        101),
    ('company:update',         'SYSTEM', 'Sửa, ngừng hoạt động công ty',       102),
    ('branch:view',            'SYSTEM', 'Xem chi nhánh',                      110),
    ('branch:create',          'SYSTEM', 'Tạo chi nhánh',                      111),
    ('branch:update',          'SYSTEM', 'Sửa, ngừng hoạt động chi nhánh',     112),
    ('department:view',        'SYSTEM', 'Xem phòng ban',                      120),
    ('department:create',      'SYSTEM', 'Tạo phòng ban',                      121),
    ('department:update',      'SYSTEM', 'Sửa, ngừng hoạt động phòng ban',     122),
    ('user:view',              'SYSTEM', 'Xem người dùng',                     130),
    ('user:create',            'SYSTEM', 'Tạo người dùng',                     131),
    ('user:update',            'SYSTEM', 'Sửa, khóa, mở khóa người dùng',      132),
    ('user:reset-password',    'SYSTEM', 'Đặt lại mật khẩu người dùng',        133),
    ('user:assign-roles',      'SYSTEM', 'Gán vai trò cho người dùng',         134),
    ('user:assign-scopes',     'SYSTEM', 'Gán phạm vi dữ liệu cho người dùng', 135),
    ('role:view',              'SYSTEM', 'Xem vai trò',                        140),
    ('role:create',            'SYSTEM', 'Tạo vai trò',                        141),
    ('role:update',            'SYSTEM', 'Sửa, ngừng hoạt động vai trò',       142),
    ('role:assign-permissions','SYSTEM', 'Gán quyền cho vai trò',              143),
    ('login-log:view',         'SYSTEM', 'Xem nhật ký đăng nhập',              150);

-- Vai trò mặc định theo BR mục 4. Quyền nghiệp vụ được gán khi làm module tương ứng.
INSERT INTO sys_roles (code, name, system_role, status, created_at, updated_at) VALUES
    ('SYSTEM_ADMIN',      'Quản trị hệ thống',               TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('DIRECTOR',          'Giám đốc',                        TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('BRANCH_DIRECTOR',   'Giám đốc chi nhánh',              TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('SALES_MANAGER',     'Trưởng phòng kinh doanh',         TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('SALES_STAFF',       'Nhân viên kinh doanh',            TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('TENDER_STAFF',      'Nhân viên đấu thầu / pháp chế',   TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('PURCHASING_STAFF',  'Nhân viên mua hàng',              TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('WAREHOUSE_MANAGER', 'Quản lý kho',                     TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('WAREHOUSE_STAFF',   'Thủ kho / Nhân viên kho',         TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('QA',                'Phụ trách chất lượng',            TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('TECHNICIAN',        'Kỹ thuật viên',                   TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('DELIVERY_STAFF',    'Nhân viên giao hàng',             TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('AR_ACCOUNTANT',     'Kế toán công nợ',                 TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('CASHIER',           'Thủ quỹ / Kế toán thanh toán',    TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('CHIEF_ACCOUNTANT',  'Kế toán trưởng',                  TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('TAX_ACCOUNTANT',    'Kế toán thuế / tổng hợp',         TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('VIEWER',            'Người dùng chỉ xem',              TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- Quản trị hệ thống: toàn bộ quyền M01.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r CROSS JOIN sys_permissions p
WHERE r.code = 'SYSTEM_ADMIN' AND p.module = 'SYSTEM';

-- Mọi vai trò nghiệp vụ được xem cơ cấu tổ chức trong phạm vi dữ liệu của mình.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r CROSS JOIN sys_permissions p
WHERE r.code <> 'SYSTEM_ADMIN' AND p.code IN ('company:view', 'branch:view', 'department:view');

-- Giám đốc: xem người dùng, vai trò, nhật ký đăng nhập.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r CROSS JOIN sys_permissions p
WHERE r.code = 'DIRECTOR' AND p.code IN ('user:view', 'role:view', 'login-log:view');
