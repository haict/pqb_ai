-- Giai đoạn 1C: kỳ kế toán/kỳ kho và khóa kỳ (BR-SYS-30..32), tham số hệ thống (BR-SYS-13),
-- cấu hình số chứng từ (BR-SYS-10). Thiết kế: docs/03 mục 4.18-4.21.

-- Kỳ theo tháng của công ty; năm tài chính trùng năm dương lịch.
CREATE TABLE fiscal_periods (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    company_id  BIGINT      NOT NULL,
    period_year INT         NOT NULL,
    period_month INT        NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    created_by  BIGINT      NULL,
    created_at  DATETIME(3) NOT NULL,
    updated_by  BIGINT      NULL,
    updated_at  DATETIME(3) NOT NULL,
    version     INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_fiscal_periods PRIMARY KEY (id),
    CONSTRAINT uk_fiscal_periods_company_month UNIQUE (company_id, period_year, period_month),
    CONSTRAINT fk_fiscal_periods_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_fiscal_periods_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT ck_fiscal_periods_dates CHECK (start_date <= end_date),
    INDEX idx_fiscal_periods_company_dates (company_id, start_date, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Trạng thái khóa theo kỳ × chi nhánh × phân hệ. Không có dòng hoặc locked = FALSE: kỳ đang mở.
-- Dòng được tạo trước (transaction riêng) khi khóa/mở để mọi khóa dòng rơi vào dòng đã tồn tại, không khóa khoảng trống.
-- Lịch sử khóa/mở (người, thời điểm, lý do) ghi ở sys_audit_logs.
CREATE TABLE period_locks (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    fiscal_period_id  BIGINT       NOT NULL,
    branch_id         BIGINT       NOT NULL,
    module            VARCHAR(20)  NOT NULL,
    locked            BOOLEAN      NOT NULL,
    changed_by        BIGINT       NULL,
    changed_at        DATETIME(3)  NULL,
    reason            VARCHAR(500) NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME(3)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_period_locks PRIMARY KEY (id),
    CONSTRAINT uk_period_locks_scope UNIQUE (fiscal_period_id, branch_id, module),
    CONSTRAINT fk_period_locks_fiscal_periods FOREIGN KEY (fiscal_period_id) REFERENCES fiscal_periods (id),
    CONSTRAINT fk_period_locks_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_period_locks_users FOREIGN KEY (changed_by) REFERENCES sys_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Định nghĩa tham số (kiểu, giới hạn, mặc định). Dữ liệu tham chiếu: thêm bằng migration của module dùng tham số.
CREATE TABLE sys_parameter_definitions (
    param_key          VARCHAR(80)  NOT NULL,
    module             VARCHAR(30)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    description        VARCHAR(500) NULL,
    value_type         VARCHAR(20)  NOT NULL,
    default_value      VARCHAR(500) NOT NULL,
    min_value          DECIMAL(19,4) NULL,
    max_value          DECIMAL(19,4) NULL,
    allowed_values     VARCHAR(500) NULL,
    lock_after_posting BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order         INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_sys_parameter_definitions PRIMARY KEY (param_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Giá trị tham số theo công ty. Không có dòng = dùng giá trị mặc định.
CREATE TABLE sys_parameters (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NOT NULL,
    param_key   VARCHAR(80)  NOT NULL,
    param_value VARCHAR(500) NOT NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME(3)  NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_sys_parameters PRIMARY KEY (id),
    CONSTRAINT uk_sys_parameters_company_key UNIQUE (company_id, param_key),
    CONSTRAINT fk_sys_parameters_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sys_parameters_definitions FOREIGN KEY (param_key) REFERENCES sys_parameter_definitions (param_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Cấu hình số chứng từ theo công ty × loại chứng từ, có ngày hiệu lực (thay đổi chỉ áp dụng từ chu kỳ sau).
-- Loại chứng từ chưa có cấu hình dùng định dạng D-03: <LOẠI>-<CHI NHÁNH>-<YYYY>-<6 số>.
CREATE TABLE document_number_formats (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    company_id      BIGINT      NOT NULL,
    document_type   VARCHAR(30) NOT NULL,
    prefix          VARCHAR(10) NOT NULL,
    sequence_length INT         NOT NULL,
    reset_cycle     VARCHAR(10) NOT NULL,
    effective_from  DATE        NOT NULL,
    created_by      BIGINT      NULL,
    created_at      DATETIME(3) NOT NULL,
    updated_by      BIGINT      NULL,
    updated_at      DATETIME(3) NOT NULL,
    version         INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_document_number_formats PRIMARY KEY (id),
    CONSTRAINT uk_document_number_formats_effective UNIQUE (company_id, document_type, effective_from),
    CONSTRAINT fk_document_number_formats_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_document_number_formats_length CHECK (sequence_length BETWEEN 3 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tham số đã chốt (D-05). Chưa module nào đọc; module ghi sổ dùng khi triển khai.
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('decimal.quantity-scale', 'GENERAL', 'Số chữ số thập phân của số lượng',
     'D-05. Tối đa 3 theo kiểu cột DECIMAL(19,3).', 'INTEGER', '3', 0, 3, NULL, TRUE, 10),
    ('decimal.unit-price-scale', 'GENERAL', 'Số chữ số thập phân của đơn giá',
     'D-05. Tối đa 4 theo kiểu cột DECIMAL(19,4).', 'INTEGER', '4', 0, 4, NULL, TRUE, 20),
    ('decimal.amount-scale', 'GENERAL', 'Số chữ số thập phân của thành tiền',
     'D-05: tiền làm tròn đến đồng.', 'INTEGER', '0', 0, 4, NULL, TRUE, 30);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('period:view',              'SYSTEM', 'Xem kỳ và trạng thái khóa kỳ',          160),
    ('period:manage',            'SYSTEM', 'Tạo kỳ, khóa kỳ',                       161),
    ('period:unlock',            'SYSTEM', 'Mở lại kỳ đã khóa',                     162),
    ('parameter:view',           'SYSTEM', 'Xem tham số hệ thống',                  170),
    ('parameter:update',         'SYSTEM', 'Sửa tham số hệ thống',                  171),
    ('document-number:view',     'SYSTEM', 'Xem cấu hình số chứng từ',              180),
    ('document-number:update',   'SYSTEM', 'Sửa cấu hình số chứng từ',              181);

-- Gán quyền mặc định (docs/07 mục 3). Mở lại kỳ chỉ Kế toán trưởng, Giám đốc (BR-SYS-32).
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON
    (r.code = 'SYSTEM_ADMIN'     AND p.code IN ('period:view', 'parameter:view', 'parameter:update',
                                                'document-number:view', 'document-number:update')) OR
    (r.code = 'DIRECTOR'         AND p.code IN ('period:view', 'period:unlock', 'parameter:view',
                                                'document-number:view')) OR
    (r.code = 'CHIEF_ACCOUNTANT' AND p.code IN ('period:view', 'period:manage', 'period:unlock',
                                                'parameter:view', 'parameter:update',
                                                'document-number:view', 'document-number:update')) OR
    (r.code = 'TAX_ACCOUNTANT'   AND p.code IN ('period:view', 'period:manage', 'parameter:view',
                                                'document-number:view')) OR
    (r.code IN ('AR_ACCOUNTANT', 'CASHIER', 'WAREHOUSE_MANAGER', 'BRANCH_DIRECTOR')
                                 AND p.code = 'period:view');
