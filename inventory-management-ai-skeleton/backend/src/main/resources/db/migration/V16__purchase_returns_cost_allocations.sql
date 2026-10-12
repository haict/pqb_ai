-- Giai đoạn 5D: trả hàng nhà cung cấp và giảm giá hàng mua (BR-PUR-14), chi phí mua hàng phân bổ (BR-PUR-12, 16).
-- Thiết kế: docs/03 mục 4.10h.

-- Số đã trả lại nhà cung cấp theo dòng phiếu nhận (tổng; phần thuộc hàng đã có hóa đơn tách riêng để tính số còn
-- chưa nhận hóa đơn = đã nhập - đã nhận hóa đơn - đã trả chưa có hóa đơn).
ALTER TABLE purchase_receipt_lines
    ADD COLUMN returned_quantity          DECIMAL(19,3) NOT NULL DEFAULT 0 AFTER invoiced_quantity,
    ADD COLUMN returned_invoiced_quantity DECIMAL(19,3) NOT NULL DEFAULT 0 AFTER returned_quantity,
    ADD CONSTRAINT ck_purchase_receipt_lines_returned CHECK (returned_quantity >= 0
        AND returned_invoiced_quantity >= 0 AND returned_invoiced_quantity <= returned_quantity);

-- Theo dòng hóa đơn: số đã trả, giá trị đã giảm (trả hàng + giảm giá, trước thuế), chi phí đã phân bổ (dòng dịch vụ).
ALTER TABLE purchase_invoice_lines
    ADD COLUMN returned_quantity DECIMAL(19,3) NOT NULL DEFAULT 0 AFTER promotional,
    ADD COLUMN adjusted_amount   DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER returned_quantity,
    ADD COLUMN allocated_amount  DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER adjusted_amount,
    ADD CONSTRAINT ck_purchase_invoice_lines_progress CHECK (returned_quantity >= 0 AND adjusted_amount >= 0
        AND allocated_amount >= 0 AND adjusted_amount + allocated_amount <= amount);

-- Trả hàng nhà cung cấp (THN) và giảm giá hàng mua (GGM) dùng chung một bảng; loại quyết định ảnh hưởng.
CREATE TABLE purchase_returns (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_type         VARCHAR(10)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    supplier_id           BIGINT        NOT NULL,
    purchase_invoice_id   BIGINT        NULL,
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
    CONSTRAINT pk_purchase_returns PRIMARY KEY (id),
    CONSTRAINT uk_purchase_returns_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_purchase_returns_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_returns_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_purchase_returns_suppliers FOREIGN KEY (supplier_id) REFERENCES partners (id),
    CONSTRAINT fk_purchase_returns_invoices FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices (id),
    CONSTRAINT fk_purchase_returns_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_purchase_returns_inventory_documents FOREIGN KEY (inventory_document_id)
        REFERENCES inventory_documents (id),
    CONSTRAINT ck_purchase_returns_type CHECK (document_type IN ('THN', 'GGM')),
    CONSTRAINT ck_purchase_returns_kind CHECK ((document_type = 'THN' AND warehouse_id IS NOT NULL)
        OR (document_type = 'GGM' AND purchase_invoice_id IS NOT NULL)),
    CONSTRAINT ck_purchase_returns_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_purchase_returns_amounts CHECK (amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    INDEX idx_purchase_returns_list (company_id, status, document_date),
    INDEX idx_purchase_returns_invoice (purchase_invoice_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng trả hàng: theo dòng phiếu nhận (đúng lô) và dòng hóa đơn (nếu hàng đã có hóa đơn); dòng giảm giá: theo dòng hóa
-- đơn, không có số lượng.
CREATE TABLE purchase_return_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    return_id         BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    receipt_line_id   BIGINT        NULL,
    invoice_line_id   BIGINT        NULL,
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
    CONSTRAINT fk_purchase_return_lines_receipt_lines FOREIGN KEY (receipt_line_id)
        REFERENCES purchase_receipt_lines (id),
    CONSTRAINT fk_purchase_return_lines_invoice_lines FOREIGN KEY (invoice_line_id)
        REFERENCES purchase_invoice_lines (id),
    CONSTRAINT fk_purchase_return_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_return_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_purchase_return_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_purchase_return_lines_values CHECK (amount >= 0 AND tax_amount >= 0
        AND (quantity IS NULL OR (quantity > 0 AND base_quantity > 0))),
    INDEX idx_purchase_return_lines_receipt_line (receipt_line_id),
    INDEX idx_purchase_return_lines_invoice_line (invoice_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_return_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_purchase_return_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_purchase_return_serials_lines FOREIGN KEY (line_id) REFERENCES purchase_return_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Phân bổ chi phí mua hàng (PBC): nguồn là dòng dịch vụ của hóa đơn mua đã ghi sổ, đích là dòng phiếu nhận đã nhập kho.
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
    closed_by         BIGINT        NULL,
    closed_at         DATETIME(3)   NULL,
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
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    allocation_id   BIGINT        NOT NULL,
    line_no         INT           NOT NULL,
    invoice_line_id BIGINT        NOT NULL,
    product_id      BIGINT        NOT NULL,
    amount          DECIMAL(19,4) NOT NULL,
    CONSTRAINT pk_purchase_cost_allocation_sources PRIMARY KEY (id),
    CONSTRAINT uk_purchase_cost_allocation_sources_no UNIQUE (allocation_id, line_no),
    CONSTRAINT fk_purchase_cost_allocation_sources_allocations FOREIGN KEY (allocation_id)
        REFERENCES purchase_cost_allocations (id),
    CONSTRAINT fk_purchase_cost_allocation_sources_invoice_lines FOREIGN KEY (invoice_line_id)
        REFERENCES purchase_invoice_lines (id),
    CONSTRAINT ck_purchase_cost_allocation_sources_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Đích phân bổ: cơ sở phân bổ (số lượng hoặc giá trị tạm tính) chụp tại lúc lập; số phân bổ làm tròn đến đồng, dòng
-- cuối nhận phần dư để tổng khớp.
CREATE TABLE purchase_cost_allocation_targets (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    allocation_id    BIGINT        NOT NULL,
    line_no          INT           NOT NULL,
    receipt_line_id  BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    base_quantity    DECIMAL(19,3) NOT NULL,
    basis_value      DECIMAL(19,4) NOT NULL,
    allocated_amount DECIMAL(19,4) NOT NULL,
    CONSTRAINT pk_purchase_cost_allocation_targets PRIMARY KEY (id),
    CONSTRAINT uk_purchase_cost_allocation_targets_no UNIQUE (allocation_id, line_no),
    CONSTRAINT fk_purchase_cost_allocation_targets_allocations FOREIGN KEY (allocation_id)
        REFERENCES purchase_cost_allocations (id),
    CONSTRAINT fk_purchase_cost_allocation_targets_receipt_lines FOREIGN KEY (receipt_line_id)
        REFERENCES purchase_receipt_lines (id),
    CONSTRAINT ck_purchase_cost_allocation_targets_values CHECK (base_quantity > 0 AND basis_value >= 0
        AND allocated_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('THN', 'PURCHASING', 'Trả hàng nhà cung cấp',      'CONFIGURED', FALSE, 340),
    ('GGM', 'PURCHASING', 'Giảm giá hàng mua',          'CONFIGURED', FALSE, 341),
    ('PBC', 'PURCHASING', 'Phân bổ chi phí mua hàng',   'CONFIGURED', FALSE, 342);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-return:view',             'PURCHASING', 'Xem trả hàng / giảm giá hàng mua',              340),
    ('purchase-return:create',           'PURCHASING', 'Lập, sửa, gửi duyệt, hủy trả hàng / giảm giá',  341),
    ('purchase-return:approve',          'PURCHASING', 'Duyệt, từ chối trả hàng / giảm giá hàng mua',   342),
    ('purchase-return:post',             'PURCHASING', 'Ghi sổ trả hàng / giảm giá hàng mua',           343),
    ('purchase-return:reverse',          'PURCHASING', 'Đảo trả hàng / giảm giá hàng mua',              344),
    ('purchase-cost-allocation:view',    'PURCHASING', 'Xem phân bổ chi phí mua hàng',                  345),
    ('purchase-cost-allocation:create',  'PURCHASING', 'Lập, sửa, gửi duyệt, hủy phân bổ chi phí mua',  346),
    ('purchase-cost-allocation:approve', 'PURCHASING', 'Duyệt, từ chối phân bổ chi phí mua',            347),
    ('purchase-cost-allocation:post',    'PURCHASING', 'Ghi sổ phân bổ chi phí mua',                    348),
    ('purchase-cost-allocation:reverse', 'PURCHASING', 'Đảo phân bổ chi phí mua',                       349);

-- Gán quyền mặc định (docs/07 mục 2.8). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p
    ON (p.code LIKE 'purchase-return:%' OR p.code LIKE 'purchase-cost-allocation:%') AND (
    r.code = 'CHIEF_ACCOUNTANT' OR
    (r.code IN ('AR_ACCOUNTANT', 'TAX_ACCOUNTANT') AND p.code IN ('purchase-return:view', 'purchase-return:create',
        'purchase-return:post', 'purchase-cost-allocation:view', 'purchase-cost-allocation:create',
        'purchase-cost-allocation:post')) OR
    (r.code = 'PURCHASING_STAFF' AND p.code IN ('purchase-return:view', 'purchase-return:create',
        'purchase-cost-allocation:view')) OR
    (r.code IN ('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF') AND p.code IN ('purchase-return:view',
        'purchase-return:post')) OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('purchase-return:view', 'purchase-return:approve',
        'purchase-cost-allocation:view', 'purchase-cost-allocation:approve')));
