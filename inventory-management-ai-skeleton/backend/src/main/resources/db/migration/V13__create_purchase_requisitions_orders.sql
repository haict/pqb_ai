-- Giai đoạn 5A: đề nghị mua (BR-PUR-01), đơn mua hàng (BR-PUR-04), tiến độ theo dòng (BR-PUR-05), hàng đang về
-- (BR mục 5.4). Thiết kế: docs/03 mục 4.11. Chứng từ nhóm B (điều phối): không ghi sổ phụ, chỉ đổi chỉ tiêu điều phối.
-- Số lượng theo dõi tiến độ (đã đặt, đã nhận, đã nhập, lỗi, đã nhận hóa đơn, đã đóng) tính theo đơn vị cơ sở.

CREATE TABLE purchase_requisitions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    branch_id     BIGINT        NOT NULL,
    document_no   VARCHAR(50)   NOT NULL,
    document_date DATE          NOT NULL,
    required_date DATE          NULL,
    requester_id  BIGINT        NULL,
    source_type   VARCHAR(30)   NOT NULL,
    description   VARCHAR(500)  NULL,
    status        VARCHAR(20)   NOT NULL,
    submitted_by  BIGINT        NULL,
    submitted_at  DATETIME(3)   NULL,
    approved_by   BIGINT        NULL,
    approved_at   DATETIME(3)   NULL,
    closed_by     BIGINT        NULL,
    closed_at     DATETIME(3)   NULL,
    reason        VARCHAR(500)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME(3)   NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME(3)   NOT NULL,
    version       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_requisitions PRIMARY KEY (id),
    CONSTRAINT uk_purchase_requisitions_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_purchase_requisitions_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_requisitions_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_purchase_requisitions_requester FOREIGN KEY (requester_id) REFERENCES sys_users (id),
    CONSTRAINT ck_purchase_requisitions_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED',
        'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_purchase_requisitions_source CHECK (source_type IN ('MANUAL')),
    INDEX idx_purchase_requisitions_list (company_id, status, document_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_requisition_lines (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    requisition_id        BIGINT        NOT NULL,
    line_no               INT           NOT NULL,
    product_id            BIGINT        NOT NULL,
    unit_id               BIGINT        NOT NULL,
    conversion_factor     DECIMAL(19,6) NOT NULL,
    quantity              DECIMAL(19,3) NOT NULL,
    base_quantity         DECIMAL(19,3) NOT NULL,
    ordered_quantity      DECIMAL(19,3) NOT NULL DEFAULT 0,
    closed_quantity       DECIMAL(19,3) NOT NULL DEFAULT 0,
    suggested_supplier_id BIGINT        NULL,
    required_date         DATE          NULL,
    note                  VARCHAR(500)  NULL,
    CONSTRAINT pk_purchase_requisition_lines PRIMARY KEY (id),
    CONSTRAINT uk_purchase_requisition_lines_no UNIQUE (requisition_id, line_no),
    CONSTRAINT fk_purchase_requisition_lines_requisitions FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions (id),
    CONSTRAINT fk_purchase_requisition_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_requisition_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_purchase_requisition_lines_suppliers FOREIGN KEY (suggested_supplier_id) REFERENCES partners (id),
    CONSTRAINT ck_purchase_requisition_lines_quantity CHECK (quantity > 0 AND base_quantity > 0
        AND conversion_factor > 0 AND ordered_quantity >= 0 AND closed_quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_orders (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    document_no       VARCHAR(50)   NOT NULL,
    document_date     DATE          NOT NULL,
    supplier_id       BIGINT        NOT NULL,
    warehouse_id      BIGINT        NOT NULL,
    expected_date     DATE          NULL,
    payment_term_days INT           NULL,
    currency_code     VARCHAR(3)    NOT NULL,
    exchange_rate     DECIMAL(19,6) NOT NULL,
    description       VARCHAR(500)  NULL,
    amount            DECIMAL(19,4) NOT NULL,
    discount_amount   DECIMAL(19,4) NOT NULL,
    tax_amount        DECIMAL(19,4) NOT NULL,
    total_amount      DECIMAL(19,4) NOT NULL,
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
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id),
    CONSTRAINT uk_purchase_orders_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_purchase_orders_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_orders_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_purchase_orders_suppliers FOREIGN KEY (supplier_id) REFERENCES partners (id),
    CONSTRAINT fk_purchase_orders_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED',
        'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_purchase_orders_amounts CHECK (amount >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND total_amount >= 0 AND exchange_rate > 0),
    INDEX idx_purchase_orders_list (company_id, status, document_date),
    INDEX idx_purchase_orders_supplier (supplier_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order_lines (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    order_id            BIGINT        NOT NULL,
    line_no             INT           NOT NULL,
    product_id          BIGINT        NOT NULL,
    unit_id             BIGINT        NOT NULL,
    conversion_factor   DECIMAL(19,6) NOT NULL,
    quantity            DECIMAL(19,3) NOT NULL,
    base_quantity       DECIMAL(19,3) NOT NULL,
    unit_price          DECIMAL(19,4) NOT NULL,
    discount_percent    DECIMAL(9,4)  NOT NULL DEFAULT 0,
    discount_amount     DECIMAL(19,4) NOT NULL DEFAULT 0,
    amount              DECIMAL(19,4) NOT NULL,
    tax_rate_id         BIGINT        NULL,
    tax_rate            DECIMAL(7,4)  NULL,
    tax_amount          DECIMAL(19,4) NOT NULL DEFAULT 0,
    expected_date       DATE          NULL,
    requisition_line_id BIGINT        NULL,
    received_quantity   DECIMAL(19,3) NOT NULL DEFAULT 0,
    stocked_quantity    DECIMAL(19,3) NOT NULL DEFAULT 0,
    rejected_quantity   DECIMAL(19,3) NOT NULL DEFAULT 0,
    invoiced_quantity   DECIMAL(19,3) NOT NULL DEFAULT 0,
    closed_quantity     DECIMAL(19,3) NOT NULL DEFAULT 0,
    note                VARCHAR(500)  NULL,
    CONSTRAINT pk_purchase_order_lines PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_lines_no UNIQUE (order_id, line_no),
    CONSTRAINT fk_purchase_order_lines_orders FOREIGN KEY (order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_order_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_order_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_purchase_order_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT fk_purchase_order_lines_requisition_lines FOREIGN KEY (requisition_line_id)
        REFERENCES purchase_requisition_lines (id),
    CONSTRAINT ck_purchase_order_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND conversion_factor > 0
        AND unit_price >= 0 AND discount_percent BETWEEN 0 AND 100 AND discount_amount >= 0 AND amount >= 0
        AND tax_amount >= 0 AND received_quantity >= 0 AND stocked_quantity >= 0 AND rejected_quantity >= 0
        AND invoiced_quantity >= 0 AND closed_quantity >= 0),
    INDEX idx_purchase_order_lines_product (product_id),
    INDEX idx_purchase_order_lines_requisition (requisition_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Đề nghị mua: duyệt khi có luồng (BR-PUR-01). Đơn mua: từ ngưỡng purchasing.order-approval-threshold phải duyệt.
INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('DNM', 'PURCHASING', 'Đề nghị mua hàng', 'CONFIGURED', FALSE, 300),
    ('DMH', 'PURCHASING', 'Đơn mua hàng',     'CONFIGURED', FALSE, 310);

-- Ngưỡng duyệt đơn mua (BR-PUR-04, D-04). 50.000.000 đồng là [Giả định].
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('purchasing.order-approval-threshold', 'PURCHASING', 'Ngưỡng duyệt đơn mua (đồng)',
     'BR-PUR-04. Đơn mua có tổng tiền (gồm thuế) từ ngưỡng trở lên phải duyệt theo luồng duyệt đã cấu hình; dưới '
     'ngưỡng tự duyệt khi gửi.', 'DECIMAL', '50000000', 0, 999999999999, NULL, FALSE, 300);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-requisition:view',    'PURCHASING', 'Xem đề nghị mua',                         300),
    ('purchase-requisition:create',  'PURCHASING', 'Lập, sửa, gửi duyệt, hủy đề nghị mua',    301),
    ('purchase-requisition:approve', 'PURCHASING', 'Duyệt, từ chối đề nghị mua',              302),
    ('purchase-requisition:close',   'PURCHASING', 'Đóng phần còn lại của đề nghị mua',       303),
    ('purchase-order:view',          'PURCHASING', 'Xem đơn mua hàng',                        310),
    ('purchase-order:create',        'PURCHASING', 'Lập, sửa, gửi duyệt, hủy đơn mua hàng',   311),
    ('purchase-order:approve',       'PURCHASING', 'Duyệt, từ chối đơn mua hàng',             312),
    ('purchase-order:close',         'PURCHASING', 'Đóng phần còn lại của đơn mua hàng',      313);

-- Gán quyền mặc định (docs/07 mục 2.8). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (p.code LIKE 'purchase-requisition:%'
        OR p.code LIKE 'purchase-order:%') AND (
    (r.code = 'PURCHASING_STAFF' AND p.code NOT LIKE '%:approve') OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND (p.code LIKE '%:view' OR p.code LIKE '%:approve')) OR
    (r.code IN ('SALES_MANAGER', 'SALES_STAFF', 'TENDER_STAFF', 'WAREHOUSE_MANAGER')
        AND p.code IN ('purchase-requisition:view', 'purchase-requisition:create', 'purchase-order:view')) OR
    (r.code IN ('WAREHOUSE_STAFF', 'CHIEF_ACCOUNTANT', 'AR_ACCOUNTANT', 'TAX_ACCOUNTANT', 'CASHIER')
        AND p.code = 'purchase-order:view'));
