-- D-54: gộp chứng từ bán hàng. Đơn bán hàng (DBH) là chứng từ ghi sổ duy nhất: kho xuất, lô (FEFO hoặc chỉ định) /
-- serial theo dòng, số hóa đơn điện tử (ghi sau khi phát hành trên MISA); ghi sổ = xuất kho (phiếu PXK liên kết) +
-- doanh thu + công nợ phải thu + bút toán. Theo dõi giao hàng (đang giao, khách nhận) là trường trên đơn, không ảnh
-- hưởng sổ. Bỏ báo giá (BG), phiếu giao hàng (GH), hóa đơn bán (HDB), bán nhanh. Trả hàng / giảm giá (TLB, GGB) và
-- tiếp nhận hàng lỗi (KN) tham chiếu đơn bán đã ghi sổ. Thiết kế: docs/03 mục 4.12.
-- Chưa có dữ liệu thật (chưa triển khai production): các bảng chứng từ bán được xóa và tạo lại.

DROP TABLE sales_complaints;
DROP TABLE sales_return_serials;
DROP TABLE sales_return_lines;
DROP TABLE sales_returns;
DROP TABLE sales_invoice_lines;
DROP TABLE sales_invoices;
DROP TABLE sales_delivery_items;
DROP TABLE sales_delivery_serials;
DROP TABLE sales_delivery_lines;
DROP TABLE sales_deliveries;
DROP TABLE sales_order_lines;
DROP TABLE sales_quote_lines;
ALTER TABLE sales_quotes DROP FOREIGN KEY fk_sales_quotes_orders;
DROP TABLE sales_order_payment_terms;
DROP TABLE sales_orders;
DROP TABLE sales_quotes;

CREATE TABLE sales_orders (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    customer_id           BIGINT        NOT NULL,
    contact_name          VARCHAR(200)  NULL,
    delivery_address      VARCHAR(500)  NULL,
    receiver_name         VARCHAR(200)  NULL,
    receiver_phone        VARCHAR(50)   NULL,
    warehouse_id          BIGINT        NOT NULL,
    expected_date         DATE          NULL,
    payment_term_days     INT           NULL,
    due_date              DATE          NULL,
    reserve_stock         BOOLEAN       NOT NULL DEFAULT FALSE,
    reserved_until        DATETIME(3)   NULL,
    sales_user_id         BIGINT        NULL,
    -- Đơn đổi hàng lập từ phiếu trả hàng (BR-SAL-33).
    source_return_id      BIGINT        NULL,
    currency_code         VARCHAR(3)    NOT NULL DEFAULT 'VND',
    description           VARCHAR(500)  NULL,
    amount                DECIMAL(19,4) NOT NULL,
    discount_amount       DECIMAL(19,4) NOT NULL,
    tax_amount            DECIMAL(19,4) NOT NULL,
    total_amount          DECIMAL(19,4) NOT NULL,
    approval_reasons      VARCHAR(1000) NULL,
    -- Hóa đơn điện tử phát hành trên MISA (BR-SAL-31); khóa chống trùng NULL khi hủy / đảo.
    einvoice_series       VARCHAR(20)   NULL,
    einvoice_no           VARCHAR(30)   NULL,
    einvoice_date         DATE          NULL,
    tax_authority_code    VARCHAR(50)   NULL,
    einvoice_key          VARCHAR(60)   NULL,
    inventory_document_id BIGINT        NULL,
    -- Theo dõi giao hàng sau ghi sổ (BR-SAL-22): PENDING, IN_TRANSIT, DELIVERED; giao thất bại = đảo đơn.
    delivery_status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    shipper_name          VARCHAR(200)  NULL,
    shipped_at            DATETIME(3)   NULL,
    received_date         DATE          NULL,
    received_by           VARCHAR(200)  NULL,
    signed_document_ref   VARCHAR(200)  NULL,
    delivery_note         VARCHAR(500)  NULL,
    status                VARCHAR(20)   NOT NULL,
    submitted_by          BIGINT        NULL,
    submitted_at          DATETIME(3)   NULL,
    approved_by           BIGINT        NULL,
    approved_at           DATETIME(3)   NULL,
    posted_by             BIGINT        NULL,
    posted_at             DATETIME(3)   NULL,
    reversed_by           BIGINT        NULL,
    reversed_at           DATETIME(3)   NULL,
    reverse_date          DATE          NULL,
    reason                VARCHAR(500)  NULL,
    created_by            BIGINT        NULL,
    created_at            DATETIME(3)   NOT NULL,
    updated_by            BIGINT        NULL,
    updated_at            DATETIME(3)   NOT NULL,
    version               INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_sales_orders PRIMARY KEY (id),
    CONSTRAINT uk_sales_orders_no UNIQUE (company_id, document_no),
    CONSTRAINT uk_sales_orders_einvoice UNIQUE (company_id, einvoice_key),
    CONSTRAINT fk_sales_orders_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sales_orders_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_orders_customers FOREIGN KEY (customer_id) REFERENCES partners (id),
    CONSTRAINT fk_sales_orders_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_sales_orders_sales_users FOREIGN KEY (sales_user_id) REFERENCES sys_users (id),
    CONSTRAINT fk_sales_orders_inventory FOREIGN KEY (inventory_document_id) REFERENCES inventory_documents (id),
    CONSTRAINT ck_sales_orders_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED', 'CANCELLED',
        'POSTED', 'REVERSED')),
    CONSTRAINT ck_sales_orders_delivery CHECK (delivery_status IN ('PENDING', 'IN_TRANSIT', 'DELIVERED')),
    CONSTRAINT ck_sales_orders_amounts CHECK (amount >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND total_amount >= 0),
    CONSTRAINT ck_sales_orders_dates CHECK (posting_date >= document_date),
    INDEX idx_sales_orders_list (company_id, status, document_date),
    INDEX idx_sales_orders_customer (customer_id, status),
    INDEX idx_sales_orders_sales_user (sales_user_id, document_date),
    INDEX idx_sales_orders_posting (company_id, status, posting_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng đơn bán: giá (BR-SAL-02, 03, 35), lô chỉ định (trống: FEFO; khác FEFO phải có lý do), theo dõi sau ghi sổ: số
-- khách đã trả lại (đơn vị cơ sở), giá trị đã giảm (trả hàng + giảm giá, trước thuế).
CREATE TABLE sales_order_lines (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    order_id            BIGINT        NOT NULL,
    line_no             INT           NOT NULL,
    product_id          BIGINT        NOT NULL,
    unit_id             BIGINT        NOT NULL,
    conversion_factor   DECIMAL(19,6) NOT NULL,
    quantity            DECIMAL(19,3) NOT NULL,
    base_quantity       DECIMAL(19,3) NOT NULL,
    list_price          DECIMAL(19,4) NULL,
    floor_price         DECIMAL(19,4) NULL,
    price_source        VARCHAR(30)   NULL,
    unit_price          DECIMAL(19,4) NOT NULL,
    discount_percent    DECIMAL(7,4)  NOT NULL DEFAULT 0,
    discount_amount     DECIMAL(19,4) NOT NULL,
    amount              DECIMAL(19,4) NOT NULL,
    tax_rate_id         BIGINT        NULL,
    tax_rate            DECIMAL(7,4)  NULL,
    tax_amount          DECIMAL(19,4) NOT NULL,
    promotional         BOOLEAN       NOT NULL DEFAULT FALSE,
    service_line        BOOLEAN       NOT NULL DEFAULT FALSE,
    expected_date       DATE          NULL,
    lot_no              VARCHAR(50)   NULL,
    lot_override_reason VARCHAR(500)  NULL,
    returned_quantity   DECIMAL(19,3) NOT NULL DEFAULT 0,
    adjusted_amount     DECIMAL(19,4) NOT NULL DEFAULT 0,
    description         VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_order_lines PRIMARY KEY (id),
    CONSTRAINT uk_sales_order_lines_no UNIQUE (order_id, line_no),
    CONSTRAINT fk_sales_order_lines_orders FOREIGN KEY (order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_sales_order_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_order_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_sales_order_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_sales_order_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND unit_price >= 0
        AND discount_percent >= 0 AND discount_percent <= 100 AND amount >= 0 AND tax_amount >= 0),
    CONSTRAINT ck_sales_order_lines_progress CHECK (returned_quantity >= 0 AND returned_quantity <= base_quantity
        AND adjusted_amount >= 0 AND adjusted_amount <= amount),
    INDEX idx_sales_order_lines_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Serial chỉ định khi lập đơn (hàng quản lý serial; trống: kho chọn).
CREATE TABLE sales_order_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_sales_order_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_sales_order_serials_lines FOREIGN KEY (line_id) REFERENCES sales_order_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hàng thực xuất khi ghi sổ: lô, hạn dùng, serial, trạng thái, số lượng (đơn vị cơ sở).
CREATE TABLE sales_order_items (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    line_id        BIGINT        NOT NULL,
    lot_id         BIGINT        NULL,
    lot_no         VARCHAR(50)   NULL,
    expiry_date    DATE          NULL,
    serial_no      VARCHAR(100)  NULL,
    quality_status VARCHAR(20)   NOT NULL,
    base_quantity  DECIMAL(19,3) NOT NULL,
    CONSTRAINT pk_sales_order_items PRIMARY KEY (id),
    CONSTRAINT fk_sales_order_items_lines FOREIGN KEY (line_id) REFERENCES sales_order_lines (id),
    CONSTRAINT ck_sales_order_items_quantity CHECK (base_quantity > 0),
    INDEX idx_sales_order_items_line (line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_order_payment_terms (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_id       BIGINT        NOT NULL,
    installment_no INT           NOT NULL,
    percent        DECIMAL(7,4)  NOT NULL,
    amount         DECIMAL(19,4) NOT NULL,
    due_basis      VARCHAR(20)   NOT NULL,
    due_days       INT           NOT NULL DEFAULT 0,
    description    VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_order_payment_terms PRIMARY KEY (id),
    CONSTRAINT uk_sales_order_payment_terms_no UNIQUE (order_id, installment_no),
    CONSTRAINT fk_sales_order_payment_terms_orders FOREIGN KEY (order_id) REFERENCES sales_orders (id),
    CONSTRAINT ck_sales_order_payment_terms_values CHECK (percent > 0 AND percent <= 100 AND amount >= 0
        AND due_days >= 0 AND due_basis IN ('ORDER_DATE', 'DELIVERY', 'INVOICE', 'ACCEPTANCE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hàng bán bị trả lại (TLB) và giảm giá hàng bán (GGB) theo một đơn bán đã ghi sổ.
CREATE TABLE sales_returns (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_type         VARCHAR(10)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    customer_id           BIGINT        NOT NULL,
    sales_order_id        BIGINT        NOT NULL,
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
    CONSTRAINT fk_sales_returns_orders FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_sales_returns_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_sales_returns_inventory_documents FOREIGN KEY (inventory_document_id)
        REFERENCES inventory_documents (id),
    CONSTRAINT ck_sales_returns_type CHECK (document_type IN ('TLB', 'GGB')),
    CONSTRAINT ck_sales_returns_kind CHECK (document_type = 'GGB' OR warehouse_id IS NOT NULL),
    CONSTRAINT ck_sales_returns_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_sales_returns_amounts CHECK (amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    INDEX idx_sales_returns_list (company_id, status, document_date),
    INDEX idx_sales_returns_order (sales_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE sales_orders
    ADD CONSTRAINT fk_sales_orders_source_returns FOREIGN KEY (source_return_id) REFERENCES sales_returns (id);

-- Dòng trả hàng: theo dòng đơn bán và lô đã xuất; dòng giảm giá: theo dòng đơn bán, không có số lượng.
CREATE TABLE sales_return_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    return_id         BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    order_line_id     BIGINT        NOT NULL,
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
    CONSTRAINT fk_sales_return_lines_order_lines FOREIGN KEY (order_line_id) REFERENCES sales_order_lines (id),
    CONSTRAINT fk_sales_return_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_return_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_sales_return_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_sales_return_lines_values CHECK (amount >= 0 AND tax_amount >= 0
        AND (quantity IS NULL OR (quantity > 0 AND base_quantity > 0))),
    INDEX idx_sales_return_lines_order_line (order_line_id, lot_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_return_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_sales_return_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_sales_return_serials_lines FOREIGN KEY (line_id) REFERENCES sales_return_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_complaints (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    complaint_no      VARCHAR(50)   NOT NULL,
    received_date     DATE          NOT NULL,
    customer_id       BIGINT        NOT NULL,
    sales_order_id    BIGINT        NULL,
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
    CONSTRAINT fk_sales_complaints_orders FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_sales_complaints_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_complaints_returns FOREIGN KEY (sales_return_id) REFERENCES sales_returns (id),
    CONSTRAINT ck_sales_complaints_status CHECK (status IN ('OPEN', 'INSPECTED', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_sales_complaints_decision CHECK (decision IS NULL OR decision IN ('EXCHANGE', 'REPAIR', 'RETURN',
        'WARRANTY', 'REJECT')),
    CONSTRAINT ck_sales_complaints_quantity CHECK (quantity > 0),
    INDEX idx_sales_complaints_list (company_id, status, received_date),
    INDEX idx_sales_complaints_customer (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Luồng duyệt, yêu cầu duyệt của các chứng từ bán (bảng chứng từ đã tạo lại nên yêu cầu cũ không còn chứng từ).
DELETE a FROM approval_actions a JOIN approval_requests r ON r.id = a.request_id
WHERE r.document_type IN ('BG', 'DBH', 'GH', 'HDB', 'TLB', 'GGB');
DELETE s FROM approval_request_steps s JOIN approval_requests r ON r.id = s.request_id
WHERE r.document_type IN ('BG', 'DBH', 'GH', 'HDB', 'TLB', 'GGB');
DELETE FROM approval_requests WHERE document_type IN ('BG', 'DBH', 'GH', 'HDB', 'TLB', 'GGB');
DELETE s FROM approval_workflow_steps s JOIN approval_workflows w ON w.id = s.workflow_id
WHERE w.document_type IN ('BG', 'GH', 'HDB');
DELETE FROM approval_workflows WHERE document_type IN ('BG', 'GH', 'HDB');
DELETE FROM approval_delegations WHERE document_type IN ('BG', 'GH', 'HDB');
DELETE FROM document_number_formats WHERE document_type IN ('BG', 'GH', 'HDB');

-- Quy tắc hạch toán và loại chứng từ đã bỏ: chỉ xóa khi chưa có bút toán tham chiếu (sổ phụ chỉ INSERT).
DELETE FROM posting_rules WHERE document_type IN ('BG', 'GH', 'HDB')
    AND id NOT IN (SELECT DISTINCT posting_rule_id FROM journal_entry_lines WHERE posting_rule_id IS NOT NULL);
DELETE FROM sys_document_types WHERE code IN ('BG', 'GH', 'HDB')
    AND code NOT IN (SELECT document_type FROM posting_rules);

DELETE FROM sys_parameters WHERE param_key = 'sales.quote-validity-days';
DELETE FROM sys_parameter_definitions WHERE param_key = 'sales.quote-validity-days';
UPDATE sys_parameter_definitions
SET description = 'BR-SAL-03, D-04. Chiết khấu một dòng vượt ngưỡng (hoặc giá dưới giá sàn) thì đơn bán phải duyệt.'
WHERE param_key = 'sales.discount-approval-percent';

DELETE rp FROM sys_role_permissions rp JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code LIKE 'sales-quote:%' OR p.code LIKE 'sales-delivery:%' OR p.code LIKE 'sales-invoice:%'
   OR p.code IN ('sales-quick-sale:execute', 'sales-order:close');
DELETE FROM sys_permissions WHERE code LIKE 'sales-quote:%' OR code LIKE 'sales-delivery:%'
   OR code LIKE 'sales-invoice:%' OR code IN ('sales-quick-sale:execute', 'sales-order:close');

UPDATE sys_document_types SET name = 'Đơn bán hàng (kèm xuất kho, hóa đơn)' WHERE code = 'DBH';
UPDATE sys_permissions SET name = 'Lập, sửa, gửi duyệt, hủy đơn bán; giữ / nhả hàng' WHERE code = 'sales-order:create';

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-order:post',    'SALES', 'Ghi sổ đơn bán (xuất kho, doanh thu, công nợ); ghi số hóa đơn điện tử', 413),
    ('sales-order:deliver', 'SALES', 'Cập nhật giao hàng: đang giao, khách nhận',                          414),
    ('sales-order:reverse', 'SALES', 'Đảo đơn bán đã ghi sổ (kể cả giao thất bại)',                        415);

-- Gán quyền mặc định (docs/07 mục 2.9): ghi sổ cho kho và kế toán (trước đây xuất kho theo phiếu giao / ghi sổ hóa
-- đơn bán), giao hàng cho nhân viên giao hàng và kho. [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (
    (p.code = 'sales-order:post' AND r.code IN ('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'CHIEF_ACCOUNTANT',
        'AR_ACCOUNTANT', 'TAX_ACCOUNTANT')) OR
    (p.code = 'sales-order:deliver' AND r.code IN ('DELIVERY_STAFF', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF',
        'SALES_MANAGER', 'SALES_STAFF')) OR
    (p.code = 'sales-order:reverse' AND r.code IN ('CHIEF_ACCOUNTANT', 'WAREHOUSE_MANAGER')))
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
