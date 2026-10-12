-- Giai đoạn 4D (M07): đối trừ công nợ, điều chỉnh công nợ có duyệt, thanh toán tạm ứng, đối chiếu công nợ, kiểm kê
-- quỹ, tạm ứng / hoàn ứng qua chứng từ thu chi (BR-AR-04, 06, 07, BR-CSH-04, 05).

-- Loại công nợ tạm ứng nhân viên (TK 141): tăng ở vế Nợ như phải thu.
ALTER TABLE debt_items DROP CHECK ck_debt_items_type;
ALTER TABLE debt_items ADD CONSTRAINT ck_debt_items_type CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE', 'ADVANCE'));
ALTER TABLE debt_ledger_entries DROP CHECK ck_debt_ledger_type;
ALTER TABLE debt_ledger_entries ADD CONSTRAINT ck_debt_ledger_type
    CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE', 'ADVANCE'));
ALTER TABLE debt_opening_documents DROP CHECK ck_debt_opening_documents_type;
ALTER TABLE debt_opening_documents ADD CONSTRAINT ck_debt_opening_documents_type
    CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE', 'ADVANCE'));

-- Tiền trả trước chưa phân bổ theo đối tượng: bản đọc nhanh của các dòng sổ chi tiết không gắn khoản công nợ, khóa
-- FOR UPDATE khi ghi tăng (thu / chi thừa), dùng (đối trừ), hoàn (đảo). Không âm.
CREATE TABLE debt_prepayments (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    company_id  BIGINT        NOT NULL,
    branch_id   BIGINT        NOT NULL,
    debt_type   VARCHAR(20)   NOT NULL,
    partner_id  BIGINT        NOT NULL,
    amount      DECIMAL(19,4) NOT NULL DEFAULT 0,
    updated_at  DATETIME(3)   NOT NULL,
    CONSTRAINT pk_debt_prepayments PRIMARY KEY (id),
    CONSTRAINT uk_debt_prepayments_party UNIQUE (company_id, branch_id, debt_type, partner_id),
    CONSTRAINT fk_debt_prepayments_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_debt_prepayments_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_debt_prepayments_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT ck_debt_prepayments_type CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE', 'ADVANCE')),
    CONSTRAINT ck_debt_prepayments_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tái tạo từ sổ chi tiết (dữ liệu trả trước ghi ở 4C).
INSERT INTO debt_prepayments (company_id, branch_id, debt_type, partner_id, amount, updated_at)
SELECT company_id, branch_id, debt_type, partner_id,
       SUM(CASE WHEN debt_type = 'PAYABLE' THEN debit_amount - credit_amount ELSE credit_amount - debit_amount END),
       UTC_TIMESTAMP(3)
FROM debt_ledger_entries
WHERE debt_item_id IS NULL
GROUP BY company_id, branch_id, debt_type, partner_id;

-- Tạm ứng (chi) và hoàn ứng (thu) qua chứng từ thu chi.
ALTER TABLE cash_documents DROP CHECK ck_cash_documents_purpose;
ALTER TABLE cash_documents DROP CHECK ck_cash_documents_debt;
ALTER TABLE cash_documents ADD CONSTRAINT ck_cash_documents_purpose CHECK (purpose IN ('CUSTOMER_RECEIPT',
    'SUPPLIER_PAYMENT', 'OTHER_RECEIPT', 'OTHER_PAYMENT', 'INTERNAL_TRANSFER', 'EMPLOYEE_ADVANCE', 'ADVANCE_RETURN'));
ALTER TABLE cash_documents ADD CONSTRAINT ck_cash_documents_debt CHECK (
    (purpose NOT IN ('CUSTOMER_RECEIPT', 'SUPPLIER_PAYMENT', 'ADVANCE_RETURN')
        OR (partner_id IS NOT NULL AND allocation_mode IS NOT NULL))
    AND (purpose <> 'EMPLOYEE_ADVANCE' OR partner_id IS NOT NULL));

-- Chứng từ bù trừ công nợ (DTCN, BR-AR-04): PREPAYMENT = trả trước với hóa đơn cùng loại công nợ; AR_AP = phải thu với
-- phải trả của cùng đối tượng (tổng dòng phải thu = tổng dòng phải trả = total_amount).
CREATE TABLE debt_offset_documents (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    branch_id     BIGINT        NOT NULL,
    document_no   VARCHAR(50)   NOT NULL,
    offset_type   VARCHAR(20)   NOT NULL,
    debt_type     VARCHAR(20)   NULL,
    partner_id    BIGINT        NOT NULL,
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
    CONSTRAINT pk_debt_offset_documents PRIMARY KEY (id),
    CONSTRAINT uk_debt_offset_documents_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_debt_offset_documents_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_debt_offset_documents_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_debt_offset_documents_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT ck_debt_offset_documents_type CHECK (offset_type IN ('PREPAYMENT', 'AR_AP')),
    CONSTRAINT ck_debt_offset_documents_debt CHECK ((offset_type = 'AR_AP') = (debt_type IS NULL)
        AND (debt_type IS NULL OR debt_type IN ('RECEIVABLE', 'PAYABLE', 'ADVANCE'))),
    CONSTRAINT ck_debt_offset_documents_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED',
        'POSTED', 'CANCELLED', 'REVERSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_debt_offset_documents_status ON debt_offset_documents (company_id, branch_id, status, document_date);

CREATE TABLE debt_offset_lines (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    document_id  BIGINT        NOT NULL,
    line_no      INT           NOT NULL,
    debt_item_id BIGINT        NOT NULL,
    amount       DECIMAL(19,4) NOT NULL,
    description  VARCHAR(500)  NULL,
    CONSTRAINT pk_debt_offset_lines PRIMARY KEY (id),
    CONSTRAINT uk_debt_offset_lines_no UNIQUE (document_id, line_no),
    CONSTRAINT fk_debt_offset_lines_documents FOREIGN KEY (document_id) REFERENCES debt_offset_documents (id),
    CONSTRAINT fk_debt_offset_lines_items FOREIGN KEY (debt_item_id) REFERENCES debt_items (id),
    CONSTRAINT ck_debt_offset_lines_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Biên bản đối chiếu công nợ (BBDC, BR-AR-06): số dư hệ thống tại ngày đối chiếu và số đối tượng xác nhận.
-- branch_id NULL = mọi chi nhánh của công ty.
CREATE TABLE debt_reconciliations (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NULL,
    document_no       VARCHAR(50)   NOT NULL,
    debt_type         VARCHAR(20)   NOT NULL,
    partner_id        BIGINT        NOT NULL,
    period_from       DATE          NOT NULL,
    as_of_date        DATE          NOT NULL,
    system_balance    DECIMAL(19,4) NOT NULL,
    confirmed_balance DECIMAL(19,4) NULL,
    status            VARCHAR(20)   NOT NULL,
    contact_name      VARCHAR(255)  NULL,
    confirmed_by      BIGINT        NULL,
    confirmed_at      DATETIME(3)   NULL,
    note              VARCHAR(1000) NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME(3)   NOT NULL,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_debt_reconciliations PRIMARY KEY (id),
    CONSTRAINT uk_debt_reconciliations_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_debt_reconciliations_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_debt_reconciliations_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_debt_reconciliations_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT ck_debt_reconciliations_type CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE', 'ADVANCE')),
    CONSTRAINT ck_debt_reconciliations_status CHECK (status IN ('PENDING', 'MATCHED', 'DIFFERENT', 'CANCELLED')),
    CONSTRAINT ck_debt_reconciliations_dates CHECK (as_of_date >= period_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_debt_reconciliations_partner ON debt_reconciliations (partner_id, as_of_date);

-- Điều chỉnh công nợ (DCCN, BR-AR-07, bắt buộc duyệt) và thanh toán tạm ứng (TTTU, BR-CSH-04: chi phí thực tế trừ vào
-- tạm ứng). Dòng INCREASE sinh khoản công nợ mới; dòng DECREASE giảm một khoản công nợ; account_id là tài khoản đối ứng.
CREATE TABLE debt_adjustment_documents (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    document_no       VARCHAR(50)   NOT NULL,
    document_type     VARCHAR(10)   NOT NULL,
    debt_type         VARCHAR(20)   NOT NULL,
    partner_id        BIGINT        NOT NULL,
    reconciliation_id BIGINT        NULL,
    document_date     DATE          NOT NULL,
    posting_date      DATE          NOT NULL,
    description       VARCHAR(500)  NULL,
    total_amount      DECIMAL(19,4) NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL,
    submitted_by      BIGINT        NULL,
    submitted_at      DATETIME(3)   NULL,
    approved_by       BIGINT        NULL,
    approved_at       DATETIME(3)   NULL,
    posted_by         BIGINT        NULL,
    posted_at         DATETIME(3)   NULL,
    reversed_by       BIGINT        NULL,
    reversed_at       DATETIME(3)   NULL,
    reverse_date      DATE          NULL,
    reason            VARCHAR(500)  NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME(3)   NOT NULL,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_debt_adjustment_documents PRIMARY KEY (id),
    CONSTRAINT uk_debt_adjustment_documents_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_debt_adjustment_documents_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_debt_adjustment_documents_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_debt_adjustment_documents_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_debt_adjustment_documents_types FOREIGN KEY (document_type) REFERENCES sys_document_types (code),
    CONSTRAINT fk_debt_adjustment_documents_reconciliations FOREIGN KEY (reconciliation_id)
        REFERENCES debt_reconciliations (id),
    CONSTRAINT ck_debt_adjustment_documents_type CHECK (debt_type IN ('RECEIVABLE', 'PAYABLE', 'ADVANCE')
        AND (document_type <> 'TTTU' OR debt_type = 'ADVANCE')),
    CONSTRAINT ck_debt_adjustment_documents_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED',
        'POSTED', 'CANCELLED', 'REVERSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_debt_adjustment_documents_status
    ON debt_adjustment_documents (company_id, branch_id, status, document_date);

CREATE TABLE debt_adjustment_lines (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    document_id  BIGINT        NOT NULL,
    line_no      INT           NOT NULL,
    direction    VARCHAR(10)   NOT NULL,
    debt_item_id BIGINT        NULL,
    account_id   BIGINT        NOT NULL,
    cost_item_id BIGINT        NULL,
    amount       DECIMAL(19,4) NOT NULL,
    invoice_no   VARCHAR(50)   NULL,
    due_date     DATE          NULL,
    description  VARCHAR(500)  NULL,
    CONSTRAINT pk_debt_adjustment_lines PRIMARY KEY (id),
    CONSTRAINT uk_debt_adjustment_lines_no UNIQUE (document_id, line_no),
    CONSTRAINT fk_debt_adjustment_lines_documents FOREIGN KEY (document_id) REFERENCES debt_adjustment_documents (id),
    CONSTRAINT fk_debt_adjustment_lines_items FOREIGN KEY (debt_item_id) REFERENCES debt_items (id),
    CONSTRAINT fk_debt_adjustment_lines_accounts FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_debt_adjustment_lines_cost_items FOREIGN KEY (cost_item_id) REFERENCES reference_items (id),
    CONSTRAINT ck_debt_adjustment_lines_direction CHECK (direction IN ('INCREASE', 'DECREASE')
        AND (direction = 'DECREASE') = (debt_item_id IS NOT NULL)),
    CONSTRAINT ck_debt_adjustment_lines_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kiểm kê quỹ tiền mặt (KKQ, BR-CSH-05, bắt buộc duyệt): ghi sổ sinh dòng sổ quỹ chênh lệch thừa / thiếu.
CREATE TABLE cash_counts (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    document_no       VARCHAR(50)   NOT NULL,
    cash_account_id   BIGINT        NOT NULL,
    document_date     DATE          NOT NULL,
    posting_date      DATE          NOT NULL,
    description       VARCHAR(500)  NULL,
    counted_amount    DECIMAL(19,4) NOT NULL,
    book_balance      DECIMAL(19,4) NULL,
    difference_amount DECIMAL(19,4) NULL,
    status            VARCHAR(20)   NOT NULL,
    submitted_by      BIGINT        NULL,
    submitted_at      DATETIME(3)   NULL,
    approved_by       BIGINT        NULL,
    approved_at       DATETIME(3)   NULL,
    posted_by         BIGINT        NULL,
    posted_at         DATETIME(3)   NULL,
    reversed_by       BIGINT        NULL,
    reversed_at       DATETIME(3)   NULL,
    reverse_date      DATE          NULL,
    reason            VARCHAR(500)  NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME(3)   NOT NULL,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_cash_counts PRIMARY KEY (id),
    CONSTRAINT uk_cash_counts_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_cash_counts_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_cash_counts_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_cash_counts_accounts FOREIGN KEY (cash_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT ck_cash_counts_amount CHECK (counted_amount >= 0),
    CONSTRAINT ck_cash_counts_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED', 'POSTED',
        'CANCELLED', 'REVERSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_cash_counts_account ON cash_counts (cash_account_id, document_date);

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('DTCN', 'FINANCE', 'Chứng từ bù trừ công nợ',      'CONFIGURED', FALSE, 760),
    ('DCCN', 'FINANCE', 'Chứng từ điều chỉnh công nợ',  'REQUIRED',   FALSE, 770),
    ('TTTU', 'FINANCE', 'Thanh toán tạm ứng',           'CONFIGURED', FALSE, 780),
    ('BBDC', 'FINANCE', 'Biên bản đối chiếu công nợ',   'NONE',       FALSE, 790),
    ('KKQ',  'FINANCE', 'Kiểm kê quỹ',                  'REQUIRED',   FALSE, 795);

-- Hạn hoàn ứng mặc định (BR-CSH-04): ngày tạm ứng + số ngày; 30 ngày là [Giả định].
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('finance.advance-due-days', 'FINANCE', 'Hạn hoàn ứng (ngày)',
     'BR-CSH-04. Hạn thanh toán tạm ứng = ngày hạch toán phiếu chi tạm ứng + số ngày.', 'INTEGER', '30', 0, 3650,
     NULL, FALSE, 140);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('debt-offset:view',            'FINANCE', 'Xem chứng từ bù trừ công nợ',                       750),
    ('debt-offset:create',          'FINANCE', 'Lập, sửa, gửi duyệt, hủy bù trừ công nợ',          751),
    ('debt-offset:approve',         'FINANCE', 'Duyệt, từ chối bù trừ công nợ',                     752),
    ('debt-offset:post',            'FINANCE', 'Ghi sổ bù trừ công nợ',                             753),
    ('debt-offset:reverse',         'FINANCE', 'Đảo (bỏ) bù trừ công nợ',                          754),
    ('debt-adjustment:view',        'FINANCE', 'Xem điều chỉnh công nợ, thanh toán tạm ứng',        760),
    ('debt-adjustment:create',      'FINANCE', 'Lập, sửa, gửi duyệt, hủy điều chỉnh / thanh toán tạm ứng', 761),
    ('debt-adjustment:approve',     'FINANCE', 'Duyệt, từ chối điều chỉnh / thanh toán tạm ứng',    762),
    ('debt-adjustment:post',        'FINANCE', 'Ghi sổ điều chỉnh / thanh toán tạm ứng',            763),
    ('debt-adjustment:reverse',     'FINANCE', 'Đảo điều chỉnh / thanh toán tạm ứng',               764),
    ('debt-reconciliation:view',    'FINANCE', 'Xem biên bản đối chiếu công nợ',                    770),
    ('debt-reconciliation:manage',  'FINANCE', 'Lập, ghi nhận xác nhận, hủy biên bản đối chiếu',     771),
    ('cash-count:view',             'FINANCE', 'Xem kiểm kê quỹ',                                   780),
    ('cash-count:create',           'FINANCE', 'Lập, sửa, gửi duyệt, hủy kiểm kê quỹ',              781),
    ('cash-count:approve',          'FINANCE', 'Duyệt, từ chối kiểm kê quỹ',                        782),
    ('cash-count:post',             'FINANCE', 'Ghi sổ kiểm kê quỹ',                                783),
    ('cash-count:reverse',          'FINANCE', 'Đảo kiểm kê quỹ',                                   784);

-- Gán quyền mặc định (docs/07 mục 2.7). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (p.code LIKE 'debt-offset:%'
        OR p.code LIKE 'debt-adjustment:%' OR p.code LIKE 'debt-reconciliation:%' OR p.code LIKE 'cash-count:%') AND (
    r.code = 'CHIEF_ACCOUNTANT' OR
    (r.code = 'AR_ACCOUNTANT' AND p.code IN ('debt-offset:view', 'debt-offset:create', 'debt-offset:post',
        'debt-adjustment:view', 'debt-adjustment:create', 'debt-adjustment:post', 'debt-reconciliation:view',
        'debt-reconciliation:manage', 'cash-count:view')) OR
    (r.code = 'CASHIER' AND p.code IN ('debt-adjustment:view', 'debt-adjustment:create', 'debt-reconciliation:view',
        'cash-count:view', 'cash-count:create', 'cash-count:post')) OR
    (r.code = 'TAX_ACCOUNTANT' AND p.code LIKE '%:view') OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND (p.code LIKE '%:view' OR p.code IN ('debt-offset:approve',
        'debt-adjustment:approve', 'cash-count:approve'))));
