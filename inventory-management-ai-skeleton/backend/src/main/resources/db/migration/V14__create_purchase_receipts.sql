-- Giai đoạn 5B: phiếu nhận hàng (BR-PUR-07), nhập kho theo phiếu nhận hàng (BR-PUR-08, D-36). Thiết kế: docs/03 mục
-- 4.10f. Phiếu nhập kho mua hàng vẫn là chứng từ PNK của module kho, liên kết chứng từ nguồn (ADR-15).

ALTER TABLE inventory_documents
    ADD COLUMN source_type VARCHAR(30) NULL AFTER issue_policy_id,
    ADD COLUMN source_id   BIGINT      NULL AFTER source_type,
    ADD COLUMN source_no   VARCHAR(50) NULL AFTER source_id,
    ADD INDEX idx_inventory_documents_source (source_type, source_id);

ALTER TABLE inventory_document_lines
    ADD COLUMN source_line_id BIGINT NULL AFTER note;

CREATE TABLE purchase_receipts (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    document_no           VARCHAR(50)   NOT NULL,
    document_date         DATE          NOT NULL,
    posting_date          DATE          NOT NULL,
    purchase_order_id     BIGINT        NOT NULL,
    supplier_id           BIGINT        NOT NULL,
    warehouse_id          BIGINT        NOT NULL,
    delivery_note_no      VARCHAR(50)   NULL,
    description           VARCHAR(500)  NULL,
    over_tolerance        BOOLEAN       NOT NULL DEFAULT FALSE,
    inventory_document_id BIGINT        NULL,
    stocked_by            BIGINT        NULL,
    stocked_at            DATETIME(3)   NULL,
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
    CONSTRAINT pk_purchase_receipts PRIMARY KEY (id),
    CONSTRAINT uk_purchase_receipts_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_purchase_receipts_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_receipts_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_purchase_receipts_orders FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_receipts_suppliers FOREIGN KEY (supplier_id) REFERENCES partners (id),
    CONSTRAINT fk_purchase_receipts_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_purchase_receipts_inventory FOREIGN KEY (inventory_document_id) REFERENCES inventory_documents (id),
    CONSTRAINT ck_purchase_receipts_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    INDEX idx_purchase_receipts_list (company_id, status, document_date),
    INDEX idx_purchase_receipts_order (purchase_order_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Mỗi dòng một kết quả kiểm nhận: ACCEPTED (nhập kho), QUARANTINE (lỗi, nhập khu cách ly), RETURN (lỗi, không nhập,
-- trả NCC). Nhiều dòng có thể cùng một dòng đơn mua (nhiều lô / nhiều kết quả).
CREATE TABLE purchase_receipt_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    receipt_id        BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    order_line_id     BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    unit_id           BIGINT        NOT NULL,
    conversion_factor DECIMAL(19,6) NOT NULL,
    quantity          DECIMAL(19,3) NOT NULL,
    base_quantity     DECIMAL(19,3) NOT NULL,
    inspection_result VARCHAR(20)   NOT NULL,
    reject_reason     VARCHAR(500)  NULL,
    location_id       BIGINT        NULL,
    lot_no            VARCHAR(50)   NULL,
    manufacture_date  DATE          NULL,
    expiry_date       DATE          NULL,
    unit_cost         DECIMAL(19,4) NOT NULL,
    note              VARCHAR(500)  NULL,
    CONSTRAINT pk_purchase_receipt_lines PRIMARY KEY (id),
    CONSTRAINT uk_purchase_receipt_lines_no UNIQUE (receipt_id, line_no),
    CONSTRAINT fk_purchase_receipt_lines_receipts FOREIGN KEY (receipt_id) REFERENCES purchase_receipts (id),
    CONSTRAINT fk_purchase_receipt_lines_order_lines FOREIGN KEY (order_line_id) REFERENCES purchase_order_lines (id),
    CONSTRAINT fk_purchase_receipt_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_receipt_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_purchase_receipt_lines_locations FOREIGN KEY (location_id) REFERENCES warehouse_locations (id),
    CONSTRAINT ck_purchase_receipt_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND conversion_factor > 0
        AND unit_cost >= 0),
    CONSTRAINT ck_purchase_receipt_lines_result CHECK (inspection_result IN ('ACCEPTED', 'QUARANTINE', 'RETURN')),
    INDEX idx_purchase_receipt_lines_order_line (order_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_receipt_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_purchase_receipt_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_purchase_receipt_serials_lines FOREIGN KEY (line_id) REFERENCES purchase_receipt_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Phiếu nhận hàng: chỉ phải duyệt khi nhận vượt số đặt quá dung sai (BR-PUR-07).
INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('PNH', 'PURCHASING', 'Phiếu nhận hàng', 'CONFIGURED', FALSE, 320);

-- Dung sai nhận vượt (% số đặt của dòng đơn mua). 0% là [Giả định].
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('purchasing.receipt-over-tolerance-percent', 'PURCHASING', 'Dung sai nhận vượt số đặt (%)',
     'BR-PUR-07. Tổng đã nhận của dòng đơn mua vượt số đặt × (1 + dung sai) thì phiếu nhận hàng phải duyệt theo luồng '
     'duyệt đã cấu hình.', 'DECIMAL', '0', 0, 100, NULL, FALSE, 310);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-receipt:view',    'PURCHASING', 'Xem phiếu nhận hàng',                              320),
    ('purchase-receipt:create',  'PURCHASING', 'Lập, sửa, gửi duyệt, hủy phiếu nhận hàng',         321),
    ('purchase-receipt:approve', 'PURCHASING', 'Duyệt nhận vượt dung sai',                         322),
    ('purchase-receipt:post',    'PURCHASING', 'Xác nhận nhận hàng, nhập kho theo phiếu nhận hàng', 323),
    ('purchase-receipt:reverse', 'PURCHASING', 'Đảo phiếu nhận hàng (kể cả phiếu nhập kho)',       324);

-- Gán quyền mặc định (docs/07 mục 2.8). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code LIKE 'purchase-receipt:%' AND (
    (r.code IN ('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')
        AND p.code IN ('purchase-receipt:view', 'purchase-receipt:create', 'purchase-receipt:post')) OR
    (r.code = 'PURCHASING_STAFF' AND p.code IN ('purchase-receipt:view', 'purchase-receipt:create')) OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('purchase-receipt:view', 'purchase-receipt:approve')) OR
    (r.code = 'WAREHOUSE_MANAGER' AND p.code IN ('purchase-receipt:approve', 'purchase-receipt:reverse')) OR
    (r.code = 'CHIEF_ACCOUNTANT' AND p.code IN ('purchase-receipt:view', 'purchase-receipt:reverse')) OR
    (r.code IN ('AR_ACCOUNTANT', 'TAX_ACCOUNTANT') AND p.code = 'purchase-receipt:view'));
