-- Giai đoạn 4A - Kế toán lõi (M08): hệ thống tài khoản, quy tắc hạch toán, bút toán (BR-ACC-01..03, ADR-11).
-- Không nạp hệ thống tài khoản hay quy tắc mặc định ở đây: nội dung theo chế độ kế toán do kế toán trưởng xác nhận
-- (BR mục 13), production nạp bằng nhập Excel; dữ liệu mẫu chỉ ở db/dev-data.

CREATE TABLE chart_of_accounts (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_id       BIGINT       NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    parent_id        BIGINT       NULL,
    account_level    INT          NOT NULL,
    balance_side     VARCHAR(10)  NOT NULL,
    postable         BOOLEAN      NOT NULL DEFAULT TRUE,
    partner_required BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL,
    created_by       BIGINT       NULL,
    created_at       DATETIME(3)  NOT NULL,
    updated_by       BIGINT       NULL,
    updated_at       DATETIME(3)  NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_chart_of_accounts PRIMARY KEY (id),
    CONSTRAINT uk_chart_of_accounts_code UNIQUE (company_id, code),
    CONSTRAINT fk_chart_of_accounts_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_chart_of_accounts_parent FOREIGN KEY (parent_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT ck_chart_of_accounts_side CHECK (balance_side IN ('DEBIT', 'CREDIT', 'BOTH')),
    CONSTRAINT ck_chart_of_accounts_level CHECK (account_level BETWEEN 1 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_chart_of_accounts_parent ON chart_of_accounts (parent_id);

-- Quy tắc hạch toán: (loại chứng từ, mã nghiệp vụ, [loại hàng], [vai trò đối tượng]) -> TK Nợ / TK Có.
-- Vế để trống: tài khoản do chứng từ truyền (ví dụ tài khoản tiền của quỹ). Cột *_key phục vụ unique khi điều
-- kiện để trống (NULL không so trùng được trong unique index).
CREATE TABLE posting_rules (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    company_id        BIGINT       NOT NULL,
    document_type     VARCHAR(30)  NOT NULL,
    transaction_code  VARCHAR(50)  NOT NULL,
    product_type      VARCHAR(30)  NULL,
    partner_role      VARCHAR(30)  NULL,
    product_type_key  VARCHAR(30)  AS (COALESCE(product_type, '')) STORED,
    partner_role_key  VARCHAR(30)  AS (COALESCE(partner_role, '')) STORED,
    debit_account_id  BIGINT       NULL,
    credit_account_id BIGINT       NULL,
    valid_from        DATE         NOT NULL,
    valid_to          DATE         NULL,
    description       VARCHAR(500) NULL,
    status            VARCHAR(20)  NOT NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME(3)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_posting_rules PRIMARY KEY (id),
    CONSTRAINT uk_posting_rules_condition UNIQUE (company_id, document_type, transaction_code, product_type_key,
        partner_role_key, valid_from),
    CONSTRAINT fk_posting_rules_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_posting_rules_document_types FOREIGN KEY (document_type) REFERENCES sys_document_types (code),
    CONSTRAINT fk_posting_rules_debit FOREIGN KEY (debit_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_posting_rules_credit FOREIGN KEY (credit_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT ck_posting_rules_accounts CHECK (debit_account_id IS NOT NULL OR credit_account_id IS NOT NULL),
    CONSTRAINT ck_posting_rules_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Bút toán: chỉ INSERT; đảo đổi trạng thái bút toán gốc sang REVERSED và sinh bút toán ngược (BR-ACC-03).
-- Mỗi chứng từ nguồn có tối đa một bút toán gốc (reversal_key = 0) và một bút toán đảo cho mỗi bút toán gốc.
CREATE TABLE journal_entries (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id     BIGINT        NOT NULL,
    branch_id      BIGINT        NOT NULL,
    entry_no       VARCHAR(50)   NOT NULL,
    posting_date   DATE          NOT NULL,
    entry_kind     VARCHAR(20)   NOT NULL,
    source_type    VARCHAR(30)   NOT NULL,
    source_id      BIGINT        NOT NULL,
    source_no      VARCHAR(50)   NOT NULL,
    description    VARCHAR(500)  NULL,
    total_amount   DECIMAL(19,4) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    reversal_of_id BIGINT        NULL,
    reversal_key   BIGINT        AS (COALESCE(reversal_of_id, 0)) STORED,
    created_by     BIGINT        NULL,
    created_at     DATETIME(3)   NOT NULL,
    updated_at     DATETIME(3)   NOT NULL,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_journal_entries PRIMARY KEY (id),
    CONSTRAINT uk_journal_entries_no UNIQUE (company_id, entry_no),
    CONSTRAINT uk_journal_entries_source UNIQUE (company_id, source_type, source_id, reversal_key),
    CONSTRAINT fk_journal_entries_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_journal_entries_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_journal_entries_reversal FOREIGN KEY (reversal_of_id) REFERENCES journal_entries (id),
    CONSTRAINT ck_journal_entries_kind CHECK (entry_kind IN ('AUTO', 'MANUAL', 'COST', 'CLOSING')),
    CONSTRAINT ck_journal_entries_status CHECK (status IN ('POSTED', 'REVERSED')),
    CONSTRAINT ck_journal_entries_total CHECK (total_amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_journal_entries_date ON journal_entries (company_id, posting_date);
CREATE INDEX idx_journal_entries_source_no ON journal_entries (company_id, source_no);

CREATE TABLE journal_entry_lines (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    journal_entry_id BIGINT        NOT NULL,
    line_no          INT           NOT NULL,
    account_id       BIGINT        NOT NULL,
    debit_amount     DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit_amount    DECIMAL(19,4) NOT NULL DEFAULT 0,
    transaction_code VARCHAR(50)   NOT NULL,
    posting_rule_id  BIGINT        NULL,
    partner_id       BIGINT        NULL,
    product_id       BIGINT        NULL,
    warehouse_id     BIGINT        NULL,
    department_id    BIGINT        NULL,
    employee_id      BIGINT        NULL,
    source_line_id   BIGINT        NULL,
    description      VARCHAR(500)  NULL,
    CONSTRAINT pk_journal_entry_lines PRIMARY KEY (id),
    CONSTRAINT uk_journal_entry_lines_no UNIQUE (journal_entry_id, line_no),
    CONSTRAINT fk_journal_entry_lines_entries FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id),
    CONSTRAINT fk_journal_entry_lines_accounts FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_journal_entry_lines_rules FOREIGN KEY (posting_rule_id) REFERENCES posting_rules (id),
    CONSTRAINT fk_journal_entry_lines_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_journal_entry_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_journal_entry_lines_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_journal_entry_lines_departments FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_journal_entry_lines_employees FOREIGN KEY (employee_id) REFERENCES sys_users (id),
    CONSTRAINT ck_journal_entry_lines_amounts CHECK (debit_amount >= 0 AND credit_amount >= 0
        AND ((debit_amount > 0 AND credit_amount = 0) OR (credit_amount > 0 AND debit_amount = 0)))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_journal_entry_lines_account ON journal_entry_lines (account_id, journal_entry_id);
CREATE INDEX idx_journal_entry_lines_partner ON journal_entry_lines (partner_id);

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('BT', 'ACCOUNTING', 'Bút toán', 'NONE', FALSE, 800);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('account:view',        'ACCOUNTING', 'Xem hệ thống tài khoản',                800),
    ('account:manage',      'ACCOUNTING', 'Thêm, sửa, ngừng, nhập tài khoản',      801),
    ('posting-rule:view',   'ACCOUNTING', 'Xem quy tắc hạch toán',                 810),
    ('posting-rule:manage', 'ACCOUNTING', 'Thêm, sửa, ngừng quy tắc hạch toán',    811),
    ('journal:view',        'ACCOUNTING', 'Xem bút toán',                          820);

-- Gán quyền mặc định (docs/07). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.module = 'ACCOUNTING' AND (
    r.code = 'CHIEF_ACCOUNTANT' OR
    (r.code = 'TAX_ACCOUNTANT' AND p.code LIKE '%:view') OR
    (r.code IN ('AR_ACCOUNTANT', 'CASHIER') AND p.code IN ('account:view', 'journal:view')) OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code LIKE '%:view'));
