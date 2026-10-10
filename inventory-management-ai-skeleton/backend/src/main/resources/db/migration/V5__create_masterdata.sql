-- Giai đoạn 2: danh mục dùng chung M02 (BR-MD-01..46). Thiết kế: docs/03 mục 4.22-4.27.

-- Quốc gia / xuất xứ (dữ liệu tham chiếu dùng chung mọi công ty, mã ISO 3166-1 alpha-2).
CREATE TABLE countries (
    code       VARCHAR(2)      NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_by BIGINT       NULL,
    updated_at DATETIME(3)  NOT NULL,
    version    INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_countries PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE units (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    company_id BIGINT       NOT NULL,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_by BIGINT       NULL,
    updated_at DATETIME(3)  NOT NULL,
    version    INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_units PRIMARY KEY (id),
    CONSTRAINT uk_units_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_units_companies FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE brands (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    company_id BIGINT       NOT NULL,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_by BIGINT       NULL,
    updated_at DATETIME(3)  NOT NULL,
    version    INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_brands PRIMARY KEY (id),
    CONSTRAINT uk_brands_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_brands_companies FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE manufacturers (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    company_id   BIGINT       NOT NULL,
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    country_code VARCHAR(2)      NULL,
    status       VARCHAR(20)  NOT NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME(3)  NOT NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME(3)  NOT NULL,
    version      INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_manufacturers PRIMARY KEY (id),
    CONSTRAINT uk_manufacturers_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_manufacturers_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_manufacturers_countries FOREIGN KEY (country_code) REFERENCES countries (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Nhóm đối tác (nhóm khách hàng cho bảng giá, BR-MD-31).
CREATE TABLE partner_groups (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    company_id BIGINT       NOT NULL,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_by BIGINT       NULL,
    updated_at DATETIME(3)  NOT NULL,
    version    INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_partner_groups PRIMARY KEY (id),
    CONSTRAINT uk_partner_groups_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_partner_groups_companies FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Danh mục phụ dạng mã - tên theo loại (BR-MD-45, BR-MD-46): điều khoản, phương thức thanh toán, giao hàng,
-- loại hợp đồng, khoản mục chi phí, lý do điều chỉnh / hủy / trả hàng / giảm giá / từ chối / chọn lô khác FEFO.
CREATE TABLE reference_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id    BIGINT        NOT NULL,
    category      VARCHAR(30)   NOT NULL,
    code          VARCHAR(30)   NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    numeric_value DECIMAL(19,4) NULL,
    status        VARCHAR(20)   NOT NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME(3)   NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME(3)   NOT NULL,
    version       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_reference_items PRIMARY KEY (id),
    CONSTRAINT uk_reference_items_code UNIQUE (company_id, category, code),
    CONSTRAINT fk_reference_items_companies FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Thuế suất GTGT có hiệu lực (BR-MD-42, BR-GEN-42). Giá trị do kế toán trưởng xác nhận.
CREATE TABLE tax_rates (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    company_id BIGINT       NOT NULL,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    rate       DECIMAL(7,4) NOT NULL,
    valid_from DATE         NOT NULL,
    valid_to   DATE         NULL,
    status     VARCHAR(20)  NOT NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_by BIGINT       NULL,
    updated_at DATETIME(3)  NOT NULL,
    version    INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_tax_rates PRIMARY KEY (id),
    CONSTRAINT uk_tax_rates_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_tax_rates_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT ck_tax_rates_rate CHECK (rate >= 0 AND rate <= 100),
    CONSTRAINT ck_tax_rates_period CHECK (valid_to IS NULL OR valid_from <= valid_to)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Nhóm hàng dạng cây (BR-MD-03).
CREATE TABLE product_categories (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    company_id BIGINT       NOT NULL,
    parent_id  BIGINT       NULL,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(3)  NOT NULL,
    updated_by BIGINT       NULL,
    updated_at DATETIME(3)  NOT NULL,
    version    INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_product_categories PRIMARY KEY (id),
    CONSTRAINT uk_product_categories_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_product_categories_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_product_categories_parent FOREIGN KEY (parent_id) REFERENCES product_categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hàng hóa (BR-MD-01..09, BR-MD-12).
CREATE TABLE products (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    company_id               BIGINT        NOT NULL,
    code                     VARCHAR(50)   NOT NULL,
    legacy_code              VARCHAR(50)   NULL,
    name                     VARCHAR(255)  NOT NULL,
    trade_name               VARCHAR(255)  NULL,
    category_id              BIGINT        NOT NULL,
    product_type             VARCHAR(20)   NOT NULL,
    base_unit_id             BIGINT        NOT NULL,
    brand_id                 BIGINT        NULL,
    manufacturer_id          BIGINT        NULL,
    origin_country_code      VARCHAR(2)       NULL,
    model                    VARCHAR(100)  NULL,
    specification            VARCHAR(500)  NULL,
    description              VARCHAR(2000) NULL,
    lot_tracked              BOOLEAN       NOT NULL,
    expiry_tracked           BOOLEAN       NOT NULL,
    serial_tracked           BOOLEAN       NOT NULL,
    medical_device_class     VARCHAR(1)       NULL,
    registration_no          VARCHAR(100)  NULL,
    registration_holder      VARCHAR(255)  NULL,
    registration_valid_to    DATE          NULL,
    storage_min_temp         DECIMAL(5,2)  NULL,
    storage_max_temp         DECIMAL(5,2)  NULL,
    cold_chain               BOOLEAN       NOT NULL DEFAULT FALSE,
    hazardous                BOOLEAN       NOT NULL DEFAULT FALSE,
    warranty_months          INT           NULL,
    maintenance_cycle_months INT           NULL,
    requires_installation    BOOLEAN       NOT NULL DEFAULT FALSE,
    requires_calibration     BOOLEAN       NOT NULL DEFAULT FALSE,
    slow_moving_days         INT           NULL,
    default_tax_rate_id      BIGINT        NULL,
    status                   VARCHAR(20)   NOT NULL,
    created_by               BIGINT        NULL,
    created_at               DATETIME(3)   NOT NULL,
    updated_by               BIGINT        NULL,
    updated_at               DATETIME(3)   NOT NULL,
    version                  INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_products_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_products_categories FOREIGN KEY (category_id) REFERENCES product_categories (id),
    CONSTRAINT fk_products_units FOREIGN KEY (base_unit_id) REFERENCES units (id),
    CONSTRAINT fk_products_brands FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT fk_products_manufacturers FOREIGN KEY (manufacturer_id) REFERENCES manufacturers (id),
    CONSTRAINT fk_products_countries FOREIGN KEY (origin_country_code) REFERENCES countries (code),
    CONSTRAINT fk_products_tax_rates FOREIGN KEY (default_tax_rate_id) REFERENCES tax_rates (id),
    CONSTRAINT ck_products_expiry_needs_lot CHECK (expiry_tracked = FALSE OR lot_tracked = TRUE),
    CONSTRAINT ck_products_device_class CHECK (medical_device_class IS NULL OR medical_device_class IN ('A', 'B', 'C', 'D')),
    INDEX idx_products_company_name (company_id, name),
    INDEX idx_products_legacy_code (company_id, legacy_code),
    INDEX idx_products_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Đơn vị giao dịch và hệ số quy đổi về đơn vị cơ sở, có hiệu lực (BR-MD-20, BR-MD-21). Hệ số đã tạo không sửa.
CREATE TABLE product_units (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    product_id        BIGINT        NOT NULL,
    unit_id           BIGINT        NOT NULL,
    conversion_factor DECIMAL(19,6) NOT NULL,
    effective_from    DATE          NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(3)   NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME(3)   NOT NULL,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_product_units PRIMARY KEY (id),
    CONSTRAINT uk_product_units_effective UNIQUE (product_id, unit_id, effective_from),
    CONSTRAINT fk_product_units_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_units_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT ck_product_units_factor CHECK (conversion_factor > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kho thuộc chi nhánh; kho ảo cho hàng đang vận chuyển, tại khách, đang sửa, demo (BR-MD-40, BR-MD-41, D-19).
CREATE TABLE warehouses (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    company_id        BIGINT       NOT NULL,
    branch_id         BIGINT       NOT NULL,
    code              VARCHAR(30)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    warehouse_type    VARCHAR(20)  NOT NULL,
    virtual_kind      VARCHAR(20)  NULL,
    address           VARCHAR(500) NULL,
    storage_condition VARCHAR(200) NULL,
    status            VARCHAR(20)  NOT NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME(3)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_warehouses PRIMARY KEY (id),
    CONSTRAINT uk_warehouses_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_warehouses_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_warehouses_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT ck_warehouses_virtual CHECK (
        (warehouse_type = 'NORMAL' AND virtual_kind IS NULL) OR (warehouse_type = 'VIRTUAL' AND virtual_kind IS NOT NULL)),
    INDEX idx_warehouses_branch (branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Vị trí trong kho; mỗi kho có đúng một vị trí mặc định (default_flag + unique).
CREATE TABLE warehouse_locations (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    warehouse_id      BIGINT       NOT NULL,
    code              VARCHAR(30)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    zone_type         VARCHAR(20)  NOT NULL,
    storage_condition VARCHAR(200) NULL,
    is_default        BOOLEAN      NOT NULL DEFAULT FALSE,
    default_flag      TINYINT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END) STORED,
    status            VARCHAR(20)  NOT NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME(3)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_warehouse_locations PRIMARY KEY (id),
    CONSTRAINT uk_warehouse_locations_code UNIQUE (warehouse_id, code),
    CONSTRAINT uk_warehouse_locations_default UNIQUE (warehouse_id, default_flag),
    CONSTRAINT fk_warehouse_locations_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Định mức tồn theo hàng và kho (BR-MD-09).
CREATE TABLE product_stock_levels (
    product_id   BIGINT        NOT NULL,
    warehouse_id BIGINT        NOT NULL,
    min_quantity DECIMAL(19,3) NULL,
    max_quantity DECIMAL(19,3) NULL,
    CONSTRAINT pk_product_stock_levels PRIMARY KEY (product_id, warehouse_id),
    CONSTRAINT fk_product_stock_levels_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_stock_levels_warehouses FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT ck_product_stock_levels_range CHECK (
        (min_quantity IS NULL OR min_quantity >= 0) AND (max_quantity IS NULL OR max_quantity >= 0)
        AND (min_quantity IS NULL OR max_quantity IS NULL OR min_quantity <= max_quantity))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Bảng giá: giá mua tham khảo, niêm yết, giá sàn, bảng giá bán theo nhóm/khách/đại lý (BR-MD-42).
CREATE TABLE price_lists (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_id       BIGINT       NOT NULL,
    code             VARCHAR(30)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    price_type       VARCHAR(30)  NOT NULL,
    target_type      VARCHAR(20)  NOT NULL,
    partner_group_id BIGINT       NULL,
    partner_id       BIGINT       NULL,
    currency_code    VARCHAR(3)      NOT NULL DEFAULT 'VND',
    valid_from       DATE         NOT NULL,
    valid_to         DATE         NULL,
    status           VARCHAR(20)  NOT NULL,
    created_by       BIGINT       NULL,
    created_at       DATETIME(3)  NOT NULL,
    updated_by       BIGINT       NULL,
    updated_at       DATETIME(3)  NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_price_lists PRIMARY KEY (id),
    CONSTRAINT uk_price_lists_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_price_lists_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_price_lists_partner_groups FOREIGN KEY (partner_group_id) REFERENCES partner_groups (id),
    CONSTRAINT ck_price_lists_period CHECK (valid_to IS NULL OR valid_from <= valid_to),
    CONSTRAINT ck_price_lists_target CHECK (
        (target_type = 'ALL' AND partner_group_id IS NULL AND partner_id IS NULL)
        OR (target_type = 'PARTNER_GROUP' AND partner_group_id IS NOT NULL AND partner_id IS NULL)
        OR (target_type = 'PARTNER' AND partner_id IS NOT NULL AND partner_group_id IS NULL)),
    INDEX idx_price_lists_type (company_id, price_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE price_list_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    price_list_id BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,
    unit_id       BIGINT        NOT NULL,
    min_quantity  DECIMAL(19,3) NOT NULL DEFAULT 0,
    unit_price    DECIMAL(19,4) NOT NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME(3)   NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME(3)   NOT NULL,
    version       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_price_list_items PRIMARY KEY (id),
    CONSTRAINT uk_price_list_items_tier UNIQUE (price_list_id, product_id, unit_id, min_quantity),
    CONSTRAINT fk_price_list_items_lists FOREIGN KEY (price_list_id) REFERENCES price_lists (id),
    CONSTRAINT fk_price_list_items_products FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_price_list_items_units FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT ck_price_list_items_values CHECK (min_quantity >= 0 AND unit_price >= 0),
    INDEX idx_price_list_items_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Đối tác: khách hàng, nhà cung cấp, đại lý, bên mời thầu, nhân viên, khác (BR-MD-30..35).
-- Thuộc tính theo vai trò (điều khoản khách, NCC, đại lý) để chung một bảng: P1 không cần bảng hồ sơ riêng.
CREATE TABLE partners (
    id                            BIGINT        NOT NULL AUTO_INCREMENT,
    company_id                    BIGINT        NOT NULL,
    code                          VARCHAR(50)   NOT NULL,
    legacy_code                   VARCHAR(50)   NULL,
    name                          VARCHAR(255)  NOT NULL,
    short_name                    VARCHAR(100)  NULL,
    partner_kind                  VARCHAR(20)   NOT NULL,
    tax_code                      VARCHAR(20)   NULL,
    address                       VARCHAR(500)  NULL,
    phone                         VARCHAR(30)   NULL,
    email                         VARCHAR(255)  NULL,
    country_code                  VARCHAR(2)       NULL,
    partner_group_id              BIGINT        NULL,
    customer_type                 VARCHAR(30)   NULL,
    sales_user_id                 BIGINT        NULL,
    managing_branch_id            BIGINT        NULL,
    payment_term_days             INT           NULL,
    credit_limit                  DECIMAL(19,4) NULL,
    min_remaining_shelf_life_days INT           NULL,
    price_list_id                 BIGINT        NULL,
    supplier_payment_term_days    INT           NULL,
    currency_code                 VARCHAR(3)       NULL,
    distributed_brands            VARCHAR(500)  NULL,
    agent_discount_percent        DECIMAL(9,4)  NULL,
    agent_region                  VARCHAR(200)  NULL,
    agent_credit_limit            DECIMAL(19,4) NULL,
    status                        VARCHAR(20)   NOT NULL,
    created_by                    BIGINT        NULL,
    created_at                    DATETIME(3)   NOT NULL,
    updated_by                    BIGINT        NULL,
    updated_at                    DATETIME(3)   NOT NULL,
    version                       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_partners PRIMARY KEY (id),
    CONSTRAINT uk_partners_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_partners_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_partners_countries FOREIGN KEY (country_code) REFERENCES countries (code),
    CONSTRAINT fk_partners_groups FOREIGN KEY (partner_group_id) REFERENCES partner_groups (id),
    CONSTRAINT fk_partners_sales_users FOREIGN KEY (sales_user_id) REFERENCES sys_users (id),
    CONSTRAINT fk_partners_branches FOREIGN KEY (managing_branch_id) REFERENCES branches (id),
    CONSTRAINT fk_partners_price_lists FOREIGN KEY (price_list_id) REFERENCES price_lists (id),
    CONSTRAINT ck_partners_amounts CHECK (
        (credit_limit IS NULL OR credit_limit >= 0) AND (agent_credit_limit IS NULL OR agent_credit_limit >= 0)),
    INDEX idx_partners_company_name (company_id, name),
    INDEX idx_partners_tax_code (company_id, tax_code),
    INDEX idx_partners_sales_user (sales_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE price_lists
    ADD CONSTRAINT fk_price_lists_partners FOREIGN KEY (partner_id) REFERENCES partners (id);

CREATE TABLE partner_roles (
    partner_id BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    CONSTRAINT pk_partner_roles PRIMARY KEY (partner_id, role),
    CONSTRAINT fk_partner_roles_partners FOREIGN KEY (partner_id) REFERENCES partners (id),
    INDEX idx_partner_roles_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE partner_contacts (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    partner_id BIGINT       NOT NULL,
    name       VARCHAR(200) NOT NULL,
    position   VARCHAR(100) NULL,
    phone      VARCHAR(30)  NULL,
    email      VARCHAR(255) NULL,
    is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_partner_contacts PRIMARY KEY (id),
    CONSTRAINT fk_partner_contacts_partners FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE partner_addresses (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    partner_id    BIGINT       NOT NULL,
    address       VARCHAR(500) NOT NULL,
    receiver_name VARCHAR(200) NULL,
    phone         VARCHAR(30)  NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_partner_addresses PRIMARY KEY (id),
    CONSTRAINT fk_partner_addresses_partners FOREIGN KEY (partner_id) REFERENCES partners (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Loại hồ sơ / giấy phép (BR-MD-44, D-28) và hồ sơ cụ thể có hạn (gắn hàng, lô, serial, đối tác, công ty).
CREATE TABLE compliance_document_types (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    company_id             BIGINT       NOT NULL,
    code                   VARCHAR(30)  NOT NULL,
    name                   VARCHAR(200) NOT NULL,
    applies_to             VARCHAR(20)  NOT NULL,
    has_expiry             BOOLEAN      NOT NULL,
    required_product_types VARCHAR(200) NULL,
    status                 VARCHAR(20)  NOT NULL,
    created_by             BIGINT       NULL,
    created_at             DATETIME(3)  NOT NULL,
    updated_by             BIGINT       NULL,
    updated_at             DATETIME(3)  NOT NULL,
    version                INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_compliance_document_types PRIMARY KEY (id),
    CONSTRAINT uk_compliance_document_types_code UNIQUE (company_id, code),
    CONSTRAINT fk_compliance_document_types_companies FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE compliance_documents (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_id       BIGINT       NOT NULL,
    document_type_id BIGINT       NOT NULL,
    owner_type       VARCHAR(20)  NOT NULL,
    owner_id         BIGINT       NOT NULL,
    document_no      VARCHAR(100) NOT NULL,
    issued_date      DATE         NULL,
    expiry_date      DATE         NULL,
    issuer           VARCHAR(255) NULL,
    note             VARCHAR(500) NULL,
    status           VARCHAR(20)  NOT NULL,
    created_by       BIGINT       NULL,
    created_at       DATETIME(3)  NOT NULL,
    updated_by       BIGINT       NULL,
    updated_at       DATETIME(3)  NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_compliance_documents PRIMARY KEY (id),
    CONSTRAINT fk_compliance_documents_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_compliance_documents_types FOREIGN KEY (document_type_id) REFERENCES compliance_document_types (id),
    CONSTRAINT ck_compliance_documents_dates CHECK (issued_date IS NULL OR expiry_date IS NULL OR issued_date <= expiry_date),
    INDEX idx_compliance_documents_owner (owner_type, owner_id),
    INDEX idx_compliance_documents_expiry (company_id, status, expiry_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO countries (code, name, status, created_at, updated_at) VALUES
    ('VN', 'Việt Nam',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('US', 'Hoa Kỳ',          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('DE', 'Đức',             'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('JP', 'Nhật Bản',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('KR', 'Hàn Quốc',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('CN', 'Trung Quốc',      'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('TW', 'Đài Loan',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('FR', 'Pháp',            'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('GB', 'Vương quốc Anh',  'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('IT', 'Ý',               'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('CH', 'Thụy Sĩ',         'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('IE', 'Ireland',         'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('NL', 'Hà Lan',          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('BE', 'Bỉ',              'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('SE', 'Thụy Điển',       'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('DK', 'Đan Mạch',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('FI', 'Phần Lan',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('AT', 'Áo',              'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('ES', 'Tây Ban Nha',     'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('PL', 'Ba Lan',          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('IN', 'Ấn Độ',           'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('SG', 'Singapore',       'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('MY', 'Malaysia',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('TH', 'Thái Lan',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('ID', 'Indonesia',       'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('AU', 'Úc',              'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('CA', 'Canada',          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('IL', 'Israel',          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('MX', 'Mexico',          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('BR', 'Brazil',          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('catalog:view',        'MASTERDATA', 'Xem danh mục phụ (ĐVT, nhóm hàng, nhãn hiệu, NSX, xuất xứ, thuế suất, lý do...)', 200),
    ('catalog:manage',      'MASTERDATA', 'Thêm, sửa, ngừng danh mục phụ',                     201),
    ('product:view',        'MASTERDATA', 'Xem hàng hóa',                                       210),
    ('product:manage',      'MASTERDATA', 'Thêm, sửa, ngừng, nhập Excel hàng hóa',              211),
    ('partner:view',        'MASTERDATA', 'Xem đối tác',                                        220),
    ('partner:manage',      'MASTERDATA', 'Thêm, sửa, ngừng, nhập Excel đối tác',               221),
    ('warehouse:view',      'MASTERDATA', 'Xem kho, vị trí',                                    230),
    ('warehouse:manage',    'MASTERDATA', 'Thêm, sửa, ngừng kho, vị trí; định mức tồn',         231),
    ('price:view',          'MASTERDATA', 'Xem bảng giá bán, giá sàn',                          240),
    ('price:manage',        'MASTERDATA', 'Thêm, sửa bảng giá',                                 241),
    ('purchase-price:view', 'MASTERDATA', 'Xem giá mua tham khảo (BR-SYS-05)',                  242),
    ('compliance:view',     'MASTERDATA', 'Xem hồ sơ, giấy phép',                               250),
    ('compliance:manage',   'MASTERDATA', 'Thêm, sửa hồ sơ, giấy phép',                         251);

-- Gán quyền mặc định theo vai trò (docs/07 mục 2.2). Doanh nghiệp điều chỉnh ở màn hình vai trò. [Suy luận]
INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON
    (r.code IN ('SYSTEM_ADMIN') AND p.module = 'MASTERDATA') OR
    (r.code = 'DIRECTOR' AND p.code IN ('catalog:view', 'product:view', 'partner:view', 'warehouse:view',
                                         'price:view', 'purchase-price:view', 'compliance:view')) OR
    (r.code = 'BRANCH_DIRECTOR' AND p.code IN ('catalog:view', 'product:view', 'partner:view', 'warehouse:view',
                                                'price:view', 'compliance:view')) OR
    (r.code = 'SALES_MANAGER' AND p.code IN ('catalog:view', 'product:view', 'partner:view', 'partner:manage',
                                              'warehouse:view', 'price:view', 'price:manage', 'compliance:view')) OR
    (r.code IN ('SALES_STAFF', 'TENDER_STAFF') AND p.code IN ('catalog:view', 'product:view', 'partner:view',
                                                               'warehouse:view', 'price:view', 'compliance:view')) OR
    (r.code = 'TENDER_STAFF' AND p.code = 'compliance:manage') OR
    (r.code = 'PURCHASING_STAFF' AND p.code IN ('catalog:view', 'product:view', 'product:manage', 'partner:view',
                                                 'partner:manage', 'warehouse:view', 'price:view',
                                                 'purchase-price:view', 'compliance:view', 'compliance:manage')) OR
    (r.code = 'WAREHOUSE_MANAGER' AND p.code IN ('catalog:view', 'product:view', 'partner:view', 'warehouse:view',
                                                  'warehouse:manage', 'compliance:view')) OR
    (r.code IN ('WAREHOUSE_STAFF', 'TECHNICIAN', 'DELIVERY_STAFF', 'VIEWER')
        AND p.code IN ('catalog:view', 'product:view', 'partner:view', 'warehouse:view')) OR
    (r.code = 'QA' AND p.code IN ('catalog:view', 'product:view', 'warehouse:view', 'compliance:view',
                                   'compliance:manage')) OR
    (r.code IN ('AR_ACCOUNTANT', 'CASHIER', 'TAX_ACCOUNTANT') AND p.code IN ('catalog:view', 'product:view',
                                                                              'partner:view', 'warehouse:view',
                                                                              'price:view')) OR
    (r.code = 'CHIEF_ACCOUNTANT' AND p.code IN ('catalog:view', 'catalog:manage', 'product:view', 'partner:view',
                                                 'warehouse:view', 'price:view', 'purchase-price:view',
                                                 'compliance:view'));
