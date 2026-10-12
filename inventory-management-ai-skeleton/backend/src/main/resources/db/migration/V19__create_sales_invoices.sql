-- Giai đoạn 6D: hóa đơn bán (HDB, BR-SAL-30) từ một hoặc nhiều phiếu giao của cùng khách, dòng dịch vụ theo đơn bán;
-- tham chiếu hóa đơn điện tử phát hành trên MISA (BR-SAL-31, H-32); ghi sổ công nợ phải thu và doanh thu (D-35).
-- Thiết kế: docs/03 mục 4.12b.

CREATE TABLE sales_invoices (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    company_id         BIGINT        NOT NULL,
    branch_id          BIGINT        NOT NULL,
    document_no        VARCHAR(50)   NOT NULL,
    document_date      DATE          NOT NULL,
    posting_date       DATE          NOT NULL,
    customer_id        BIGINT        NOT NULL,
    buyer_name         VARCHAR(255)  NULL,
    buyer_tax_code     VARCHAR(20)   NULL,
    buyer_address      VARCHAR(500)  NULL,
    invoice_date       DATE          NOT NULL,
    due_date           DATE          NOT NULL,
    einvoice_series    VARCHAR(20)   NULL,
    einvoice_no        VARCHAR(30)   NULL,
    einvoice_date      DATE          NULL,
    tax_authority_code VARCHAR(50)   NULL,
    -- Khóa chống trùng số hóa đơn điện tử (ký hiệu|số); NULL khi chưa có hoặc hủy / đảo.
    einvoice_key       VARCHAR(60)   NULL,
    sales_user_id      BIGINT        NULL,
    description        VARCHAR(500)  NULL,
    amount             DECIMAL(19,4) NOT NULL,
    discount_amount    DECIMAL(19,4) NOT NULL,
    tax_amount         DECIMAL(19,4) NOT NULL,
    total_amount       DECIMAL(19,4) NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    submitted_by       BIGINT        NULL,
    submitted_at       DATETIME(3)   NULL,
    approved_by        BIGINT        NULL,
    approved_at        DATETIME(3)   NULL,
    posted_by          BIGINT        NULL,
    posted_at          DATETIME(3)   NULL,
    closed_by          BIGINT        NULL,
    closed_at          DATETIME(3)   NULL,
    reversed_by        BIGINT        NULL,
    reversed_at        DATETIME(3)   NULL,
    reverse_date       DATE          NULL,
    reason             VARCHAR(500)  NULL,
    created_by         BIGINT        NULL,
    created_at         DATETIME(3)   NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME(3)   NOT NULL,
    version            INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales_invoices PRIMARY KEY (id),
    CONSTRAINT uk_sales_invoices_no UNIQUE (company_id, document_no),
    CONSTRAINT uk_sales_invoices_einvoice UNIQUE (company_id, einvoice_key),
    CONSTRAINT fk_sales_invoices_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sales_invoices_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_invoices_customers FOREIGN KEY (customer_id) REFERENCES partners (id),
    CONSTRAINT fk_sales_invoices_sales_users FOREIGN KEY (sales_user_id) REFERENCES sys_users (id),
    CONSTRAINT ck_sales_invoices_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_sales_invoices_amounts CHECK (amount >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND total_amount >= 0),
    CONSTRAINT ck_sales_invoices_dates CHECK (due_date >= invoice_date),
    INDEX idx_sales_invoices_list (company_id, status, document_date),
    INDEX idx_sales_invoices_customer (customer_id, status),
    INDEX idx_sales_invoices_sales_user (sales_user_id, posting_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng hàng hóa theo dòng phiếu giao đã xuất kho; dòng dịch vụ theo dòng đơn bán dịch vụ. Giá, chiết khấu, thuế theo
-- dòng đơn bán. Số đã trả lại, giá trị đã giảm (trả hàng / giảm giá, trước thuế) cập nhật ở Giai đoạn 6E.
CREATE TABLE sales_invoice_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_id        BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    delivery_line_id  BIGINT        NULL,
    order_line_id     BIGINT        NOT NULL,
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
    returned_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    adjusted_amount   DECIMAL(19,4) NOT NULL DEFAULT 0,
    description       VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_invoice_lines PRIMARY KEY (id),
    CONSTRAINT uk_sales_invoice_lines_no UNIQUE (invoice_id, line_no),
    CONSTRAINT fk_sales_invoice_lines_invoices FOREIGN KEY (invoice_id) REFERENCES sales_invoices (id),
    CONSTRAINT fk_sales_invoice_lines_delivery_lines FOREIGN KEY (delivery_line_id)
        REFERENCES sales_delivery_lines (id),
    CONSTRAINT fk_sales_invoice_lines_order_lines FOREIGN KEY (order_line_id) REFERENCES sales_order_lines (id),
    CONSTRAINT fk_sales_invoice_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_invoice_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_sales_invoice_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_sales_invoice_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND unit_price >= 0
        AND amount >= 0 AND tax_amount >= 0 AND returned_quantity >= 0 AND adjusted_amount >= 0
        AND adjusted_amount <= amount),
    INDEX idx_sales_invoice_lines_delivery_line (delivery_line_id),
    INDEX idx_sales_invoice_lines_order_line (order_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('HDB', 'SALES', 'Hóa đơn bán hàng', 'CONFIGURED', FALSE, 430);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-invoice:view',    'SALES', 'Xem hóa đơn bán',                                       430),
    ('sales-invoice:create',  'SALES', 'Lập, sửa, gửi duyệt, hủy hóa đơn bán; cập nhật số HĐĐT', 431),
    ('sales-invoice:approve', 'SALES', 'Duyệt hóa đơn bán (khi cấu hình luồng)',               432),
    ('sales-invoice:post',    'SALES', 'Ghi sổ hóa đơn bán (doanh thu, công nợ phải thu)',      433),
    ('sales-invoice:reverse', 'SALES', 'Đảo hóa đơn bán',                                       434);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code LIKE 'sales-invoice:%' AND (
    r.code = 'CHIEF_ACCOUNTANT' OR
    (r.code IN ('AR_ACCOUNTANT', 'TAX_ACCOUNTANT') AND p.code IN ('sales-invoice:view', 'sales-invoice:create',
        'sales-invoice:post')) OR
    (r.code IN ('SALES_MANAGER', 'SALES_STAFF', 'CASHIER') AND p.code = 'sales-invoice:view') OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('sales-invoice:view', 'sales-invoice:approve')));
