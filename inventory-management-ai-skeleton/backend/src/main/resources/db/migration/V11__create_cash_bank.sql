-- Giai đoạn 4C - Quỹ, ngân hàng (M07): quỹ / tài khoản ngân hàng, chứng từ thu chi (phiếu thu, phiếu chi, giấy báo
-- có, ủy nhiệm chi, chuyển tiền nội bộ), phân bổ thanh toán vào khoản công nợ, sổ quỹ / sổ tiền gửi
-- (BR-CSH-01..03, BR-AR-03, BR-AP-02, ADR-13).

-- Quỹ tiền mặt, tài khoản ngân hàng. balance là bản đọc nhanh của sổ quỹ, chỉ đổi khi ghi sổ (khóa FOR UPDATE);
-- quỹ tiền mặt không âm (BR-CSH-03).
CREATE TABLE cash_accounts (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    branch_id       BIGINT        NOT NULL,
    code            VARCHAR(30)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    account_type    VARCHAR(10)   NOT NULL,
    gl_account_id   BIGINT        NOT NULL,
    bank_name       VARCHAR(200)  NULL,
    bank_account_no VARCHAR(50)   NULL,
    currency_code   VARCHAR(3)    NOT NULL DEFAULT 'VND',
    balance         DECIMAL(19,4) NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL,
    created_by      BIGINT        NULL,
    created_at      DATETIME(3)   NOT NULL,
    updated_by      BIGINT        NULL,
    updated_at      DATETIME(3)   NOT NULL,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_cash_accounts PRIMARY KEY (id),
    CONSTRAINT uk_cash_accounts_code UNIQUE (company_id, code),
    CONSTRAINT fk_cash_accounts_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_cash_accounts_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_cash_accounts_gl FOREIGN KEY (gl_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT ck_cash_accounts_type CHECK (account_type IN ('CASH', 'BANK')),
    CONSTRAINT ck_cash_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_cash_accounts_bank CHECK (account_type = 'CASH' OR bank_account_no IS NOT NULL),
    CONSTRAINT ck_cash_accounts_balance CHECK (account_type <> 'CASH' OR balance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_cash_accounts_branch ON cash_accounts (branch_id, status);

-- Chứng từ thu chi (ADR-13: một bảng cho PT, PC, BC, UNC, CTN, phân biệt bằng document_type + purpose).
-- CUSTOMER_RECEIPT / SUPPLIER_PAYMENT giảm công nợ: dòng là yêu cầu phân bổ (debt_item_id) khi allocation_mode =
-- MANUAL, hoặc phân bổ hóa đơn cũ nhất trước lúc ghi sổ khi AUTO; phần chưa phân bổ là trả trước.
-- OTHER_RECEIPT / OTHER_PAYMENT: dòng là tài khoản đối ứng (account_id). INTERNAL_TRANSFER: không có dòng.
CREATE TABLE cash_documents (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    company_id             BIGINT        NOT NULL,
    branch_id              BIGINT        NOT NULL,
    document_no            VARCHAR(50)   NOT NULL,
    document_type          VARCHAR(10)   NOT NULL,
    purpose                VARCHAR(30)   NOT NULL,
    document_date          DATE          NOT NULL,
    posting_date           DATE          NOT NULL,
    cash_account_id        BIGINT        NOT NULL,
    target_cash_account_id BIGINT        NULL,
    partner_id             BIGINT        NULL,
    counterparty_name      VARCHAR(255)  NULL,
    allocation_mode        VARCHAR(10)   NULL,
    description            VARCHAR(500)  NULL,
    total_amount           DECIMAL(19,4) NOT NULL,
    allocated_amount       DECIMAL(19,4) NOT NULL DEFAULT 0,
    prepayment_amount      DECIMAL(19,4) NOT NULL DEFAULT 0,
    status                 VARCHAR(20)   NOT NULL,
    submitted_by           BIGINT        NULL,
    submitted_at           DATETIME(3)   NULL,
    approved_by            BIGINT        NULL,
    approved_at            DATETIME(3)   NULL,
    posted_by              BIGINT        NULL,
    posted_at              DATETIME(3)   NULL,
    reversed_by            BIGINT        NULL,
    reversed_at            DATETIME(3)   NULL,
    reverse_date           DATE          NULL,
    reason                 VARCHAR(500)  NULL,
    created_by             BIGINT        NULL,
    created_at             DATETIME(3)   NOT NULL,
    updated_by             BIGINT        NULL,
    updated_at             DATETIME(3)   NOT NULL,
    version                INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_cash_documents PRIMARY KEY (id),
    CONSTRAINT uk_cash_documents_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_cash_documents_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_cash_documents_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_cash_documents_accounts FOREIGN KEY (cash_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT fk_cash_documents_target FOREIGN KEY (target_cash_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT fk_cash_documents_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_cash_documents_types FOREIGN KEY (document_type) REFERENCES sys_document_types (code),
    CONSTRAINT ck_cash_documents_purpose CHECK (purpose IN ('CUSTOMER_RECEIPT', 'SUPPLIER_PAYMENT', 'OTHER_RECEIPT',
        'OTHER_PAYMENT', 'INTERNAL_TRANSFER')),
    CONSTRAINT ck_cash_documents_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_cash_documents_mode CHECK (allocation_mode IS NULL OR allocation_mode IN ('AUTO', 'MANUAL')),
    CONSTRAINT ck_cash_documents_amounts CHECK (total_amount > 0 AND allocated_amount >= 0 AND prepayment_amount >= 0
        AND allocated_amount + prepayment_amount <= total_amount),
    CONSTRAINT ck_cash_documents_transfer CHECK ((purpose = 'INTERNAL_TRANSFER') = (target_cash_account_id IS NOT NULL)),
    CONSTRAINT ck_cash_documents_debt CHECK (purpose NOT IN ('CUSTOMER_RECEIPT', 'SUPPLIER_PAYMENT')
        OR (partner_id IS NOT NULL AND allocation_mode IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_cash_documents_status ON cash_documents (company_id, branch_id, status, document_date);
CREATE INDEX idx_cash_documents_partner ON cash_documents (partner_id, document_date);

CREATE TABLE cash_document_lines (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    document_id  BIGINT        NOT NULL,
    line_no      INT           NOT NULL,
    debt_item_id BIGINT        NULL,
    account_id   BIGINT        NULL,
    cost_item_id BIGINT        NULL,
    amount       DECIMAL(19,4) NOT NULL,
    description  VARCHAR(500)  NULL,
    CONSTRAINT pk_cash_document_lines PRIMARY KEY (id),
    CONSTRAINT uk_cash_document_lines_no UNIQUE (document_id, line_no),
    CONSTRAINT fk_cash_document_lines_documents FOREIGN KEY (document_id) REFERENCES cash_documents (id),
    CONSTRAINT fk_cash_document_lines_items FOREIGN KEY (debt_item_id) REFERENCES debt_items (id),
    CONSTRAINT fk_cash_document_lines_accounts FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_cash_document_lines_cost_items FOREIGN KEY (cost_item_id) REFERENCES reference_items (id),
    CONSTRAINT ck_cash_document_lines_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_document_lines_target CHECK ((debt_item_id IS NULL) <> (account_id IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Phân bổ thanh toán vào khoản công nợ (BR-AR-03). Nguồn tổng quát (phiếu thu / chi, đối trừ ở 4D). Đảo chuyển
-- REVERSED, trả lại số còn lại của khoản công nợ.
CREATE TABLE payment_allocations (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id     BIGINT        NOT NULL,
    branch_id      BIGINT        NOT NULL,
    debt_item_id   BIGINT        NOT NULL,
    source_type    VARCHAR(30)   NOT NULL,
    source_id      BIGINT        NOT NULL,
    source_line_id BIGINT        NULL,
    source_no      VARCHAR(50)   NOT NULL,
    posting_date   DATE          NOT NULL,
    amount         DECIMAL(19,4) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    reversed_by    BIGINT        NULL,
    reversed_at    DATETIME(3)   NULL,
    created_by     BIGINT        NULL,
    created_at     DATETIME(3)   NOT NULL,
    CONSTRAINT pk_payment_allocations PRIMARY KEY (id),
    CONSTRAINT fk_payment_allocations_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_payment_allocations_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_payment_allocations_items FOREIGN KEY (debt_item_id) REFERENCES debt_items (id),
    CONSTRAINT ck_payment_allocations_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_allocations_status CHECK (status IN ('ACTIVE', 'REVERSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_allocations_item ON payment_allocations (debt_item_id, status);
CREATE INDEX idx_payment_allocations_source ON payment_allocations (source_type, source_id);

-- Sổ quỹ / sổ tiền gửi: chỉ INSERT. Tiền vào ở vế Nợ, tiền ra ở vế Có; số dư quỹ = Σ Nợ − Σ Có.
CREATE TABLE cash_ledger_entries (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    cash_account_id   BIGINT        NOT NULL,
    posting_date      DATE          NOT NULL,
    transaction_code  VARCHAR(30)   NOT NULL,
    debit_amount      DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit_amount     DECIMAL(19,4) NOT NULL DEFAULT 0,
    partner_id        BIGINT        NULL,
    counterparty_name VARCHAR(255)  NULL,
    reference_type    VARCHAR(30)   NOT NULL,
    reference_id      BIGINT        NOT NULL,
    reference_line_id BIGINT        NULL,
    reference_no      VARCHAR(50)   NOT NULL,
    description       VARCHAR(500)  NULL,
    reversal_of_id    BIGINT        NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    CONSTRAINT pk_cash_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_cash_ledger_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_cash_ledger_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_cash_ledger_accounts FOREIGN KEY (cash_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT fk_cash_ledger_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_cash_ledger_reversal FOREIGN KEY (reversal_of_id) REFERENCES cash_ledger_entries (id),
    CONSTRAINT ck_cash_ledger_amounts CHECK (debit_amount >= 0 AND credit_amount >= 0
        AND ((debit_amount > 0 AND credit_amount = 0) OR (credit_amount > 0 AND debit_amount = 0)))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_cash_ledger_account_date ON cash_ledger_entries (cash_account_id, posting_date);
CREATE INDEX idx_cash_ledger_reference ON cash_ledger_entries (reference_type, reference_id);
CREATE INDEX idx_cash_ledger_company_date ON cash_ledger_entries (company_id, posting_date);

-- Quy tắc hạch toán để trống cả hai vế: cả hai tài khoản do chứng từ quyết định (thu chi khác, chuyển tiền nội bộ);
-- quy tắc vẫn bắt buộc có để kế toán trưởng bật nghiệp vụ (ADR-11, sửa đổi ở 4C).
ALTER TABLE posting_rules DROP CHECK ck_posting_rules_accounts;

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('PT',  'FINANCE', 'Phiếu thu tiền mặt',     'NONE',       FALSE, 710),
    ('PC',  'FINANCE', 'Phiếu chi tiền mặt',     'CONFIGURED', FALSE, 720),
    ('BC',  'FINANCE', 'Giấy báo có',            'NONE',       FALSE, 730),
    ('UNC', 'FINANCE', 'Ủy nhiệm chi',           'CONFIGURED', FALSE, 740),
    ('CTN', 'FINANCE', 'Chuyển tiền nội bộ',     'CONFIGURED', FALSE, 750);

-- Ngưỡng duyệt chi (BR-CSH-03): phiếu chi, ủy nhiệm chi, chuyển tiền từ ngưỡng trở lên phải qua luồng duyệt.
-- Mặc định 20.000.000 đồng là [Giả định]; doanh nghiệp chỉnh ở màn hình tham số.
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('finance.payment-approval-threshold', 'FINANCE', 'Ngưỡng duyệt chi (đồng)',
     'BR-CSH-03. Phiếu chi, ủy nhiệm chi, chuyển tiền nội bộ có số tiền từ ngưỡng trở lên phải duyệt theo luồng '
     'duyệt đã cấu hình; dưới ngưỡng tự duyệt khi gửi.', 'DECIMAL', '20000000', 0, 999999999999, NULL, FALSE, 130);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('cash-account:view',      'FINANCE', 'Xem quỹ, tài khoản ngân hàng',                   720),
    ('cash-account:manage',    'FINANCE', 'Thêm, sửa, ngừng quỹ, tài khoản ngân hàng',      721),
    ('cash-document:view',     'FINANCE', 'Xem phiếu thu, phiếu chi, giao dịch ngân hàng',  730),
    ('cash-document:create',   'FINANCE', 'Lập, sửa, gửi duyệt, hủy chứng từ thu chi',      731),
    ('cash-document:approve',  'FINANCE', 'Duyệt, từ chối chứng từ chi',                    732),
    ('cash-document:post',     'FINANCE', 'Ghi sổ chứng từ thu chi',                        733),
    ('cash-document:reverse',  'FINANCE', 'Đảo chứng từ thu chi đã ghi sổ',                 734),
    ('cash-book:view',         'FINANCE', 'Xem sổ quỹ, sổ tiền gửi, dòng tiền thực tế',     740);

-- Gán quyền mặc định (docs/07 mục 2.6). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code LIKE 'cash-%' AND (
    r.code = 'CHIEF_ACCOUNTANT' OR
    (r.code = 'CASHIER' AND p.code IN ('cash-account:view', 'cash-document:view', 'cash-document:create',
        'cash-document:post', 'cash-book:view')) OR
    (r.code = 'AR_ACCOUNTANT' AND p.code IN ('cash-account:view', 'cash-document:view', 'cash-document:create',
        'cash-book:view')) OR
    (r.code = 'TAX_ACCOUNTANT' AND p.code IN ('cash-account:view', 'cash-document:view', 'cash-book:view')) OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('cash-account:view', 'cash-document:view',
        'cash-document:approve', 'cash-book:view')));
