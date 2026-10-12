-- Giai đoạn 6E: trả hàng bán (TLB, BR-SAL-32), giảm giá hàng bán (GGB, BR-SAL-36), đổi hàng (BR-SAL-33: trả hàng + phiếu
-- giao mới liên kết), tiếp nhận hàng lỗi khách báo (BR-SAL-34). Thiết kế: docs/03 mục 4.12c.

CREATE TABLE sales_returns (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_type         VARCHAR(10)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    customer_id           BIGINT        NOT NULL,
    sales_invoice_id      BIGINT        NULL,
    warehouse_id          BIGINT        NULL,
    inventory_document_id BIGINT        NULL,
    sales_user_id         BIGINT        NULL,
    description           VARCHAR(500)  NULL,
    amount                DECIMAL(19,4) NOT NULL,
    tax_amount            DECIMAL(19,4) NOT NULL,
    total_amount          DECIMAL(19,4) NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    submitted_by          BIGINT        NULL,
    submitted_at          DATETIME(3)   NULL,
    approved_by           BIGINT        NULL,
    approved_at           DATETIME(3)   NULL,
    posted_by             BIGINT        NULL,
    posted_at             DATETIME(3)   NULL,
    closed_by             BIGINT        NULL,
    closed_at             DATETIME(3)   NULL,
    reversed_by           BIGINT        NULL,
    reversed_at           DATETIME(3)   NULL,
    reverse_date          DATE          NULL,
    reason                VARCHAR(500)  NULL,
    created_by            BIGINT        NULL,
    created_at            DATETIME(3)   NOT NULL,
    updated_by            BIGINT        NULL,
    updated_at            DATETIME(3)   NOT NULL,
    version               INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales_returns PRIMARY KEY (id),
    CONSTRAINT uk_sales_returns_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_sales_returns_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sales_returns_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_returns_customers FOREIGN KEY (customer_id) REFERENCES partners (id),
    CONSTRAINT fk_sales_returns_invoices FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices (id),
    CONSTRAINT fk_sales_returns_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_sales_returns_inventory_documents FOREIGN KEY (inventory_document_id)
        REFERENCES inventory_documents (id),
    CONSTRAINT ck_sales_returns_type CHECK (document_type IN ('TLB', 'GGB')),
    CONSTRAINT ck_sales_returns_kind CHECK ((document_type = 'TLB' AND warehouse_id IS NOT NULL)
        OR (document_type = 'GGB' AND sales_invoice_id IS NOT NULL)),
    CONSTRAINT ck_sales_returns_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_sales_returns_amounts CHECK (amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    INDEX idx_sales_returns_list (company_id, status, document_date),
    INDEX idx_sales_returns_invoice (sales_invoice_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng trả hàng: theo dòng phiếu giao (đúng lô, serial đã xuất) và dòng hóa đơn nếu hàng đã có hóa đơn; dòng giảm giá
-- theo dòng hóa đơn, không có số lượng.
CREATE TABLE sales_return_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    return_id         BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    delivery_line_id  BIGINT        NULL,
    invoice_line_id   BIGINT        NULL,
    order_line_id     BIGINT        NULL,
    product_id        BIGINT        NOT NULL,
    unit_id           BIGINT        NOT NULL,
    conversion_factor DECIMAL(19,6) NOT NULL,
    quantity          DECIMAL(19,3) NULL,
    base_quantity     DECIMAL(19,3) NULL,
    lot_no            VARCHAR(50)   NULL,
    expiry_date       DATE          NULL,
    quality_status    VARCHAR(20)   NULL,
    unit_price        DECIMAL(19,4) NULL,
    amount            DECIMAL(19,4) NOT NULL,
    tax_rate_id       BIGINT        NULL,
    tax_rate          DECIMAL(7,4)  NULL,
    tax_amount        DECIMAL(19,4) NOT NULL,
    description       VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_return_lines PRIMARY KEY (id),
    CONSTRAINT uk_sales_return_lines_no UNIQUE (return_id, line_no),
    CONSTRAINT fk_sales_return_lines_returns FOREIGN KEY (return_id) REFERENCES sales_returns (id),
    CONSTRAINT fk_sales_return_lines_delivery_lines FOREIGN KEY (delivery_line_id)
        REFERENCES sales_delivery_lines (id),
    CONSTRAINT fk_sales_return_lines_invoice_lines FOREIGN KEY (invoice_line_id) REFERENCES sales_invoice_lines (id),
    CONSTRAINT fk_sales_return_lines_order_lines FOREIGN KEY (order_line_id) REFERENCES sales_order_lines (id),
    CONSTRAINT fk_sales_return_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_return_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_sales_return_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_sales_return_lines_values CHECK (amount >= 0 AND tax_amount >= 0
        AND (quantity IS NULL OR (quantity > 0 AND base_quantity > 0))),
    INDEX idx_sales_return_lines_delivery_line (delivery_line_id, lot_no),
    INDEX idx_sales_return_lines_invoice_line (invoice_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_return_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_sales_return_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_sales_return_serials_lines FOREIGN KEY (line_id) REFERENCES sales_return_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tiếp nhận hàng lỗi khách báo (BR-SAL-34): tiếp nhận → kiểm tra → quyết định đổi / sửa / trả / bảo hành / từ chối.
CREATE TABLE sales_complaints (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    complaint_no      VARCHAR(50)   NOT NULL,
    received_date     DATE          NOT NULL,
    customer_id       BIGINT        NOT NULL,
    delivery_id       BIGINT        NULL,
    sales_invoice_id  BIGINT        NULL,
    product_id        BIGINT        NOT NULL,
    lot_no            VARCHAR(50)   NULL,
    serial_no         VARCHAR(100)  NULL,
    quantity          DECIMAL(19,3) NOT NULL,
    description       VARCHAR(1000) NOT NULL,
    inspection_result VARCHAR(1000) NULL,
    decision          VARCHAR(20)   NULL,
    resolution_note   VARCHAR(1000) NULL,
    sales_return_id   BIGINT        NULL,
    status            VARCHAR(20)   NOT NULL,
    inspected_by      BIGINT        NULL,
    inspected_at      DATETIME(3)   NULL,
    resolved_by       BIGINT        NULL,
    resolved_at       DATETIME(3)   NULL,
    sales_user_id     BIGINT        NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME(3)   NOT NULL,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales_complaints PRIMARY KEY (id),
    CONSTRAINT uk_sales_complaints_no UNIQUE (company_id, complaint_no),
    CONSTRAINT fk_sales_complaints_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sales_complaints_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_complaints_customers FOREIGN KEY (customer_id) REFERENCES partners (id),
    CONSTRAINT fk_sales_complaints_deliveries FOREIGN KEY (delivery_id) REFERENCES sales_deliveries (id),
    CONSTRAINT fk_sales_complaints_invoices FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoices (id),
    CONSTRAINT fk_sales_complaints_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_complaints_returns FOREIGN KEY (sales_return_id) REFERENCES sales_returns (id),
    CONSTRAINT ck_sales_complaints_status CHECK (status IN ('OPEN', 'INSPECTED', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_sales_complaints_decision CHECK (decision IS NULL OR decision IN ('EXCHANGE', 'REPAIR', 'RETURN',
        'WARRANTY', 'REJECT')),
    CONSTRAINT ck_sales_complaints_quantity CHECK (quantity > 0),
    INDEX idx_sales_complaints_list (company_id, status, received_date),
    INDEX idx_sales_complaints_customer (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('TLB', 'SALES', 'Hàng bán bị trả lại',  'CONFIGURED', FALSE, 440),
    ('GGB', 'SALES', 'Giảm giá hàng bán',     'CONFIGURED', FALSE, 441),
    ('KN',  'SALES', 'Tiếp nhận hàng lỗi',    'NONE',       FALSE, 442);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-return:view',       'SALES', 'Xem trả hàng / giảm giá hàng bán',                   440),
    ('sales-return:create',     'SALES', 'Lập, sửa, gửi duyệt, hủy trả hàng / giảm giá; đổi hàng', 441),
    ('sales-return:approve',    'SALES', 'Duyệt trả hàng / giảm giá hàng bán',                442),
    ('sales-return:post',       'SALES', 'Ghi sổ trả hàng / giảm giá hàng bán',               443),
    ('sales-return:reverse',    'SALES', 'Đảo trả hàng / giảm giá hàng bán',                  444),
    ('sales-complaint:view',    'SALES', 'Xem tiếp nhận hàng lỗi',                             450),
    ('sales-complaint:create',  'SALES', 'Tiếp nhận hàng lỗi khách báo',                       451),
    ('sales-complaint:resolve', 'SALES', 'Kiểm tra, quyết định xử lý hàng lỗi',                452);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p
    ON (p.code LIKE 'sales-return:%' OR p.code LIKE 'sales-complaint:%') AND (
    r.code = 'CHIEF_ACCOUNTANT' AND p.code LIKE 'sales-return:%' OR
    (r.code IN ('AR_ACCOUNTANT', 'TAX_ACCOUNTANT') AND p.code IN ('sales-return:view', 'sales-return:create',
        'sales-return:post', 'sales-complaint:view')) OR
    (r.code = 'SALES_MANAGER' AND p.code IN ('sales-return:view', 'sales-return:create', 'sales-return:approve',
        'sales-complaint:view', 'sales-complaint:create', 'sales-complaint:resolve')) OR
    (r.code = 'SALES_STAFF' AND p.code IN ('sales-return:view', 'sales-return:create', 'sales-complaint:view',
        'sales-complaint:create')) OR
    (r.code IN ('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF') AND p.code IN ('sales-return:view', 'sales-return:post',
        'sales-complaint:view')) OR
    (r.code IN ('QA', 'TECHNICIAN') AND p.code IN ('sales-complaint:view', 'sales-complaint:resolve')) OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('sales-return:view', 'sales-return:approve',
        'sales-complaint:view', 'sales-complaint:resolve')));
