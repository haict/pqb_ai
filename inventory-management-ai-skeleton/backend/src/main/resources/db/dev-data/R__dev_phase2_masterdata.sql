-- DỮ LIỆU MẪU CHỈ CHO dev/test (Giai đoạn 2 - danh mục). Không bao giờ chạy ở uat/prod.
-- Chạy sau R__dev_organization_and_users.sql (thứ tự tên). Viết để chạy lại được: INSERT IGNORE theo khóa nghiệp vụ.
-- Thuế suất, hồ sơ pháp lý là dữ liệu giả lập; giá trị thật do kế toán trưởng / pháp chế xác nhận.

INSERT IGNORE INTO units (company_id, code, name, status, created_at, updated_at) VALUES
    (1, 'CAI',  'Cái',   'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'BO',   'Bộ',    'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'HOP',  'Hộp',   'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'CHAI', 'Chai',  'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'ML',   'ml',    'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'TEST', 'Test',  'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO product_categories (company_id, code, name, status, created_at, updated_at) VALUES
    (1, 'MAY',  'Máy móc - thiết bị',     'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'TT',   'Thuốc thử - hóa chất',   'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'VT',   'Vật tư tiêu hao',        'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO product_categories (company_id, parent_id, code, name, status, created_at, updated_at)
SELECT 1, id, 'MAY_XN', 'Máy xét nghiệm', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM product_categories WHERE company_id = 1 AND code = 'MAY';

INSERT IGNORE INTO brands (company_id, code, name, status, created_at, updated_at) VALUES
    (1, 'ROCHE',  'Roche (mẫu)',  'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'SYSMEX', 'Sysmex (mẫu)', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO manufacturers (company_id, code, name, country_code, status, created_at, updated_at) VALUES
    (1, 'ROCHE_DE',  'Roche Diagnostics GmbH (mẫu)', 'DE', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'SYSMEX_JP', 'Sysmex Corporation (mẫu)',     'JP', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO tax_rates (company_id, code, name, rate, valid_from, status, created_at, updated_at) VALUES
    (1, 'VAT0',  'Thuế GTGT 0% (mẫu)',  0,  '2020-01-01', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'VAT5',  'Thuế GTGT 5% (mẫu)',  5,  '2020-01-01', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'VAT10', 'Thuế GTGT 10% (mẫu)', 10, '2020-01-01', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO partner_groups (company_id, code, name, status, created_at, updated_at) VALUES
    (1, 'BV_CONG', 'Bệnh viện công (mẫu)', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'DAI_LY',  'Đại lý (mẫu)',         'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO reference_items (company_id, category, code, name, numeric_value, status, created_at, updated_at) VALUES
    (1, 'PAYMENT_TERM',          'NET30',        'Thanh toán trong 30 ngày', 30, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'PAYMENT_METHOD',        'CK',           'Chuyển khoản',           NULL, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'ADJUSTMENT_REASON',     'HONG_VO',      'Hỏng vỡ',                NULL, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'ADJUSTMENT_REASON',     'SAI_LECH',     'Sai lệch số liệu',       NULL, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'SCRAP_REASON',          'HET_HAN',      'Hết hạn sử dụng',        NULL, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'LOT_OVERRIDE_REASON',   'KHACH_YC',     'Khách yêu cầu lô cụ thể', NULL, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'QUALITY_CHANGE_REASON', 'KQ_KIEM_TRA',  'Kết quả kiểm tra chất lượng', NULL, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'QUALITY_CHANGE_REASON', 'CANH_BAO_NSX', 'Cảnh báo của nhà sản xuất',   NULL, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- Kho thật và kho ảo (BR-MD-40, BR-MD-41). Kho "đang vận chuyển" dùng cho chuyển kho khác chi nhánh (BR-INV-30).
INSERT IGNORE INTO warehouses (company_id, branch_id, code, name, warehouse_type, virtual_kind, status, created_at, updated_at) VALUES
    (1, 1, 'KHO_HN',    'Kho Hà Nội (mẫu)',                'NORMAL',  NULL,          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 2, 'KHO_HCM',   'Kho Hồ Chí Minh (mẫu)',           'NORMAL',  NULL,          'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 1, 'VC_HN',     'Hàng đang vận chuyển về HN (mẫu)', 'VIRTUAL', 'IN_TRANSIT',  'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 2, 'VC_HCM',    'Hàng đang vận chuyển về HCM (mẫu)', 'VIRTUAL', 'IN_TRANSIT', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 1, 'TAI_KHACH', 'Hàng gửi tại khách (mẫu)',        'VIRTUAL', 'AT_CUSTOMER', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO warehouse_locations (warehouse_id, code, name, zone_type, is_default, status, created_at, updated_at)
SELECT id, 'MAC_DINH', 'Vị trí mặc định', 'NORMAL', TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM warehouses WHERE company_id = 1;

INSERT IGNORE INTO warehouse_locations (warehouse_id, code, name, zone_type, is_default, status, created_at, updated_at)
SELECT id, 'CACH_LY', 'Khu cách ly', 'QUARANTINE', FALSE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM warehouses WHERE company_id = 1 AND code IN ('KHO_HN', 'KHO_HCM');

-- Hàng mẫu theo ba kiểu quản lý: serial (máy), lô + hạn (thuốc thử, chờ kiểm tra D-09), lô + hạn (vật tư).
INSERT IGNORE INTO products (company_id, code, name, category_id, product_type, base_unit_id, brand_id, manufacturer_id,
                             origin_country_code, model, lot_tracked, expiry_tracked, serial_tracked, medical_device_class,
                             warranty_months, requires_installation, status, created_at, updated_at)
SELECT 1, 'MAY_XN_01', 'Máy xét nghiệm huyết học (mẫu)', c.id, 'MACHINE', u.id, b.id, m.id, 'JP', 'XN-1000', FALSE, FALSE,
       TRUE, 'B', 12, TRUE, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM product_categories c, units u, brands b, manufacturers m
WHERE c.company_id = 1 AND c.code = 'MAY_XN' AND u.company_id = 1 AND u.code = 'CAI'
  AND b.company_id = 1 AND b.code = 'SYSMEX' AND m.company_id = 1 AND m.code = 'SYSMEX_JP';

INSERT IGNORE INTO products (company_id, code, name, category_id, product_type, base_unit_id, brand_id, manufacturer_id,
                             origin_country_code, lot_tracked, expiry_tracked, serial_tracked, cold_chain,
                             storage_min_temp, storage_max_temp, status, created_at, updated_at)
SELECT 1, 'TT_GLU_01', 'Thuốc thử Glucose (mẫu)', c.id, 'REAGENT', u.id, b.id, m.id, 'DE', TRUE, TRUE, FALSE, TRUE,
       2, 8, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM product_categories c, units u, brands b, manufacturers m
WHERE c.company_id = 1 AND c.code = 'TT' AND u.company_id = 1 AND u.code = 'HOP'
  AND b.company_id = 1 AND b.code = 'ROCHE' AND m.company_id = 1 AND m.code = 'ROCHE_DE';

INSERT IGNORE INTO products (company_id, code, name, category_id, product_type, base_unit_id, lot_tracked,
                             expiry_tracked, serial_tracked, status, created_at, updated_at)
SELECT 1, 'VT_ONG_01', 'Ống lấy máu chân không (mẫu)', c.id, 'CONSUMABLE', u.id, TRUE, TRUE, FALSE, 'ACTIVE',
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM product_categories c, units u
WHERE c.company_id = 1 AND c.code = 'VT' AND u.company_id = 1 AND u.code = 'CAI';

-- 1 Hộp vật tư = 100 cái (đơn vị giao dịch, BR-MD-20).
INSERT IGNORE INTO product_units (product_id, unit_id, conversion_factor, effective_from, status, created_at, updated_at)
SELECT p.id, u.id, 100, '2020-01-01', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM products p, units u
WHERE p.company_id = 1 AND p.code = 'VT_ONG_01' AND u.company_id = 1 AND u.code = 'HOP';

INSERT IGNORE INTO partners (company_id, code, name, partner_kind, tax_code, address, customer_type, payment_term_days,
                             credit_limit, min_remaining_shelf_life_days, status, created_at, updated_at) VALUES
    (1, 'KH_BV01', 'Bệnh viện Đa khoa mẫu', 'ORGANIZATION', '0100000001', 'Hà Nội', 'PUBLIC_HOSPITAL', 60, 2000000000, 180,
     'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO partners (company_id, code, name, partner_kind, tax_code, address, supplier_payment_term_days,
                             currency_code, status, created_at, updated_at) VALUES
    (1, 'NCC_01', 'Nhà phân phối thiết bị mẫu', 'ORGANIZATION', '0300000001', 'TP. Hồ Chí Minh', 30, 'VND',
     'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO partner_roles (partner_id, role)
SELECT id, 'CUSTOMER' FROM partners WHERE company_id = 1 AND code = 'KH_BV01'
UNION ALL SELECT id, 'TENDER_OWNER' FROM partners WHERE company_id = 1 AND code = 'KH_BV01'
UNION ALL SELECT id, 'SUPPLIER' FROM partners WHERE company_id = 1 AND code = 'NCC_01';

-- Bộ loại hồ sơ mặc định theo D-28 (pháp chế rà soát trước UAT).
INSERT IGNORE INTO compliance_document_types (company_id, code, name, applies_to, has_expiry, required_product_types,
                                              status, created_at, updated_at) VALUES
    (1, 'DKKD',       'Giấy chứng nhận đăng ký kinh doanh',               'COMPANY', FALSE, NULL,                 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'CB_TTBYT',   'Công bố đủ điều kiện mua bán TTBYT',               'COMPANY', FALSE, NULL,                 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'SO_LH',      'Số lưu hành / giấy phép nhập khẩu',                'PRODUCT', TRUE,  'MACHINE,REAGENT',    'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'GP_HOA_CHAT','Giấy phép hóa chất',                               'PRODUCT', TRUE,  NULL,                 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'UQ_HANG',    'Ủy quyền phân phối của hãng',                      'PARTNER', TRUE,  NULL,                 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'CO',         'Giấy chứng nhận xuất xứ (CO)',                     'LOT',     FALSE, NULL,                 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1, 'CQ',         'Giấy chứng nhận chất lượng (CQ)',                  'LOT',     FALSE, NULL,                 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
