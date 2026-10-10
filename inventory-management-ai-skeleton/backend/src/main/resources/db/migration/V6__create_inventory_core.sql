-- Giai đoạn 3: lõi kho M04 (BR-INV-01..42, BR-GEN-20..31). Thiết kế: docs/03 mục 4.1-4.6, 4.28-4.33.
-- Sổ nhật ký kho (inventory_transactions) chỉ INSERT; số dư (inventory_balances, inventory_stock_summaries) là bản
-- đọc nhanh, tái tạo được từ sổ nhật ký và chỉ InventoryPostingService được cập nhật.

CREATE TABLE inventory_lots (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_id       BIGINT       NOT NULL,
    product_id       BIGINT       NOT NULL,
    lot_no           VARCHAR(50)  NOT NULL,
    manufacture_date DATE         NULL,
    expiry_date      DATE         NULL,
    is_temporary     BOOLEAN      NOT NULL DEFAULT FALSE,
    blocked          BOOLEAN      NOT NULL DEFAULT FALSE,
    block_reason     VARCHAR(500) NULL,
    created_by       BIGINT       NULL,
    created_at       DATETIME(3)  NOT NULL,
    updated_by       BIGINT       NULL,
    updated_at       DATETIME(3)  NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_lots PRIMARY KEY (id),
    CONSTRAINT uk_inventory_lots_product_lot UNIQUE (product_id, lot_no),
    CONSTRAINT fk_inventory_lots_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_inventory_lots_products FOREIGN KEY (product_id) REFERENCES products (id),
    INDEX idx_inventory_lots_expiry (company_id, expiry_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE inventory_serials (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    company_id           BIGINT       NOT NULL,
    product_id           BIGINT       NOT NULL,
    serial_no            VARCHAR(100) NOT NULL,
    lot_id               BIGINT       NULL,
    lifecycle_status     VARCHAR(30)  NOT NULL,
    current_warehouse_id BIGINT       NULL,
    blocked              BOOLEAN      NOT NULL DEFAULT FALSE,
    block_reason         VARCHAR(500) NULL,
    created_by           BIGINT       NULL,
    created_at           DATETIME(3)  NOT NULL,
    updated_by           BIGINT       NULL,
    updated_at           DATETIME(3)  NOT NULL,
    version              INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_serials PRIMARY KEY (id),
    CONSTRAINT uk_inventory_serials_product_serial UNIQUE (product_id, serial_no),
    CONSTRAINT fk_inventory_serials_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_inventory_serials_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_serials_lots FOREIGN KEY (lot_id) REFERENCES inventory_lots (id),
    CONSTRAINT fk_inventory_serials_warehouses FOREIGN KEY (current_warehouse_id) REFERENCES warehouses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Sổ nhật ký kho, bất biến (BR-INV 9.1). Mỗi dòng tham chiếu chứng từ và dòng chứng từ nguồn.
CREATE TABLE inventory_transactions (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    branch_id         BIGINT        NOT NULL,
    posting_date      DATE          NOT NULL,
    posted_at         DATETIME(3)   NOT NULL,
    product_id        BIGINT        NOT NULL,
    warehouse_id      BIGINT        NOT NULL,
    location_id       BIGINT        NOT NULL,
    lot_id            BIGINT        NULL,
    serial_id         BIGINT        NULL,
    quality_status    VARCHAR(30)   NOT NULL,
    transaction_type  VARCHAR(30)   NOT NULL,
    direction         VARCHAR(3)    NOT NULL,
    quantity          DECIMAL(19,3) NOT NULL,
    unit_cost         DECIMAL(19,4) NULL,
    amount            DECIMAL(19,4) NULL,
    partner_id        BIGINT        NULL,
    reference_type    VARCHAR(30)   NOT NULL,
    reference_id      BIGINT        NOT NULL,
    reference_line_id BIGINT        NULL,
    reference_no      VARCHAR(50)   NOT NULL,
    reversal_of_id    BIGINT        NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    CONSTRAINT pk_inventory_transactions PRIMARY KEY (id),
    CONSTRAINT fk_inventory_transactions_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_inventory_transactions_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_inventory_transactions_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_transactions_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_transactions_locations FOREIGN KEY (location_id) REFERENCES warehouse_locations (id),
    CONSTRAINT fk_inventory_transactions_lots FOREIGN KEY (lot_id) REFERENCES inventory_lots (id),
    CONSTRAINT fk_inventory_transactions_serials FOREIGN KEY (serial_id) REFERENCES inventory_serials (id),
    CONSTRAINT fk_inventory_transactions_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_inventory_transactions_reversal FOREIGN KEY (reversal_of_id) REFERENCES inventory_transactions (id),
    CONSTRAINT ck_inventory_transactions_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_transactions_direction CHECK (direction IN ('IN', 'OUT')),
    INDEX idx_inventory_transactions_product (product_id, warehouse_id, posting_date),
    INDEX idx_inventory_transactions_reference (reference_type, reference_id),
    INDEX idx_inventory_transactions_lot (lot_id),
    INDEX idx_inventory_transactions_serial (serial_id),
    INDEX idx_inventory_transactions_company_date (company_id, posting_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tồn thực tế chi tiết theo (hàng, kho, vị trí, lô, serial, trạng thái chất lượng). Không bao giờ âm (BR-GEN-31).
CREATE TABLE inventory_balances (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    warehouse_id     BIGINT        NOT NULL,
    location_id      BIGINT        NOT NULL,
    lot_id           BIGINT        NULL,
    serial_id        BIGINT        NULL,
    lot_key          BIGINT GENERATED ALWAYS AS (IFNULL(lot_id, 0)) STORED,
    serial_key       BIGINT GENERATED ALWAYS AS (IFNULL(serial_id, 0)) STORED,
    quality_status   VARCHAR(30)   NOT NULL,
    on_hand_quantity DECIMAL(19,3) NOT NULL,
    version          INT           NOT NULL DEFAULT 0,
    updated_at       DATETIME(3)   NOT NULL,
    CONSTRAINT pk_inventory_balances PRIMARY KEY (id),
    CONSTRAINT uk_inventory_balances_key
        UNIQUE (product_id, warehouse_id, location_id, lot_key, serial_key, quality_status),
    CONSTRAINT fk_inventory_balances_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_inventory_balances_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_balances_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_balances_locations FOREIGN KEY (location_id) REFERENCES warehouse_locations (id),
    CONSTRAINT fk_inventory_balances_lots FOREIGN KEY (lot_id) REFERENCES inventory_lots (id),
    CONSTRAINT fk_inventory_balances_serials FOREIGN KEY (serial_id) REFERENCES inventory_serials (id),
    CONSTRAINT ck_inventory_balances_non_negative CHECK (on_hand_quantity >= 0),
    INDEX idx_inventory_balances_warehouse (warehouse_id, product_id),
    INDEX idx_inventory_balances_lot (lot_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tổng theo (hàng, kho): tồn thực tế, giữ chỗ, đang về. Là dòng khóa đầu tiên khi ghi sổ (thứ tự khóa cố định).
CREATE TABLE inventory_stock_summaries (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    warehouse_id      BIGINT        NOT NULL,
    on_hand_quantity  DECIMAL(19,3) NOT NULL DEFAULT 0,
    reserved_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    incoming_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    version           INT           NOT NULL DEFAULT 0,
    updated_at        DATETIME(3)   NOT NULL,
    CONSTRAINT pk_inventory_stock_summaries PRIMARY KEY (id),
    CONSTRAINT uk_inventory_stock_summaries_key UNIQUE (product_id, warehouse_id),
    CONSTRAINT fk_inventory_stock_summaries_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_inventory_stock_summaries_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_stock_summaries_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_inventory_stock_summaries_values CHECK (
        on_hand_quantity >= 0 AND reserved_quantity >= 0 AND incoming_quantity >= 0),
    INDEX idx_inventory_stock_summaries_warehouse (warehouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Giữ chỗ tồn cho đơn bán / hợp đồng / phiếu xuất chưa ghi sổ (BR 5.4). Phân hệ bán hàng dùng ở Giai đoạn 5.
CREATE TABLE inventory_reservations (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    company_id        BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    warehouse_id      BIGINT        NOT NULL,
    lot_id            BIGINT        NULL,
    serial_id         BIGINT        NULL,
    source_type       VARCHAR(30)   NOT NULL,
    source_id         BIGINT        NOT NULL,
    source_line_id    BIGINT        NULL,
    quantity          DECIMAL(19,3) NOT NULL,
    consumed_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    released_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL,
    expires_at        DATETIME(3)   NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME(3)   NOT NULL,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_reservations PRIMARY KEY (id),
    CONSTRAINT fk_inventory_reservations_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_inventory_reservations_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_reservations_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_inventory_reservations_quantity CHECK (
        quantity > 0 AND consumed_quantity >= 0 AND released_quantity >= 0
        AND consumed_quantity + released_quantity <= quantity),
    INDEX idx_inventory_reservations_source (source_type, source_id),
    INDEX idx_inventory_reservations_stock (product_id, warehouse_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Trạng thái kho: kiểm kê đang chạy chặn nhập/xuất trong kho (D-11). Dòng khóa khi bắt đầu / kết thúc kiểm kê.
CREATE TABLE inventory_warehouse_states (
    warehouse_id           BIGINT      NOT NULL,
    company_id             BIGINT      NOT NULL,
    counting_stock_count_id BIGINT     NULL,
    version                INT         NOT NULL DEFAULT 0,
    updated_at             DATETIME(3) NOT NULL,
    CONSTRAINT pk_inventory_warehouse_states PRIMARY KEY (warehouse_id),
    CONSTRAINT fk_inventory_warehouse_states_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_warehouse_states_companies FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Chứng từ kho nhóm A dùng chung một bảng (docs/03 mục 4.28): nhập (PNK), xuất nội bộ (PXK), xuất hủy (PXH),
-- chuyển kho (PCK), điều chỉnh (PDC), chuyển trạng thái chất lượng (PCL), đảo chứng từ kho (PDK).
CREATE TABLE inventory_documents (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    company_id               BIGINT        NOT NULL,
    branch_id                BIGINT        NOT NULL,
    document_type            VARCHAR(30)   NOT NULL,
    sub_type                 VARCHAR(30)   NULL,
    document_no              VARCHAR(50)   NOT NULL,
    document_date            DATE          NOT NULL,
    posting_date             DATE          NOT NULL,
    status                   VARCHAR(20)   NOT NULL,
    warehouse_id             BIGINT        NOT NULL,
    destination_warehouse_id BIGINT        NULL,
    transit_warehouse_id     BIGINT        NULL,
    partner_id               BIGINT        NULL,
    reason_id                BIGINT        NULL,
    reason_text              VARCHAR(500)  NULL,
    note                     VARCHAR(1000) NULL,
    recall_id                BIGINT        NULL,
    stock_count_id           BIGINT        NULL,
    issue_policy_id          BIGINT        NULL,
    reversal_of_id           BIGINT        NULL,
    reversed_by_id           BIGINT        NULL,
    total_amount             DECIMAL(19,4) NULL,
    submitted_by             BIGINT        NULL,
    submitted_at             DATETIME(3)   NULL,
    approved_by              BIGINT        NULL,
    approved_at              DATETIME(3)   NULL,
    posted_by                BIGINT        NULL,
    posted_at                DATETIME(3)   NULL,
    received_by              BIGINT        NULL,
    received_at              DATETIME(3)   NULL,
    receive_posting_date     DATE          NULL,
    cancelled_by             BIGINT        NULL,
    cancelled_at             DATETIME(3)   NULL,
    cancel_reason            VARCHAR(500)  NULL,
    created_by               BIGINT        NULL,
    created_at               DATETIME(3)   NOT NULL,
    updated_by               BIGINT        NULL,
    updated_at               DATETIME(3)   NOT NULL,
    version                  INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_documents PRIMARY KEY (id),
    CONSTRAINT uk_inventory_documents_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_inventory_documents_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_inventory_documents_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_inventory_documents_types FOREIGN KEY (document_type) REFERENCES sys_document_types (code),
    CONSTRAINT fk_inventory_documents_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_documents_destination FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_documents_transit FOREIGN KEY (transit_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_documents_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_inventory_documents_reasons FOREIGN KEY (reason_id) REFERENCES reference_items (id),
    CONSTRAINT fk_inventory_documents_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES inventory_documents (id),
    CONSTRAINT fk_inventory_documents_reversed_by FOREIGN KEY (reversed_by_id) REFERENCES inventory_documents (id),
    INDEX idx_inventory_documents_list (company_id, document_type, status, document_date),
    INDEX idx_inventory_documents_warehouse (warehouse_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE inventory_document_lines (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    document_id             BIGINT        NOT NULL,
    line_no                 INT           NOT NULL,
    product_id              BIGINT        NOT NULL,
    unit_id                 BIGINT        NOT NULL,
    conversion_factor       DECIMAL(19,6) NOT NULL,
    quantity                DECIMAL(19,3) NOT NULL,
    base_quantity           DECIMAL(19,3) NOT NULL,
    direction               VARCHAR(3)    NULL,
    location_id             BIGINT        NULL,
    destination_location_id BIGINT        NULL,
    lot_id                  BIGINT        NULL,
    lot_no                  VARCHAR(50)   NULL,
    manufacture_date        DATE          NULL,
    expiry_date             DATE          NULL,
    quality_status          VARCHAR(30)   NULL,
    target_quality_status   VARCHAR(30)   NULL,
    unit_cost               DECIMAL(19,4) NULL,
    amount                  DECIMAL(19,4) NULL,
    lot_override_reason     VARCHAR(500)  NULL,
    note                    VARCHAR(500)  NULL,
    CONSTRAINT pk_inventory_document_lines PRIMARY KEY (id),
    CONSTRAINT uk_inventory_document_lines_no UNIQUE (document_id, line_no),
    CONSTRAINT fk_inventory_document_lines_documents FOREIGN KEY (document_id) REFERENCES inventory_documents (id),
    CONSTRAINT fk_inventory_document_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_document_lines_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_inventory_document_lines_locations FOREIGN KEY (location_id) REFERENCES warehouse_locations (id),
    CONSTRAINT fk_inventory_document_lines_dest_locations FOREIGN KEY (destination_location_id) REFERENCES warehouse_locations (id),
    CONSTRAINT fk_inventory_document_lines_lots FOREIGN KEY (lot_id) REFERENCES inventory_lots (id),
    CONSTRAINT ck_inventory_document_lines_quantity CHECK (quantity > 0 AND base_quantity > 0 AND conversion_factor > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE inventory_document_line_serials (
    line_id   BIGINT       NOT NULL,
    serial_no VARCHAR(100) NOT NULL,
    CONSTRAINT pk_inventory_document_line_serials PRIMARY KEY (line_id, serial_no),
    CONSTRAINT fk_inventory_document_line_serials_lines FOREIGN KEY (line_id) REFERENCES inventory_document_lines (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kiểm kê (BR-INV-31): DRAFT → COUNTING → SUBMITTED → APPROVED → POSTED; ghi sổ sinh phiếu điều chỉnh chênh lệch.
CREATE TABLE stock_counts (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    company_id             BIGINT        NOT NULL,
    branch_id              BIGINT        NOT NULL,
    warehouse_id           BIGINT        NOT NULL,
    document_no            VARCHAR(50)   NOT NULL,
    count_date             DATE          NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    note                   VARCHAR(1000) NULL,
    started_by             BIGINT        NULL,
    started_at             DATETIME(3)   NULL,
    submitted_by           BIGINT        NULL,
    submitted_at           DATETIME(3)   NULL,
    approved_by            BIGINT        NULL,
    approved_at            DATETIME(3)   NULL,
    posted_by              BIGINT        NULL,
    posted_at              DATETIME(3)   NULL,
    adjustment_document_id BIGINT        NULL,
    cancelled_by           BIGINT        NULL,
    cancelled_at           DATETIME(3)   NULL,
    cancel_reason          VARCHAR(500)  NULL,
    created_by             BIGINT        NULL,
    created_at             DATETIME(3)   NOT NULL,
    updated_by             BIGINT        NULL,
    updated_at             DATETIME(3)   NOT NULL,
    version                INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_counts PRIMARY KEY (id),
    CONSTRAINT uk_stock_counts_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_stock_counts_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_stock_counts_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_stock_counts_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_stock_counts_adjustments FOREIGN KEY (adjustment_document_id) REFERENCES inventory_documents (id),
    INDEX idx_stock_counts_warehouse (warehouse_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE inventory_warehouse_states
    ADD CONSTRAINT fk_inventory_warehouse_states_counts FOREIGN KEY (counting_stock_count_id) REFERENCES stock_counts (id);
ALTER TABLE inventory_documents
    ADD CONSTRAINT fk_inventory_documents_stock_counts FOREIGN KEY (stock_count_id) REFERENCES stock_counts (id);

-- Dòng kiểm kê: số tồn chụp lúc bắt đầu (snapshot) và số đếm gốc (BR-INV-31).
CREATE TABLE stock_count_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    stock_count_id    BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    location_id       BIGINT        NOT NULL,
    lot_id            BIGINT        NULL,
    serial_id         BIGINT        NULL,
    quality_status    VARCHAR(30)   NOT NULL,
    snapshot_quantity DECIMAL(19,3) NOT NULL,
    counted_quantity  DECIMAL(19,3) NULL,
    counted_by        BIGINT        NULL,
    counted_at        DATETIME(3)   NULL,
    note              VARCHAR(500)  NULL,
    CONSTRAINT pk_stock_count_lines PRIMARY KEY (id),
    CONSTRAINT fk_stock_count_lines_counts FOREIGN KEY (stock_count_id) REFERENCES stock_counts (id),
    CONSTRAINT fk_stock_count_lines_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_count_lines_locations FOREIGN KEY (location_id) REFERENCES warehouse_locations (id),
    CONSTRAINT fk_stock_count_lines_lots FOREIGN KEY (lot_id) REFERENCES inventory_lots (id),
    CONSTRAINT fk_stock_count_lines_serials FOREIGN KEY (serial_id) REFERENCES inventory_serials (id),
    CONSTRAINT ck_stock_count_lines_quantity CHECK (snapshot_quantity >= 0 AND (counted_quantity IS NULL OR counted_quantity >= 0)),
    INDEX idx_stock_count_lines_count (stock_count_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Thu hồi lô / serial (BR-INV-42): khi duyệt thì khóa lô / serial trên toàn bộ kho.
CREATE TABLE recalls (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    company_id   BIGINT        NOT NULL,
    branch_id    BIGINT        NOT NULL,
    document_no  VARCHAR(50)   NOT NULL,
    recall_date  DATE          NOT NULL,
    product_id   BIGINT        NOT NULL,
    lot_id       BIGINT        NULL,
    serial_id    BIGINT        NULL,
    reason       VARCHAR(1000) NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    submitted_by BIGINT        NULL,
    submitted_at DATETIME(3)   NULL,
    approved_by  BIGINT        NULL,
    approved_at  DATETIME(3)   NULL,
    closed_by    BIGINT        NULL,
    closed_at    DATETIME(3)   NULL,
    cancel_reason VARCHAR(500) NULL,
    created_by   BIGINT        NULL,
    created_at   DATETIME(3)   NOT NULL,
    updated_by   BIGINT        NULL,
    updated_at   DATETIME(3)   NOT NULL,
    version      INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_recalls PRIMARY KEY (id),
    CONSTRAINT uk_recalls_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_recalls_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_recalls_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_recalls_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_recalls_lots FOREIGN KEY (lot_id) REFERENCES inventory_lots (id),
    CONSTRAINT fk_recalls_serials FOREIGN KEY (serial_id) REFERENCES inventory_serials (id),
    CONSTRAINT ck_recalls_target CHECK (lot_id IS NOT NULL OR serial_id IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE inventory_documents
    ADD CONSTRAINT fk_inventory_documents_recalls FOREIGN KEY (recall_id) REFERENCES recalls (id);

-- Chính sách xuất vượt tồn khả dụng (lấy phần đang giữ chỗ), Giám đốc duyệt (BR-GEN-31, D-10).
CREATE TABLE stock_issue_policies (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    branch_id     BIGINT        NOT NULL,
    document_no   VARCHAR(50)   NOT NULL,
    warehouse_id  BIGINT        NOT NULL,
    product_id    BIGINT        NULL,
    max_quantity  DECIMAL(19,3) NOT NULL,
    used_quantity DECIMAL(19,3) NOT NULL DEFAULT 0,
    valid_from    DATE          NOT NULL,
    valid_to      DATE          NOT NULL,
    reason        VARCHAR(1000) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    submitted_by  BIGINT        NULL,
    submitted_at  DATETIME(3)   NULL,
    approved_by   BIGINT        NULL,
    approved_at   DATETIME(3)   NULL,
    cancel_reason VARCHAR(500)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME(3)   NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME(3)   NOT NULL,
    version       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_issue_policies PRIMARY KEY (id),
    CONSTRAINT uk_stock_issue_policies_no UNIQUE (company_id, document_no),
    CONSTRAINT fk_stock_issue_policies_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_stock_issue_policies_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_stock_issue_policies_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_stock_issue_policies_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_stock_issue_policies_values CHECK (
        max_quantity > 0 AND used_quantity >= 0 AND used_quantity <= max_quantity AND valid_from <= valid_to)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE inventory_documents
    ADD CONSTRAINT fk_inventory_documents_policies FOREIGN KEY (issue_policy_id) REFERENCES stock_issue_policies (id);

-- Loại chứng từ kho và chế độ duyệt (D-04: điều chỉnh, hủy, thu hồi, đảo bắt buộc duyệt).
INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed, sort_order) VALUES
    ('PNK', 'INVENTORY', 'Phiếu nhập kho',                     'CONFIGURED', FALSE, 300),
    ('PXK', 'INVENTORY', 'Phiếu xuất kho nội bộ',              'REQUIRED',   FALSE, 310),
    ('PXH', 'INVENTORY', 'Phiếu xuất hủy',                     'REQUIRED',   FALSE, 320),
    ('PCK', 'INVENTORY', 'Phiếu chuyển kho',                   'CONFIGURED', FALSE, 330),
    ('PDC', 'INVENTORY', 'Phiếu điều chỉnh tồn',               'REQUIRED',   FALSE, 340),
    ('PCL', 'INVENTORY', 'Phiếu chuyển trạng thái chất lượng', 'REQUIRED',   FALSE, 350),
    ('PDK', 'INVENTORY', 'Chứng từ đảo chứng từ kho',          'REQUIRED',   FALSE, 360),
    ('PKK', 'INVENTORY', 'Phiếu kiểm kê',                      'REQUIRED',   FALSE, 370),
    ('PTH', 'INVENTORY', 'Phiếu thu hồi',                      'REQUIRED',   FALSE, 380),
    ('CSX', 'INVENTORY', 'Chính sách xuất vượt tồn khả dụng',  'REQUIRED',   FALSE, 390);

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('inventory:view',               'INVENTORY', 'Xem tồn kho, sổ kho, lô, serial, cảnh báo', 300),
    ('lot:block',                    'INVENTORY', 'Khóa / mở khóa lô, serial',                 301),
    ('inventory-receipt:view',       'INVENTORY', 'Xem phiếu nhập kho',                        310),
    ('inventory-receipt:create',     'INVENTORY', 'Lập, sửa, gửi duyệt, hủy phiếu nhập kho',   311),
    ('inventory-receipt:approve',    'INVENTORY', 'Duyệt phiếu nhập kho',                      312),
    ('inventory-receipt:post',       'INVENTORY', 'Ghi sổ phiếu nhập kho',                     313),
    ('inventory-issue:view',         'INVENTORY', 'Xem phiếu xuất kho, xuất hủy',              320),
    ('inventory-issue:create',       'INVENTORY', 'Lập, sửa, gửi duyệt, hủy phiếu xuất',       321),
    ('inventory-issue:approve',      'INVENTORY', 'Duyệt phiếu xuất kho, xuất hủy',            322),
    ('inventory-issue:post',         'INVENTORY', 'Ghi sổ phiếu xuất kho, xuất hủy',           323),
    ('inventory-transfer:view',      'INVENTORY', 'Xem phiếu chuyển kho',                      330),
    ('inventory-transfer:create',    'INVENTORY', 'Lập, sửa, gửi duyệt, hủy phiếu chuyển kho', 331),
    ('inventory-transfer:approve',   'INVENTORY', 'Duyệt phiếu chuyển kho',                    332),
    ('inventory-transfer:post',      'INVENTORY', 'Ghi sổ (xuất, nhận) phiếu chuyển kho',      333),
    ('inventory-adjustment:view',    'INVENTORY', 'Xem phiếu điều chỉnh, chuyển trạng thái chất lượng', 340),
    ('inventory-adjustment:create',  'INVENTORY', 'Lập phiếu điều chỉnh, chuyển trạng thái chất lượng', 341),
    ('inventory-adjustment:approve', 'INVENTORY', 'Duyệt phiếu điều chỉnh, chuyển trạng thái chất lượng', 342),
    ('inventory-adjustment:post',    'INVENTORY', 'Ghi sổ phiếu điều chỉnh, chuyển trạng thái chất lượng', 343),
    ('inventory-reversal:create',    'INVENTORY', 'Lập chứng từ đảo chứng từ kho',             350),
    ('inventory-reversal:approve',   'INVENTORY', 'Duyệt đảo chứng từ kho',                    351),
    ('inventory-reversal:post',      'INVENTORY', 'Ghi sổ đảo chứng từ kho',                   352),
    ('stock-count:view',             'INVENTORY', 'Xem kiểm kê',                               360),
    ('stock-count:create',           'INVENTORY', 'Lập, bắt đầu, nhập số đếm, gửi duyệt kiểm kê', 361),
    ('stock-count:approve',          'INVENTORY', 'Duyệt kiểm kê',                             362),
    ('stock-count:post',             'INVENTORY', 'Ghi sổ kiểm kê',                            363),
    ('recall:view',                  'INVENTORY', 'Xem thu hồi',                               370),
    ('recall:create',                'INVENTORY', 'Lập, gửi duyệt, đóng thu hồi',              371),
    ('recall:approve',               'INVENTORY', 'Duyệt thu hồi',                             372),
    ('stock-policy:view',            'INVENTORY', 'Xem chính sách xuất vượt tồn khả dụng',     380),
    ('stock-policy:create',          'INVENTORY', 'Lập chính sách xuất vượt tồn khả dụng',     381),
    ('stock-policy:approve',         'INVENTORY', 'Duyệt chính sách xuất vượt tồn khả dụng',   382);

-- Gán quyền mặc định (docs/07 mục 2.2). [Suy luận] Doanh nghiệp điều chỉnh ở màn hình vai trò.
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON
    (r.code = 'SYSTEM_ADMIN' AND p.code = 'inventory:view') OR
    (r.code IN ('DIRECTOR', 'BRANCH_DIRECTOR') AND p.module = 'INVENTORY'
        AND (p.code LIKE '%:view' OR p.code LIKE '%:approve')) OR
    (r.code = 'WAREHOUSE_MANAGER' AND p.module = 'INVENTORY'
        AND p.code NOT IN ('stock-policy:approve', 'recall:approve', 'inventory-reversal:approve')) OR
    (r.code = 'WAREHOUSE_STAFF' AND p.code IN ('inventory:view', 'inventory-receipt:view', 'inventory-receipt:create',
        'inventory-receipt:post', 'inventory-issue:view', 'inventory-issue:create', 'inventory-issue:post',
        'inventory-transfer:view', 'inventory-transfer:create', 'inventory-transfer:post',
        'inventory-adjustment:view', 'inventory-adjustment:create', 'stock-count:view', 'stock-count:create',
        'recall:view')) OR
    (r.code = 'QA' AND p.code IN ('inventory:view', 'lot:block', 'inventory-adjustment:view',
        'inventory-adjustment:create', 'inventory-adjustment:approve', 'recall:view', 'recall:create')) OR
    (r.code = 'CHIEF_ACCOUNTANT' AND p.module = 'INVENTORY'
        AND (p.code LIKE '%:view' OR p.code IN ('inventory-reversal:create', 'inventory-reversal:approve',
             'inventory-reversal:post', 'stock-count:approve'))) OR
    (r.code IN ('SALES_MANAGER', 'SALES_STAFF', 'PURCHASING_STAFF', 'TENDER_STAFF', 'TAX_ACCOUNTANT',
                'AR_ACCOUNTANT', 'VIEWER') AND p.code = 'inventory:view');
