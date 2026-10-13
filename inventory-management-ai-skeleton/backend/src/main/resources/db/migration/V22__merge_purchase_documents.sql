-- D-54: gộp chứng từ mua hàng. Đơn mua hàng (DMH) là chứng từ ghi sổ duy nhất: mang thông tin hóa đơn của nhà cung
-- cấp, lô / hạn dùng / serial theo dòng; ghi sổ = nhập kho (phiếu PNK liên kết) + công nợ phải trả + bút toán. Bỏ đề
-- nghị mua (DNM), phiếu nhận hàng (PNH), hóa đơn mua (HDM), dung sai nhận vượt, dung sai đơn giá. Trả hàng / giảm giá
-- (THN, GGM) và phân bổ chi phí (PBC) tham chiếu dòng đơn mua đã ghi sổ. Thiết kế: docs/03 mục 4.11.
-- Chưa có dữ liệu thật (chưa triển khai production): các bảng chứng từ mua được xóa và tạo lại.

DROP TABLE purchase_cost_allocation_targets;
DROP TABLE purchase_cost_allocation_sources;
DROP TABLE purchase_cost_allocations;
DROP TABLE purchase_return_serials;
DROP TABLE purchase_return_lines;
DROP TABLE purchase_returns;
DROP TABLE purchase_cost_entries;
DROP TABLE purchase_invoice_lines;
DROP TABLE purchase_invoices;
DROP TABLE purchase_receipt_serials;
DROP TABLE purchase_receipt_lines;
DROP TABLE purchase_receipts;
DROP TABLE purchase_order_lines;
DROP TABLE purchase_orders;
DROP TABLE purchase_requisition_lines;
DROP TABLE purchase_requisitions;

CREATE TABLE purchase_orders (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    supplier_id           BIGINT        NOT NULL,
    warehouse_id          BIGINT        NOT NULL,
    expected_date         DATE          NULL,
    payment_term_days     INT           NULL,
    currency_code         VARCHAR(3)    NOT NULL,
    exchange_rate         DECIMAL(19,6) NOT NULL,
    -- Hóa đơn của nhà cung cấp: đủ cả ký hiệu, số, ngày hoặc để trống (mua không hóa đơn).
    seller_tax_code       VARCHAR(20)   NULL,
    invoice_series        VARCHAR(20)   NULL,
    invoice_no            VARCHAR(30)   NULL,
    invoice_date          DATE          NULL,
    due_date              DATE          NULL,
    -- Khóa chống trùng (MST người bán, ký hiệu, số); NULL khi không có hóa đơn hoặc khi hủy / đảo để lập lại được.
    duplicate_key         VARCHAR(80)   NULL,
    description           VARCHAR(500)  NULL,
    amount                DECIMAL(19,4) NOT NULL,
    discount_amount       DECIMAL(19,4) NOT NULL,
    tax_amount            DECIMAL(19,4) NOT NULL,
    total_amount          DECIMAL(19,4) NOT NULL,
    inventory_document_id BIGINT        NULL,
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
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id),
    CONSTRAINT uk_purchase_orders_no UNIQUE (company_id, document_no),
    CONSTRAINT uk_purchase_orders_invoice UNIQUE (company_id, duplicate_key),
    CONSTRAINT fk_purchase_orders_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_orders_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_purchase_orders_suppliers FOREIGN KEY (supplier_id) REFERENCES partners (id),
    CONSTRAINT fk_purchase_orders_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_purchase_orders_inventory FOREIGN KEY (inventory_document_id) REFERENCES inventory_documents (id),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_purchase_orders_amounts CHECK (amount >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND total_amount >= 0 AND exchange_rate > 0),
    CONSTRAINT ck_purchase_orders_invoice CHECK ((invoice_series IS NULL AND invoice_no IS NULL AND invoice_date IS NULL)
        OR (seller_tax_code IS NOT NULL AND invoice_series IS NOT NULL AND invoice_no IS NOT NULL
            AND invoice_date IS NOT NULL)),
    CONSTRAINT ck_purchase_orders_dates CHECK (posting_date >= document_date),
    INDEX idx_purchase_orders_list (company_id, status, document_date),
    INDEX idx_purchase_orders_supplier (supplier_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng hàng hóa: một lô (hàng quản lý lô) và serial; nhập kho theo dòng. Dòng dịch vụ (service_line) không qua kho,
-- dùng làm nguồn phân bổ chi phí mua. Theo dõi sau ghi sổ: số đã trả NCC (đơn vị cơ sở), giá trị đã giảm (trả hàng +
-- giảm giá, trước thuế), chi phí đã phân bổ (dòng dịch vụ).
CREATE TABLE purchase_order_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    product_id        BIGINT        NOT NULL,
    unit_id           BIGINT        NOT NULL,
    conversion_factor DECIMAL(19,6) NOT NULL,
    quantity          DECIMAL(19,3) NOT NULL,
    base_quantity     DECIMAL(19,3) NOT NULL,
    unit_price        DECIMAL(19,4) NOT NULL,
    discount_percent  DECIMAL(9,4)  NOT NULL DEFAULT 0,
    discount_amount   DECIMAL(19,4) NOT NULL DEFAULT 0,
    amount            DECIMAL(19,4) NOT NULL,
    tax_rate_id       BIGINT        NULL,
    tax_rate          DECIMAL(7,4)  NULL,
    tax_amount        DECIMAL(19,4) NOT NULL DEFAULT 0,
    service_line      BOOLEAN       NOT NULL DEFAULT FALSE,
    location_id       BIGINT        NULL,
    lot_no            VARCHAR(50)   NULL,
    manufacture_date  DATE          NULL,
    expiry_date       DATE          NULL,
    returned_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    adjusted_amount   DECIMAL(19,4) NOT NULL DEFAULT 0,
    allocated_amount  DECIMAL(19,4) NOT NULL DEFAULT 0,
    note              VARCHAR(500)  NULL,
    CONSTRAINT pk_purchase_order_lines PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_lines_no UNIQUE (order_id, line_no),
    CONSTRAINT fk_purchase_order_lines_orders FOREIGN KEY (order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_order_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_order_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_purchase_order_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT fk_purchase_order_lines_locations FOREIGN KEY (location_id) REFERENCES warehouse_locations (id),
    CONSTRAINT ck_purchase_order_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND conversion_factor > 0
        AND unit_price >= 0 AND discount_percent BETWEEN 0 AND 100 AND discount_amount >= 0 AND amount >= 0
        AND tax_amount >= 0),
    CONSTRAINT ck_purchase_order_lines_progress CHECK (returned_quantity >= 0 AND returned_quantity <= base_quantity
        AND adjusted_amount >= 0 AND allocated_amount >= 0
        AND adjusted_amount + allocated_amount <= amount - discount_amount),
    INDEX idx_purchase_order_lines_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_purchase_order_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_purchase_order_serials_lines FOREIGN KEY (line_id) REFERENCES purchase_order_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Điều chỉnh giá trị nhập theo dòng đơn mua (chỉ INSERT, đảo bằng dòng âm): chi phí mua phân bổ (BR-PUR-12), giảm giá
-- hàng mua (BR-PUR-14). Giai đoạn 8 dùng để tính lại giá vốn.
CREATE TABLE purchase_cost_entries (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    order_line_id     BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    warehouse_id      BIGINT        NOT NULL,
    entry_type        VARCHAR(30)   NOT NULL,
    posting_date      DATE          NOT NULL,
    base_quantity     DECIMAL(19,3) NOT NULL,
    amount            DECIMAL(19,4) NOT NULL,
    reference_type    VARCHAR(30)   NOT NULL,
    reference_id      BIGINT        NOT NULL,
    reference_line_id BIGINT        NULL,
    reference_no      VARCHAR(50)   NOT NULL,
    reversal_of_id    BIGINT        NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    CONSTRAINT pk_purchase_cost_entries PRIMARY KEY (id),
    CONSTRAINT fk_purchase_cost_entries_order_lines FOREIGN KEY (order_line_id) REFERENCES purchase_order_lines (id),
    CONSTRAINT fk_purchase_cost_entries_reversal FOREIGN KEY (reversal_of_id) REFERENCES purchase_cost_entries (id),
    CONSTRAINT ck_purchase_cost_entries_type CHECK (entry_type IN ('LANDED_COST', 'DISCOUNT')),
    INDEX idx_purchase_cost_entries_line (order_line_id),
    INDEX idx_purchase_cost_entries_reference (reference_type, reference_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Trả hàng nhà cung cấp (THN) và giảm giá hàng mua (GGM) theo một đơn mua đã ghi sổ.
CREATE TABLE purchase_returns (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_type         VARCHAR(10)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    supplier_id           BIGINT        NOT NULL,
    purchase_order_id     BIGINT        NOT NULL,
    warehouse_id          BIGINT        NULL,
    inventory_document_id BIGINT        NULL,
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
    CONSTRAINT pk_purchase_returns PRIMARY KEY (id),
    CONSTRAINT uk_purchase_returns_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_purchase_returns_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_returns_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_purchase_returns_suppliers FOREIGN KEY (supplier_id) REFERENCES partners (id),
    CONSTRAINT fk_purchase_returns_orders FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_returns_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_purchase_returns_inventory_documents FOREIGN KEY (inventory_document_id)
        REFERENCES inventory_documents (id),
    CONSTRAINT ck_purchase_returns_type CHECK (document_type IN ('THN', 'GGM')),
    CONSTRAINT ck_purchase_returns_kind CHECK (document_type = 'GGM' OR warehouse_id IS NOT NULL),
    CONSTRAINT ck_purchase_returns_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_purchase_returns_amounts CHECK (amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    INDEX idx_purchase_returns_list (company_id, status, document_date),
    INDEX idx_purchase_returns_order (purchase_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng trả hàng: theo dòng đơn mua (đúng lô, serial); dòng giảm giá: theo dòng đơn mua, không có số lượng.
CREATE TABLE purchase_return_lines (
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
    quality_status    VARCHAR(20)   NULL,
    unit_price        DECIMAL(19,4) NULL,
    amount            DECIMAL(19,4) NOT NULL,
    tax_rate_id       BIGINT        NULL,
    tax_rate          DECIMAL(7,4)  NULL,
    tax_amount        DECIMAL(19,4) NOT NULL,
    description       VARCHAR(500)  NULL,
    CONSTRAINT pk_purchase_return_lines PRIMARY KEY (id),
    CONSTRAINT uk_purchase_return_lines_no UNIQUE (return_id, line_no),
    CONSTRAINT fk_purchase_return_lines_returns FOREIGN KEY (return_id) REFERENCES purchase_returns (id),
    CONSTRAINT fk_purchase_return_lines_order_lines FOREIGN KEY (order_line_id) REFERENCES purchase_order_lines (id),
    CONSTRAINT fk_purchase_return_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_return_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_purchase_return_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_purchase_return_lines_values CHECK (amount >= 0 AND tax_amount >= 0
        AND (quantity IS NULL OR (quantity > 0 AND base_quantity > 0))),
    INDEX idx_purchase_return_lines_order_line (order_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_return_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_purchase_return_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_purchase_return_serials_lines FOREIGN KEY (line_id) REFERENCES purchase_return_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Phân bổ chi phí mua hàng (PBC): nguồn là dòng dịch vụ của đơn mua đã ghi sổ, đích là dòng hàng hóa của đơn mua đã
-- ghi sổ.
CREATE TABLE purchase_cost_allocations (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    document_no       VARCHAR(50)   NOT NULL,
    document_date     DATE          NOT NULL,
    posting_date      DATE          NOT NULL,
    allocation_method VARCHAR(20)   NOT NULL,
    description       VARCHAR(500)  NULL,
    amount            DECIMAL(19,4) NOT NULL,
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
    CONSTRAINT pk_purchase_cost_allocations PRIMARY KEY (id),
    CONSTRAINT uk_purchase_cost_allocations_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_purchase_cost_allocations_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_cost_allocations_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT ck_purchase_cost_allocations_method CHECK (allocation_method IN ('VALUE', 'QUANTITY')),
    CONSTRAINT ck_purchase_cost_allocations_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED',
        'POSTED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_purchase_cost_allocations_amount CHECK (amount > 0),
    INDEX idx_purchase_cost_allocations_list (company_id, status, document_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_cost_allocation_sources (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    allocation_id BIGINT        NOT NULL,
    line_no       INT           NOT NULL,
    order_line_id BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,
    amount        DECIMAL(19,4) NOT NULL,
    CONSTRAINT pk_purchase_cost_allocation_sources PRIMARY KEY (id),
    CONSTRAINT uk_purchase_cost_allocation_sources_no UNIQUE (allocation_id, line_no),
    CONSTRAINT fk_purchase_cost_allocation_sources_allocations FOREIGN KEY (allocation_id)
        REFERENCES purchase_cost_allocations (id),
    CONSTRAINT fk_purchase_cost_allocation_sources_order_lines FOREIGN KEY (order_line_id)
        REFERENCES purchase_order_lines (id),
    CONSTRAINT ck_purchase_cost_allocation_sources_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Đích phân bổ: cơ sở phân bổ (số lượng hoặc giá trị nhập) chụp tại lúc lập; số phân bổ làm tròn đến đồng, dòng cuối
-- nhận phần dư để tổng khớp.
CREATE TABLE purchase_cost_allocation_targets (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    allocation_id    BIGINT        NOT NULL,
    line_no          INT           NOT NULL,
    order_line_id    BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    base_quantity    DECIMAL(19,3) NOT NULL,
    basis_value      DECIMAL(19,4) NOT NULL,
    allocated_amount DECIMAL(19,4) NOT NULL,
    CONSTRAINT pk_purchase_cost_allocation_targets PRIMARY KEY (id),
    CONSTRAINT uk_purchase_cost_allocation_targets_no UNIQUE (allocation_id, line_no),
    CONSTRAINT fk_purchase_cost_allocation_targets_allocations FOREIGN KEY (allocation_id)
        REFERENCES purchase_cost_allocations (id),
    CONSTRAINT fk_purchase_cost_allocation_targets_order_lines FOREIGN KEY (order_line_id)
        REFERENCES purchase_order_lines (id),
    CONSTRAINT ck_purchase_cost_allocation_targets_values CHECK (base_quantity > 0 AND basis_value >= 0
        AND allocated_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Luồng duyệt, yêu cầu duyệt của các chứng từ mua (bảng chứng từ đã tạo lại nên yêu cầu cũ không còn chứng từ).
DELETE a FROM approval_actions a JOIN approval_requests r ON r.id = a.request_id
WHERE r.document_type IN ('DNM', 'DMH', 'PNH', 'HDM', 'THN', 'GGM', 'PBC');
DELETE s FROM approval_request_steps s JOIN approval_requests r ON r.id = s.request_id
WHERE r.document_type IN ('DNM', 'DMH', 'PNH', 'HDM', 'THN', 'GGM', 'PBC');
DELETE FROM approval_requests WHERE document_type IN ('DNM', 'DMH', 'PNH', 'HDM', 'THN', 'GGM', 'PBC');
DELETE s FROM approval_workflow_steps s JOIN approval_workflows w ON w.id = s.workflow_id
WHERE w.document_type IN ('DNM', 'PNH', 'HDM');
DELETE FROM approval_workflows WHERE document_type IN ('DNM', 'PNH', 'HDM');
DELETE FROM approval_delegations WHERE document_type IN ('DNM', 'PNH', 'HDM');
DELETE FROM document_number_formats WHERE document_type IN ('DNM', 'PNH', 'HDM');

-- Quy tắc hạch toán và loại chứng từ đã bỏ: chỉ xóa khi chưa có bút toán / chứng từ kho tham chiếu (sổ phụ chỉ INSERT,
-- không xóa lịch sử).
DELETE FROM posting_rules WHERE document_type IN ('DNM', 'PNH', 'HDM')
    AND id NOT IN (SELECT DISTINCT posting_rule_id FROM journal_entry_lines WHERE posting_rule_id IS NOT NULL);
DELETE FROM sys_document_types WHERE code IN ('DNM', 'PNH', 'HDM')
    AND code NOT IN (SELECT document_type FROM posting_rules)
    AND code NOT IN (SELECT document_type FROM inventory_documents);

DELETE FROM sys_parameters WHERE param_key IN ('purchasing.receipt-over-tolerance-percent',
    'purchasing.price-tolerance-percent');
DELETE FROM sys_parameter_definitions WHERE param_key IN ('purchasing.receipt-over-tolerance-percent',
    'purchasing.price-tolerance-percent');

DELETE rp FROM sys_role_permissions rp JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code LIKE 'purchase-requisition:%' OR p.code LIKE 'purchase-receipt:%' OR p.code LIKE 'purchase-invoice:%'
   OR p.code = 'purchase-order:close';
DELETE FROM sys_permissions WHERE code LIKE 'purchase-requisition:%' OR code LIKE 'purchase-receipt:%'
   OR code LIKE 'purchase-invoice:%' OR code = 'purchase-order:close';

UPDATE sys_document_types SET name = 'Đơn mua hàng (kèm hóa đơn, nhập kho)' WHERE code = 'DMH';

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-order:post',    'PURCHASING', 'Ghi sổ đơn mua (nhập kho, công nợ phải trả, bút toán)', 313),
    ('purchase-order:reverse', 'PURCHASING', 'Đảo đơn mua đã ghi sổ',                                 314);

-- Gán quyền mặc định (docs/07 mục 2.8): ghi sổ cho kho và kế toán (trước đây nhập kho / ghi sổ hóa đơn). [Suy luận]
-- Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON (
    (r.code IN ('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'AR_ACCOUNTANT', 'TAX_ACCOUNTANT')
        AND p.code = 'purchase-order:post') OR
    (r.code IN ('CHIEF_ACCOUNTANT', 'WAREHOUSE_MANAGER')
        AND p.code IN ('purchase-order:post', 'purchase-order:reverse')))
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);
