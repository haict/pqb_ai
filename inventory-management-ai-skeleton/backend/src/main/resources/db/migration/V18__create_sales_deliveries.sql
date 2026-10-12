-- Giai đoạn 6C: phiếu giao hàng theo đơn bán (GH): xuất kho theo đơn (BR-SAL-21, FEFO), giao hàng một hoặc nhiều lần
-- (BR-SAL-20), biên bản giao nhận (BR-SAL-22), trạng thái giao (BR-SAL-23). Thiết kế: docs/03 mục 4.12a.

CREATE TABLE sales_deliveries (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    order_id              BIGINT        NOT NULL,
    customer_id           BIGINT        NOT NULL,
    warehouse_id          BIGINT        NOT NULL,
    delivery_address      VARCHAR(500)  NULL,
    receiver_name         VARCHAR(200)  NULL,
    receiver_phone        VARCHAR(50)   NULL,
    shipper_name          VARCHAR(200)  NULL,
    sales_user_id         BIGINT        NULL,
    source_return_id      BIGINT        NULL,
    description           VARCHAR(500)  NULL,
    delivery_status       VARCHAR(20)   NOT NULL,
    inventory_document_id BIGINT        NULL,
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
    CONSTRAINT pk_sales_deliveries PRIMARY KEY (id),
    CONSTRAINT uk_sales_deliveries_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_sales_deliveries_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_sales_deliveries_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_deliveries_orders FOREIGN KEY (order_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_sales_deliveries_customers FOREIGN KEY (customer_id) REFERENCES partners (id),
    CONSTRAINT fk_sales_deliveries_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_sales_deliveries_inventory_documents FOREIGN KEY (inventory_document_id)
        REFERENCES inventory_documents (id),
    CONSTRAINT ck_sales_deliveries_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'REJECTED', 'APPROVED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_sales_deliveries_delivery_status CHECK (delivery_status IN ('PENDING', 'IN_TRANSIT', 'DELIVERED',
        'FAILED')),
    INDEX idx_sales_deliveries_list (company_id, status, document_date),
    INDEX idx_sales_deliveries_order (order_id),
    INDEX idx_sales_deliveries_customer (customer_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng giao: theo dòng đơn bán; lô chỉ định (trống = FEFO); số đã xuất hóa đơn, đã trả lại (đơn vị cơ sở).
CREATE TABLE sales_delivery_lines (
    id                         BIGINT        NOT NULL AUTO_INCREMENT,
    delivery_id                BIGINT        NOT NULL,
    line_no                    INT           NOT NULL,
    order_line_id              BIGINT        NOT NULL,
    product_id                 BIGINT        NOT NULL,
    unit_id                    BIGINT        NOT NULL,
    conversion_factor          DECIMAL(19,6) NOT NULL,
    quantity                   DECIMAL(19,3) NOT NULL,
    base_quantity              DECIMAL(19,3) NOT NULL,
    lot_no                     VARCHAR(50)   NULL,
    lot_override_reason        VARCHAR(500)  NULL,
    invoiced_quantity          DECIMAL(19,3) NOT NULL DEFAULT 0,
    returned_quantity          DECIMAL(19,3) NOT NULL DEFAULT 0,
    returned_invoiced_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    note                       VARCHAR(500)  NULL,
    CONSTRAINT pk_sales_delivery_lines PRIMARY KEY (id),
    CONSTRAINT uk_sales_delivery_lines_no UNIQUE (delivery_id, line_no),
    CONSTRAINT fk_sales_delivery_lines_deliveries FOREIGN KEY (delivery_id) REFERENCES sales_deliveries (id),
    CONSTRAINT fk_sales_delivery_lines_order_lines FOREIGN KEY (order_line_id) REFERENCES sales_order_lines (id),
    CONSTRAINT fk_sales_delivery_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sales_delivery_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT ck_sales_delivery_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND invoiced_quantity >= 0
        AND returned_quantity >= 0 AND returned_invoiced_quantity >= 0
        AND returned_invoiced_quantity <= returned_quantity),
    INDEX idx_sales_delivery_lines_order_line (order_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_delivery_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_sales_delivery_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_sales_delivery_serials_lines FOREIGN KEY (line_id) REFERENCES sales_delivery_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hàng thực xuất theo dòng (lô, hạn dùng, serial, trạng thái) ghi khi xuất kho: biên bản giao nhận, trả hàng đúng lô.
CREATE TABLE sales_delivery_items (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    line_id        BIGINT        NOT NULL,
    lot_id         BIGINT        NULL,
    lot_no         VARCHAR(50)   NULL,
    expiry_date    DATE          NULL,
    serial_no      VARCHAR(100)  NULL,
    quality_status VARCHAR(20)   NOT NULL,
    base_quantity  DECIMAL(19,3) NOT NULL,
    CONSTRAINT pk_sales_delivery_items PRIMARY KEY (id),
    CONSTRAINT fk_sales_delivery_items_lines FOREIGN KEY (line_id) REFERENCES sales_delivery_lines (id),
    CONSTRAINT ck_sales_delivery_items_quantity CHECK (base_quantity > 0),
    INDEX idx_sales_delivery_items_line (line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('GH', 'SALES', 'Phiếu giao hàng (xuất kho theo đơn bán)', 'CONFIGURED', FALSE, 420);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('sales-delivery:view',    'SALES', 'Xem phiếu giao hàng',                                420),
    ('sales-delivery:create',  'SALES', 'Lập, sửa, gửi duyệt, hủy phiếu giao hàng',          421),
    ('sales-delivery:approve', 'SALES', 'Duyệt phiếu giao hàng (khi cấu hình luồng)',        422),
    ('sales-delivery:post',    'SALES', 'Xuất kho theo phiếu giao hàng',                     423),
    ('sales-delivery:deliver', 'SALES', 'Cập nhật giao hàng: đang giao, khách nhận, thất bại', 424),
    ('sales-delivery:reverse', 'SALES', 'Đảo phiếu giao hàng (hoàn tồn)',                   425);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code LIKE 'sales-delivery:%' AND (
    (r.code IN ('WAREHOUSE_MANAGER') AND p.code <> 'sales-delivery:approve') OR
    (r.code = 'WAREHOUSE_STAFF' AND p.code IN ('sales-delivery:view', 'sales-delivery:create', 'sales-delivery:post',
        'sales-delivery:deliver')) OR
    (r.code IN ('SALES_MANAGER', 'SALES_STAFF') AND p.code IN ('sales-delivery:view', 'sales-delivery:create',
        'sales-delivery:deliver')) OR
    (r.code = 'DELIVERY_STAFF' AND p.code IN ('sales-delivery:view', 'sales-delivery:deliver')) OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('sales-delivery:view', 'sales-delivery:approve')) OR
    (r.code = 'CHIEF_ACCOUNTANT' AND p.code IN ('sales-delivery:view', 'sales-delivery:reverse')) OR
    (r.code IN ('AR_ACCOUNTANT', 'TAX_ACCOUNTANT') AND p.code = 'sales-delivery:view'));
