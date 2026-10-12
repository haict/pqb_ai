-- Giai đoạn 4B - Sổ công nợ (M07): khoản công nợ theo chứng từ nguồn, sổ chi tiết công nợ, chứng từ công nợ đầu kỳ
-- (BR-AR-01, 02, 05, BR-AP-01, 02, ADR-12). Phải thu và phải trả dùng chung một cấu trúc, phân biệt bằng debt_type.

-- Khoản công nợ: một dòng cho mỗi hóa đơn / dòng công nợ đầu kỳ (nguồn tổng quát source_type/source_id, ADR-12).
-- Còn lại = gốc - đã thanh toán; khóa FOR UPDATE theo id tăng dần khi phân bổ (Giai đoạn 4C).
CREATE TABLE debt_items (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    branch_id        BIGINT        NOT NULL,
    debt_type        VARCHAR(20)   NOT NULL,
    partner_id       BIGINT        NOT NULL,
    source_type      VARCHAR(30)   NOT NULL,
    source_id        BIGINT        NOT NULL,
    source_line_id   BIGINT        NULL,
    source_line_key  BIGINT        AS (COALESCE(source_line_id, 0)) STORED,
    source_no        VARCHAR(50)   NOT NULL,
    invoice_no       VARCHAR(50)   NULL,
    document_date    DATE          NOT NULL,
    due_date         DATE          NOT NULL,
    original_amount  DECIMAL(19,4) NOT NULL,
    settled_amount   DECIMAL(19,4) NOT NULL DEFAULT 0,
    remaining_amount DECIMAL(19,4) AS (original_amount - settled_amount) STORED,
    sales_user_id    BIGINT        NULL,
    description      VARCHAR(500)  NULL,
    status           VARCHAR(20)   NOT NULL,
    created_by       BIGINT        NULL,
    created_at       DATETIME(3)   NOT NULL,
    updated_by       BIGINT        NULL,
    updated_at       DATETIME(3)   NOT NULL,
    version          INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_debt_items PRIMARY KEY (id),
    CONSTRAINT uk_debt_items_source UNIQUE (company_id, debt_type, source_type, source_id, source_line_key),
    CONSTRAINT fk_debt_items_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_debt_items_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_debt_items_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_debt_items_sales_users FOREIGN KEY (sales_user_id) REFERENCES sys_users (id),
    CONSTRAINT ck_debt_items_type CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT ck_debt_items_status CHECK (status IN ('OPEN', 'PARTIAL', 'SETTLED', 'REVERSED')),
    CONSTRAINT ck_debt_items_amounts CHECK (original_amount > 0 AND settled_amount >= 0
        AND settled_amount <= original_amount),
    CONSTRAINT ck_debt_items_dates CHECK (due_date >= document_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_debt_items_partner_due ON debt_items (partner_id, due_date);
CREATE INDEX idx_debt_items_status_due ON debt_items (company_id, debt_type, status, due_date);

-- Sổ chi tiết công nợ: chỉ INSERT. Phải thu tăng ở vế Nợ, phải trả tăng ở vế Có; số dư tính từ sổ này.
CREATE TABLE debt_ledger_entries (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    debt_type         VARCHAR(20)   NOT NULL,
    partner_id        BIGINT        NOT NULL,
    debt_item_id      BIGINT        NULL,
    posting_date      DATE          NOT NULL,
    entry_type        VARCHAR(20)   NOT NULL,
    debit_amount      DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit_amount     DECIMAL(19,4) NOT NULL DEFAULT 0,
    reference_type    VARCHAR(30)   NOT NULL,
    reference_id      BIGINT        NOT NULL,
    reference_line_id BIGINT        NULL,
    reference_no      VARCHAR(50)   NOT NULL,
    description       VARCHAR(500)  NULL,
    reversal_of_id    BIGINT        NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    CONSTRAINT pk_debt_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_debt_ledger_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_debt_ledger_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_debt_ledger_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_debt_ledger_items FOREIGN KEY (debt_item_id) REFERENCES debt_items (id),
    CONSTRAINT fk_debt_ledger_reversal FOREIGN KEY (reversal_of_id) REFERENCES debt_ledger_entries (id),
    CONSTRAINT ck_debt_ledger_type CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT ck_debt_ledger_amounts CHECK (debit_amount >= 0 AND credit_amount >= 0
        AND ((debit_amount > 0 AND credit_amount = 0) OR (credit_amount > 0 AND debit_amount = 0)))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_debt_ledger_partner_date ON debt_ledger_entries (company_id, debt_type, partner_id, posting_date);
CREATE INDEX idx_debt_ledger_reference ON debt_ledger_entries (reference_type, reference_id);
CREATE INDEX idx_debt_ledger_item ON debt_ledger_entries (debt_item_id);

-- Chứng từ công nợ đầu kỳ (CNDK): nhập số còn nợ theo từng hóa đơn cũ khi chuyển từ MISA (BR-MIG-01).
CREATE TABLE debt_opening_documents (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    branch_id     BIGINT        NOT NULL,
    document_no   VARCHAR(50)   NOT NULL,
    debt_type     VARCHAR(20)   NOT NULL,
    document_date DATE          NOT NULL,
    posting_date  DATE          NOT NULL,
    description   VARCHAR(500)  NULL,
    total_amount  DECIMAL(19,4) NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL,
    submitted_by  BIGINT        NULL,
    submitted_at  DATETIME(3)   NULL,
    approved_by   BIGINT        NULL,
    approved_at   DATETIME(3)   NULL,
    posted_by     BIGINT        NULL,
    posted_at     DATETIME(3)   NULL,
    reversed_by   BIGINT        NULL,
    reversed_at   DATETIME(3)   NULL,
    reverse_date  DATE          NULL,
    reason        VARCHAR(500)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME(3)   NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME(3)   NOT NULL,
    version       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_debt_opening_documents PRIMARY KEY (id),
    CONSTRAINT uk_debt_opening_documents_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_debt_opening_documents_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_debt_opening_documents_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT ck_debt_opening_documents_type CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT ck_debt_opening_documents_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED',
        'POSTED', 'CANCELLED', 'REVERSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_debt_opening_documents_status ON debt_opening_documents (company_id, branch_id, status, document_date);

CREATE TABLE debt_opening_lines (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    document_id   BIGINT        NOT NULL,
    line_no       INT           NOT NULL,
    partner_id    BIGINT        NOT NULL,
    invoice_no    VARCHAR(50)   NOT NULL,
    invoice_date  DATE          NOT NULL,
    due_date      DATE          NOT NULL,
    amount        DECIMAL(19,4) NOT NULL,
    sales_user_id BIGINT        NULL,
    note          VARCHAR(500)  NULL,
    CONSTRAINT pk_debt_opening_lines PRIMARY KEY (id),
    CONSTRAINT uk_debt_opening_lines_no UNIQUE (document_id, line_no),
    CONSTRAINT fk_debt_opening_lines_documents FOREIGN KEY (document_id) REFERENCES debt_opening_documents (id),
    CONSTRAINT fk_debt_opening_lines_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_debt_opening_lines_sales_users FOREIGN KEY (sales_user_id) REFERENCES sys_users (id),
    CONSTRAINT ck_debt_opening_lines_amount CHECK (amount > 0),
    CONSTRAINT ck_debt_opening_lines_dates CHECK (due_date >= invoice_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('CNDK', 'FINANCE', 'Chứng từ công nợ đầu kỳ', 'REQUIRED', FALSE, 700);

-- Mốc tuổi nợ (BR-AR-05). Giá trị mặc định 30 / 60 / 90 ngày là [Giả định]; doanh nghiệp chỉnh ở màn hình tham số.
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('finance.aging-bucket-1-days', 'FINANCE', 'Tuổi nợ - mốc 1 (ngày quá hạn)',
     'BR-AR-05. Nhóm quá hạn từ 1 đến mốc 1 ngày.', 'INTEGER', '30', 1, 3650, NULL, FALSE, 100),
    ('finance.aging-bucket-2-days', 'FINANCE', 'Tuổi nợ - mốc 2 (ngày quá hạn)',
     'BR-AR-05. Nhóm quá hạn trên mốc 1 đến mốc 2 ngày; phải lớn hơn mốc 1.', 'INTEGER', '60', 1, 3650, NULL,
     FALSE, 110),
    ('finance.aging-bucket-3-days', 'FINANCE', 'Tuổi nợ - mốc 3 (ngày quá hạn)',
     'BR-AR-05. Nhóm quá hạn trên mốc 2 đến mốc 3 ngày; trên mốc 3 là nhóm cuối.', 'INTEGER', '90', 1, 3650, NULL,
     FALSE, 120);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('debt:view',             'FINANCE', 'Xem công nợ phải thu, phải trả, sổ chi tiết, tuổi nợ', 700),
    ('debt-opening:view',     'FINANCE', 'Xem chứng từ công nợ đầu kỳ',                        710),
    ('debt-opening:create',   'FINANCE', 'Lập, sửa, gửi duyệt, hủy công nợ đầu kỳ',            711),
    ('debt-opening:approve',  'FINANCE', 'Duyệt, đảo công nợ đầu kỳ',                          712),
    ('debt-opening:post',     'FINANCE', 'Ghi sổ công nợ đầu kỳ',                              713);

-- Gán quyền mặc định (docs/07 mục 2.5). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.module = 'FINANCE' AND (
    r.code = 'CHIEF_ACCOUNTANT' OR
    (r.code = 'AR_ACCOUNTANT' AND p.code IN ('debt:view', 'debt-opening:view', 'debt-opening:create',
        'debt-opening:post')) OR
    (r.code IN ('CASHIER', 'TAX_ACCOUNTANT') AND p.code = 'debt:view') OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('debt:view', 'debt-opening:view')));
