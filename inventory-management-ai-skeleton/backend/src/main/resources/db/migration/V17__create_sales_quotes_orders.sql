-- Giai đoạn 6A, 6B: báo giá (BR-SAL-01..05), đơn bán (BR-SAL-10..15), giữ hàng theo đơn (BR-SAL-13, D-37), hạn mức
-- tín dụng (BR-SAL-14, D-22), lịch thu tiền theo đơn (BR-SAL-50). Thiết kế: docs/03 mục 4.12.
-- Số lượng theo dõi tiến độ tính theo đơn vị cơ sở; tiền DECIMAL(19,4), làm tròn đến đồng (D-05).

CREATE TABLE sales_quotes (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    company_id         BIGINT        NOT NULL,
    branch_id          BIGINT        NOT NULL,
    document_no        VARCHAR(50)   NOT NULL,
    document_date      DATE          NOT NULL,
    revision_no        INT           NOT NULL DEFAULT 1,
    previous_quote_id  BIGINT        NULL,
    customer_id        BIGINT        NOT NULL,
    contact_name       VARCHAR(200)  NULL,
    valid_until        DATE          NOT NULL,
    delivery_terms     VARCHAR(500)  NULL,
    warranty_terms     VARCHAR(500)  NULL,
    payment_terms      VARCHAR(500)  NULL,
    sales_user_id      BIGINT        NULL,
    description        VARCHAR(500)  NULL,
    amount             DECIMAL(19,4) NOT NULL,
    discount_amount    DECIMAL(19,4) NOT NULL,
    tax_amount         DECIMAL(19,4) NOT NULL,
    total_amount       DECIMAL(19,4) NOT NULL,
    approval_reasons   VARCHAR(1000) NULL,
    sales_order_id     BIGINT        NULL,
    status             VARCHAR(20)   NOT NULL,
    submitted_by       BIGINT        NULL,
    submitted_at       DATETIME(3)   NULL,
    approved_by        BIGINT        NULL,
    approved_at        DATETIME(3)   NULL,
    sent_at            DATETIME(3)   NULL,
    responded_at       DATETIME(3)   NULL,
    closed_by          BIGINT        NULL,
    closed_at          DATETIME(3)   NULL,
    reason             VARCHAR(500)  NULL,
    created_by         BIGINT        NULL,
    created_at         DATETIME(3)   NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME(3)   NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales_quotes PRIMARY KEY (id),
    CONSTRAINT uk_sales_quotes_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_sales_quotes_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sales_quotes_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_quotes_customers FOREIGN KEY (customer_id) REFERENCES partners (id),
    CONSTRAINT fk_sales_quotes_previous FOREIGN KEY (previous_quote_id) REFERENCES sales_quotes (id),
    CONSTRAINT fk_sales_quotes_sales_users FOREIGN KEY (sales_user_id) REFERENCES sys_users (id),
    CONSTRAINT ck_sales_quotes_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED', 'SENT',
        'ACCEPTED', 'DECLINED', 'EXPIRED', 'CLOSED', 'CANCELLED')),
    INDEX idx_sales_quotes_list (company_id, status, document_date),
    INDEX idx_sales_quotes_customer (customer_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_quote_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    quote_id          BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    product_id        BIGINT        NOT NULL,
    unit_id           BIGINT        NOT NULL,
    conversion_factor DECIMAL(19,6) NOT NULL,
    quantity          DECIMAL(19,3) NOT NULL,
    base_quantity     DECIMAL(19,3) NOT NULL,
    list_price        DECIMAL(19,4) NULL,
    floor_price       DECIMAL(19,4) NULL,
    price_source      VARCHAR(30)   NULL,
    unit_price        DECIMAL(19,4) NOT NULL,
    discount_percent  DECIMAL(7,4)  NOT NULL DEFAULT 0,
    discount_amount   DECIMAL(19,4) NOT NULL,
    amount            DECIMAL(19,4) NOT NULL,
    tax_rate_id       BIGINT        NULL,
    tax_rate          DECIMAL(7,4)  NULL,
    tax_amount        DECIMAL(19,4) NOT NULL,
    promotional       BOOLEAN       NOT NULL DEFAULT FALSE,
    description       VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_quote_lines PRIMARY KEY (id),
    CONSTRAINT uk_sales_quote_lines_no UNIQUE (quote_id, line_no),
    CONSTRAINT fk_sales_quote_lines_quotes FOREIGN KEY (quote_id) REFERENCES sales_quotes (id),
    CONSTRAINT fk_sales_quote_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_quote_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_sales_quote_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_sales_quote_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND unit_price >= 0
        AND discount_percent >= 0 AND discount_percent <= 100 AND amount >= 0 AND tax_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_orders (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    document_no       VARCHAR(50)   NOT NULL,
    document_date     DATE          NOT NULL,
    customer_id       BIGINT        NOT NULL,
    quote_id          BIGINT        NULL,
    contact_name      VARCHAR(200)  NULL,
    delivery_address  VARCHAR(500)  NULL,
    receiver_name     VARCHAR(200)  NULL,
    receiver_phone    VARCHAR(50)   NULL,
    warehouse_id      BIGINT        NOT NULL,
    expected_date     DATE          NULL,
    payment_term_days INT           NULL,
    reserve_stock     BOOLEAN       NOT NULL DEFAULT FALSE,
    reserved_until    DATETIME(3)   NULL,
    sales_user_id     BIGINT        NULL,
    currency_code     VARCHAR(3)    NOT NULL DEFAULT 'VND',
    description       VARCHAR(500)  NULL,
    amount            DECIMAL(19,4) NOT NULL,
    discount_amount   DECIMAL(19,4) NOT NULL,
    tax_amount        DECIMAL(19,4) NOT NULL,
    total_amount      DECIMAL(19,4) NOT NULL,
    approval_reasons  VARCHAR(1000) NULL,
    status            VARCHAR(20)   NOT NULL,
    submitted_by      BIGINT        NULL,
    submitted_at      DATETIME(3)   NULL,
    approved_by       BIGINT        NULL,
    approved_at       DATETIME(3)   NULL,
    closed_by         BIGINT        NULL,
    closed_at         DATETIME(3)   NULL,
    reason            VARCHAR(500)  NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME(3)   NOT NULL,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales_orders PRIMARY KEY (id),
    CONSTRAINT uk_sales_orders_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_sales_orders_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sales_orders_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_orders_customers FOREIGN KEY (customer_id) REFERENCES partners (id),
    CONSTRAINT fk_sales_orders_quotes FOREIGN KEY (quote_id) REFERENCES sales_quotes (id),
    CONSTRAINT fk_sales_orders_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_sales_orders_sales_users FOREIGN KEY (sales_user_id) REFERENCES sys_users (id),
    CONSTRAINT ck_sales_orders_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED', 'IN_PROGRESS',
        'COMPLETED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_sales_orders_amounts CHECK (amount >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND total_amount >= 0),
    INDEX idx_sales_orders_list (company_id, status, document_date),
    INDEX idx_sales_orders_customer (customer_id, status),
    INDEX idx_sales_orders_sales_user (sales_user_id, document_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tiến độ dòng (đơn vị cơ sở): đã xuất, đã giao, đã xuất hóa đơn, đã trả lại, đã đóng (BR-SAL-20).
CREATE TABLE sales_order_lines (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    order_id           BIGINT        NOT NULL,
    line_no            INT           NOT NULL,
    quote_line_id      BIGINT        NULL,
    product_id         BIGINT        NOT NULL,
    unit_id            BIGINT        NOT NULL,
    conversion_factor  DECIMAL(19,6) NOT NULL,
    quantity           DECIMAL(19,3) NOT NULL,
    base_quantity      DECIMAL(19,3) NOT NULL,
    list_price         DECIMAL(19,4) NULL,
    floor_price        DECIMAL(19,4) NULL,
    price_source       VARCHAR(30)   NULL,
    unit_price         DECIMAL(19,4) NOT NULL,
    discount_percent   DECIMAL(7,4)  NOT NULL DEFAULT 0,
    discount_amount    DECIMAL(19,4) NOT NULL,
    amount             DECIMAL(19,4) NOT NULL,
    tax_rate_id        BIGINT        NULL,
    tax_rate           DECIMAL(7,4)  NULL,
    tax_amount         DECIMAL(19,4) NOT NULL,
    promotional        BOOLEAN       NOT NULL DEFAULT FALSE,
    service_line       BOOLEAN       NOT NULL DEFAULT FALSE,
    expected_date      DATE          NULL,
    issued_quantity    DECIMAL(19,3) NOT NULL DEFAULT 0,
    delivered_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    invoiced_quantity  DECIMAL(19,3) NOT NULL DEFAULT 0,
    returned_quantity  DECIMAL(19,3) NOT NULL DEFAULT 0,
    closed_quantity    DECIMAL(19,3) NOT NULL DEFAULT 0,
    description        VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_order_lines PRIMARY KEY (id),
    CONSTRAINT uk_sales_order_lines_no UNIQUE (order_id, line_no),
    CONSTRAINT fk_sales_order_lines_orders FOREIGN KEY (order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_sales_order_lines_quote_lines FOREIGN KEY (quote_line_id) REFERENCES sales_quote_lines (id),
    CONSTRAINT fk_sales_order_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_order_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_sales_order_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_sales_order_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND unit_price >= 0
        AND discount_percent >= 0 AND discount_percent <= 100 AND amount >= 0 AND tax_amount >= 0),
    CONSTRAINT ck_sales_order_lines_progress CHECK (issued_quantity >= 0 AND delivered_quantity >= 0
        AND invoiced_quantity >= 0 AND returned_quantity >= 0 AND closed_quantity >= 0
        AND delivered_quantity <= issued_quantity),
    INDEX idx_sales_order_lines_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Lịch thu tiền theo đơn (BR-SAL-50): đợt, tỷ lệ, mốc tính hạn, số ngày.
CREATE TABLE sales_order_payment_terms (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    order_id         BIGINT        NOT NULL,
    installment_no   INT           NOT NULL,
    percent          DECIMAL(7,4)  NOT NULL,
    amount           DECIMAL(19,4) NOT NULL,
    due_basis        VARCHAR(20)   NOT NULL,
    due_days         INT           NOT NULL DEFAULT 0,
    description      VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_order_payment_terms PRIMARY KEY (id),
    CONSTRAINT uk_sales_order_payment_terms_no UNIQUE (order_id, installment_no),
    CONSTRAINT fk_sales_order_payment_terms_orders FOREIGN KEY (order_id) REFERENCES sales_orders (id),
    CONSTRAINT ck_sales_order_payment_terms_values CHECK (percent > 0 AND percent <= 100 AND amount >= 0
        AND due_days >= 0 AND due_basis IN ('ORDER_DATE', 'DELIVERY', 'INVOICE', 'ACCEPTANCE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE sales_quotes
    ADD CONSTRAINT fk_sales_quotes_orders FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id);

-- Đề nghị mua từ đơn bán thiếu hàng (BR-SAL-15, BR-PUR-01).
ALTER TABLE purchase_requisitions
    ADD COLUMN source_id BIGINT      NULL AFTER source_type,
    ADD COLUMN source_no VARCHAR(50) NULL AFTER source_id,
    DROP CHECK ck_purchase_requisitions_source,
    ADD CONSTRAINT ck_purchase_requisitions_source CHECK (source_type IN ('MANUAL', 'SALES_ORDER'));

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('BG',  'SALES', 'Báo giá',  'CONFIGURED', FALSE, 400),
    ('DBH', 'SALES', 'Đơn bán hàng', 'CONFIGURED', FALSE, 410);

-- [Giả định] Ngưỡng chiết khấu phải duyệt 10%; hiệu lực báo giá mặc định 30 ngày; thời hạn giữ hàng 7 ngày (D-37).
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('sales.discount-approval-percent', 'SALES', 'Chiết khấu dòng phải duyệt từ (%)',
     'BR-SAL-03, D-04. Chiết khấu một dòng vượt ngưỡng (hoặc giá dưới giá sàn) thì báo giá / đơn bán phải duyệt.',
     'DECIMAL', '10', 0, 100, NULL, FALSE, 400),
    ('sales.quote-validity-days', 'SALES', 'Hiệu lực báo giá mặc định (ngày)',
     'BR-SAL-01. Báo giá không ghi hạn hiệu lực thì hết hạn sau số ngày này.', 'INTEGER', '30', 1, 365, NULL, FALSE,
     401),
    ('sales.reservation-days', 'SALES', 'Thời hạn giữ hàng theo đơn (ngày)',
     'BR-SAL-13, D-37. Hết hạn thì giữ chỗ tự nhả.', 'INTEGER', '7', 1, 180, NULL, FALSE, 402);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-quote:view',    'SALES', 'Xem báo giá',                               400),
    ('sales-quote:create',  'SALES', 'Lập, sửa, gửi duyệt, gửi khách, hủy báo giá', 401),
    ('sales-quote:approve', 'SALES', 'Duyệt giá bán (dưới giá sàn, chiết khấu vượt ngưỡng)', 402),
    ('sales-order:view',    'SALES', 'Xem đơn bán',                               410),
    ('sales-order:create',  'SALES', 'Lập, sửa, gửi duyệt, hủy đơn bán; giữ / nhả hàng', 411),
    ('sales-order:approve', 'SALES', 'Duyệt đơn bán (giá, hạn mức tín dụng)',     412),
    ('sales-order:close',   'SALES', 'Đóng phần còn lại của đơn bán',            413);

-- Gán quyền mặc định (docs/07 mục 2.9). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (p.code LIKE 'sales-quote:%' OR p.code LIKE 'sales-order:%')
    AND (
    (r.code IN ('SALES_MANAGER', 'DIRECTOR', 'BRANCH_DIRECTOR')) OR
    (r.code = 'SALES_STAFF' AND p.code IN ('sales-quote:view', 'sales-quote:create', 'sales-order:view',
        'sales-order:create', 'sales-order:close')) OR
    (r.code IN ('TENDER_STAFF', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'DELIVERY_STAFF', 'CHIEF_ACCOUNTANT',
        'AR_ACCOUNTANT', 'TAX_ACCOUNTANT', 'CASHIER', 'PURCHASING_STAFF') AND p.code = 'sales-order:view'));
