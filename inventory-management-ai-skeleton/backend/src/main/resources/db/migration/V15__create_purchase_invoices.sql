-- Giai đoạn 5C: hóa đơn mua (BR-PUR-09), hàng về trước hóa đơn về sau (BR-PUR-10), đối chiếu ba chiều (BR-PUR-11),
-- mua dịch vụ (BR-PUR-16), hàng khuyến mại (BR-PUR-17). Thiết kế: docs/03 mục 4.10g.

ALTER TABLE purchase_receipt_lines
    ADD COLUMN invoiced_quantity DECIMAL(19,3) NOT NULL DEFAULT 0 AFTER unit_cost,
    ADD CONSTRAINT ck_purchase_receipt_lines_invoiced CHECK (invoiced_quantity >= 0);

CREATE TABLE purchase_invoices (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    document_no       VARCHAR(50)   NOT NULL,
    document_date     DATE          NOT NULL,
    posting_date      DATE          NOT NULL,
    supplier_id       BIGINT        NOT NULL,
    seller_tax_code   VARCHAR(20)   NOT NULL,
    invoice_series    VARCHAR(20)   NOT NULL,
    invoice_no        VARCHAR(30)   NOT NULL,
    invoice_date      DATE          NOT NULL,
    due_date          DATE          NOT NULL,
    -- Khóa chống trùng (MST người bán, ký hiệu, số); NULL khi hủy / đảo để lập lại được.
    duplicate_key     VARCHAR(80)   NULL,
    description       VARCHAR(500)  NULL,
    amount            DECIMAL(19,4) NOT NULL,
    tax_amount        DECIMAL(19,4) NOT NULL,
    total_amount      DECIMAL(19,4) NOT NULL,
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
    CONSTRAINT pk_purchase_invoices PRIMARY KEY (id),
    CONSTRAINT uk_purchase_invoices_no UNIQUE (company_id, document_no),
    CONSTRAINT uk_purchase_invoices_duplicate UNIQUE (company_id, duplicate_key),
    CONSTRAINT fk_purchase_invoices_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_purchase_invoices_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_purchase_invoices_suppliers FOREIGN KEY (supplier_id) REFERENCES partners (id),
    CONSTRAINT ck_purchase_invoices_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
        'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_purchase_invoices_amounts CHECK (amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    CONSTRAINT ck_purchase_invoices_dates CHECK (due_date >= invoice_date),
    INDEX idx_purchase_invoices_list (company_id, status, document_date),
    INDEX idx_purchase_invoices_supplier (supplier_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dòng hàng hóa tham chiếu dòng phiếu nhận hàng đã nhập kho; dòng dịch vụ không qua kho (có thể theo dòng đơn mua).
CREATE TABLE purchase_invoice_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_id        BIGINT        NOT NULL,
    line_no           INT           NOT NULL,
    receipt_line_id   BIGINT        NULL,
    order_line_id     BIGINT        NULL,
    product_id        BIGINT        NOT NULL,
    unit_id           BIGINT        NOT NULL,
    conversion_factor DECIMAL(19,6) NOT NULL,
    quantity          DECIMAL(19,3) NOT NULL,
    base_quantity     DECIMAL(19,3) NOT NULL,
    unit_price        DECIMAL(19,4) NOT NULL,
    amount            DECIMAL(19,4) NOT NULL,
    tax_rate_id       BIGINT        NULL,
    tax_rate          DECIMAL(7,4)  NULL,
    tax_amount        DECIMAL(19,4) NOT NULL,
    promotional       BOOLEAN       NOT NULL DEFAULT FALSE,
    description       VARCHAR(500)  NULL,
    CONSTRAINT pk_purchase_invoice_lines PRIMARY KEY (id),
    CONSTRAINT uk_purchase_invoice_lines_no UNIQUE (invoice_id, line_no),
    CONSTRAINT fk_purchase_invoice_lines_invoices FOREIGN KEY (invoice_id) REFERENCES purchase_invoices (id),
    CONSTRAINT fk_purchase_invoice_lines_receipt_lines FOREIGN KEY (receipt_line_id)
        REFERENCES purchase_receipt_lines (id),
    CONSTRAINT fk_purchase_invoice_lines_order_lines FOREIGN KEY (order_line_id) REFERENCES purchase_order_lines (id),
    CONSTRAINT fk_purchase_invoice_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_invoice_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_purchase_invoice_lines_tax_rates FOREIGN KEY (tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_purchase_invoice_lines_values CHECK (quantity > 0 AND base_quantity > 0 AND conversion_factor > 0
        AND unit_price >= 0 AND amount >= 0 AND tax_amount >= 0),
    INDEX idx_purchase_invoice_lines_receipt_line (receipt_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Điều chỉnh giá trị nhập theo dòng phiếu nhận hàng (chỉ INSERT): chênh lệch giá hóa đơn so với giá tạm tính
-- (BR-PUR-10), chi phí mua phân bổ (BR-PUR-12, Giai đoạn 5D). Giai đoạn 8 dùng để tính lại giá vốn.
CREATE TABLE purchase_cost_entries (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    receipt_line_id  BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    warehouse_id     BIGINT        NOT NULL,
    entry_type       VARCHAR(30)   NOT NULL,
    posting_date     DATE          NOT NULL,
    base_quantity    DECIMAL(19,3) NOT NULL,
    amount           DECIMAL(19,4) NOT NULL,
    reference_type   VARCHAR(30)   NOT NULL,
    reference_id     BIGINT        NOT NULL,
    reference_line_id BIGINT       NULL,
    reference_no     VARCHAR(50)   NOT NULL,
    reversal_of_id   BIGINT        NULL,
    created_by       BIGINT        NULL,
    created_at       DATETIME(3)   NOT NULL,
    CONSTRAINT pk_purchase_cost_entries PRIMARY KEY (id),
    CONSTRAINT fk_purchase_cost_entries_receipt_lines FOREIGN KEY (receipt_line_id)
        REFERENCES purchase_receipt_lines (id),
    CONSTRAINT fk_purchase_cost_entries_reversal FOREIGN KEY (reversal_of_id) REFERENCES purchase_cost_entries (id),
    CONSTRAINT ck_purchase_cost_entries_type CHECK (entry_type IN ('INVOICE_PRICE', 'LANDED_COST', 'DISCOUNT')),
    INDEX idx_purchase_cost_entries_line (receipt_line_id),
    INDEX idx_purchase_cost_entries_reference (reference_type, reference_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('HDM', 'PURCHASING', 'Hóa đơn mua hàng', 'CONFIGURED', FALSE, 330);

-- Dung sai đơn giá hóa đơn so với đơn mua (BR-PUR-11): vượt thì cảnh báo. 0% là [Giả định].
INSERT INTO sys_parameter_definitions
    (param_key, module, name, description, value_type, default_value, min_value, max_value, allowed_values,
     lock_after_posting, sort_order)
VALUES
    ('purchasing.price-tolerance-percent', 'PURCHASING', 'Dung sai đơn giá hóa đơn mua (%)',
     'BR-PUR-11. Đơn giá hóa đơn lệch đơn giá đơn mua (sau chiết khấu) quá dung sai thì cảnh báo khi đối chiếu ba '
     'chiều.', 'DECIMAL', '0', 0, 100, NULL, FALSE, 320);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('purchase-invoice:view',    'PURCHASING', 'Xem hóa đơn mua',                       330),
    ('purchase-invoice:create',  'PURCHASING', 'Lập, sửa, gửi duyệt, hủy hóa đơn mua',  331),
    ('purchase-invoice:approve', 'PURCHASING', 'Duyệt, từ chối hóa đơn mua',            332),
    ('purchase-invoice:post',    'PURCHASING', 'Ghi sổ hóa đơn mua (công nợ phải trả)', 333),
    ('purchase-invoice:reverse', 'PURCHASING', 'Đảo hóa đơn mua',                       334);

-- Gán quyền mặc định (docs/07 mục 2.8). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.code LIKE 'purchase-invoice:%' AND (
    r.code = 'CHIEF_ACCOUNTANT' OR
    (r.code IN ('AR_ACCOUNTANT', 'TAX_ACCOUNTANT')
        AND p.code IN ('purchase-invoice:view', 'purchase-invoice:create', 'purchase-invoice:post')) OR
    (r.code = 'PURCHASING_STAFF' AND p.code IN ('purchase-invoice:view', 'purchase-invoice:create')) OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.code IN ('purchase-invoice:view', 'purchase-invoice:approve')) OR
    (r.code = 'CASHIER' AND p.code = 'purchase-invoice:view'));

-- Dòng đơn mua hàng dịch vụ (không quản lý tồn, BR-PUR-16): hoàn thành theo số đã nhận hóa đơn thay vì đã nhập kho.
ALTER TABLE purchase_order_lines
    ADD COLUMN service_line BOOLEAN NOT NULL DEFAULT FALSE AFTER requisition_line_id;
