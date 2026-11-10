-- D-72: tùy biến giao diện theo từng khách hàng (một mã nguồn, mỗi khách một database).
-- Hai bảng chỉ sửa trực tiếp bằng SQL tại khách (docs/10-deployment.md); ứng dụng chỉ đọc.
-- Ẩn chức năng chỉ phục vụ giao diện, không thay phân quyền: API vẫn kiểm tra quyền như cũ.

-- Ẩn / đổi tên menu và nút. Mặc định bật hết; không có dòng cho một khóa = bật, giữ tên mặc định.
-- feature_key: 'group:<nhóm menu>' | 'menu:<đường dẫn màn hình>' | 'action:<mã nút>'.
CREATE TABLE sys_ui_features (
    feature_key  VARCHAR(150) NOT NULL,
    feature_type VARCHAR(20)  NOT NULL,
    module       VARCHAR(30)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    custom_label VARCHAR(150) NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_sys_ui_features PRIMARY KEY (feature_key),
    CONSTRAINT ck_sys_ui_features_type CHECK (feature_type IN ('MENU_GROUP', 'MENU_ITEM', 'ACTION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Thiết lập ứng dụng dạng khóa / giá trị. setting_value NULL = dùng mặc định có sẵn trong ứng dụng.
CREATE TABLE sys_app_settings (
    setting_key   VARCHAR(80)  NOT NULL,
    setting_value VARCHAR(500) NULL,
    description   VARCHAR(500) NOT NULL,
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_sys_app_settings PRIMARY KEY (setting_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_app_settings (setting_key, setting_value, description) VALUES
('branding.app-name', NULL,
 'Tên hiển thị cạnh logo, trang đăng nhập, tiêu đề tab trình duyệt (tối đa 150 ký tự). NULL = tên mặc định.'),
('branding.logo-file', NULL,
 'Tên file logo (PNG, JPEG, WebP) trong thư mục erp.branding.directory, ví dụ logo.png. NULL = logo mặc định.');

-- Danh sách đầy đủ menu và nút được phép ẩn tại thời điểm D-72. Không có: nhóm Quản trị hệ thống và các màn hình
-- Công ty, Chi nhánh, Người dùng, Vai trò, Bản quyền (không cho ẩn để quản trị không tự khóa mình).
INSERT INTO sys_ui_features (feature_key, feature_type, module, name, sort_order) VALUES
('group:purchasing', 'MENU_GROUP', 'purchasing', 'Nhóm menu Mua hàng', 100),
('menu:/purchasing/orders', 'MENU_ITEM', 'purchasing', 'Đơn mua hàng', 101),
('menu:/purchasing/returns', 'MENU_ITEM', 'purchasing', 'Trả hàng / giảm giá', 102),
('menu:/purchasing/cost-allocations', 'MENU_ITEM', 'purchasing', 'Phân bổ chi phí mua', 103),
('menu:/purchasing/ledger', 'MENU_ITEM', 'purchasing', 'Sổ chi tiết mua hàng', 104),

('group:sales', 'MENU_GROUP', 'sales', 'Nhóm menu Bán hàng', 200),
('menu:/sales/orders', 'MENU_ITEM', 'sales', 'Đơn bán hàng', 201),
('menu:/sales/returns', 'MENU_ITEM', 'sales', 'Trả hàng / giảm giá bán', 202),
('menu:/sales/complaints', 'MENU_ITEM', 'sales', 'Hàng lỗi khách báo', 203),
('menu:/sales/report', 'MENU_ITEM', 'sales', 'Báo cáo doanh số', 204),
('menu:/sales/ledger', 'MENU_ITEM', 'sales', 'Sổ chi tiết bán hàng', 205),

('group:inventory', 'MENU_GROUP', 'inventory', 'Nhóm menu Kho', 300),
('menu:/inventory/stock', 'MENU_ITEM', 'inventory', 'Tồn kho', 301),
('menu:/inventory/documents', 'MENU_ITEM', 'inventory', 'Chứng từ kho', 302),
('menu:/inventory/opening-stock', 'MENU_ITEM', 'inventory', 'Tồn kho đầu kỳ', 303),
('menu:/inventory/lots', 'MENU_ITEM', 'inventory', 'Lô, serial', 304),
('menu:/inventory/stock-counts', 'MENU_ITEM', 'inventory', 'Kiểm kê', 305),
('menu:/inventory/recalls', 'MENU_ITEM', 'inventory', 'Thu hồi', 306),
('menu:/inventory/stock-issue-policies', 'MENU_ITEM', 'inventory', 'Chính sách xuất vượt tồn', 307),

('group:finance', 'MENU_GROUP', 'finance', 'Nhóm menu Công nợ, quỹ', 400),
('menu:/finance/cash-documents', 'MENU_ITEM', 'finance', 'Thu chi, ngân hàng', 401),
('menu:/finance/cash-book', 'MENU_ITEM', 'finance', 'Sổ quỹ, dòng tiền', 402),
('menu:/finance/cash-counts', 'MENU_ITEM', 'finance', 'Kiểm kê quỹ', 403),
('menu:/finance/receivables', 'MENU_ITEM', 'finance', 'Công nợ phải thu', 404),
('menu:/finance/payables', 'MENU_ITEM', 'finance', 'Công nợ phải trả', 405),
('menu:/finance/advances', 'MENU_ITEM', 'finance', 'Công nợ tạm ứng', 406),
('menu:/finance/debt-offsets', 'MENU_ITEM', 'finance', 'Bù trừ công nợ', 407),
('menu:/finance/debt-adjustments', 'MENU_ITEM', 'finance', 'Điều chỉnh công nợ', 408),
('menu:/finance/advance-settlements', 'MENU_ITEM', 'finance', 'Thanh toán tạm ứng', 409),
('menu:/finance/reconciliations', 'MENU_ITEM', 'finance', 'Đối chiếu công nợ', 410),
('menu:/finance/debt-openings', 'MENU_ITEM', 'finance', 'Công nợ đầu kỳ', 411),
('menu:/finance/cash-accounts', 'MENU_ITEM', 'finance', 'Quỹ, tài khoản ngân hàng', 412),

('group:accounting', 'MENU_GROUP', 'accounting', 'Nhóm menu Kế toán', 500),
('menu:/accounting/journal-entries', 'MENU_ITEM', 'accounting', 'Bút toán', 501),
('menu:/accounting/accounts', 'MENU_ITEM', 'accounting', 'Hệ thống tài khoản', 502),
('menu:/accounting/posting-rules', 'MENU_ITEM', 'accounting', 'Quy tắc hạch toán', 503),

('group:reporting', 'MENU_GROUP', 'reporting', 'Nhóm menu Báo cáo', 600),
('menu:/reports/stock-card', 'MENU_ITEM', 'reporting', 'Thẻ kho', 601),
('menu:/reports/inventory-movement', 'MENU_ITEM', 'reporting', 'Nhập - xuất - tồn', 602),
('menu:/reports/stock-alerts', 'MENU_ITEM', 'reporting', 'Tồn lâu, ngoại lệ, thiếu hồ sơ', 603),
('menu:/reports/traceability', 'MENU_ITEM', 'reporting', 'Truy xuất lô / serial', 604),
('menu:/reports/purchase-order-progress', 'MENU_ITEM', 'reporting', 'Tình hình thực hiện đơn mua', 605),
('menu:/reports/purchase-analysis', 'MENU_ITEM', 'reporting', 'Giá mua, hàng lỗi theo NCC', 606),
('menu:/reports/sales-order-progress', 'MENU_ITEM', 'reporting', 'Tình hình thực hiện đơn bán', 607),
('menu:/reports/sales-journal', 'MENU_ITEM', 'reporting', 'Sổ nhật ký bán hàng', 608),
('menu:/reports/stock-backlog', 'MENU_ITEM', 'reporting', 'Tồn so với đặt hàng chưa giao', 609),
('menu:/reports/cash-flow-forecast', 'MENU_ITEM', 'reporting', 'Dòng tiền dự kiến', 610),

('group:masterdata', 'MENU_GROUP', 'masterdata', 'Nhóm menu Danh mục', 700),
('menu:/masterdata/products', 'MENU_ITEM', 'masterdata', 'Hàng hóa', 701),
('menu:/masterdata/categories', 'MENU_ITEM', 'masterdata', 'Nhóm hàng', 702),
('menu:/masterdata/partners', 'MENU_ITEM', 'masterdata', 'Đối tác', 703),
('menu:/masterdata/warehouses', 'MENU_ITEM', 'masterdata', 'Kho, vị trí', 704),
('menu:/masterdata/price-lists', 'MENU_ITEM', 'masterdata', 'Bảng giá', 705),
('menu:/masterdata/compliance-documents', 'MENU_ITEM', 'masterdata', 'Hồ sơ, giấy phép', 706),
('menu:/masterdata/catalogs', 'MENU_ITEM', 'masterdata', 'Danh mục chung', 707),

('menu:/system/departments', 'MENU_ITEM', 'system', 'Phòng ban', 801),
('menu:/system/login-logs', 'MENU_ITEM', 'system', 'Nhật ký đăng nhập', 802),
('menu:/system/fiscal-periods', 'MENU_ITEM', 'system', 'Kỳ kế toán, kỳ kho', 803),
('menu:/system/parameters', 'MENU_ITEM', 'system', 'Tham số hệ thống', 804),
('menu:/system/document-number-formats', 'MENU_ITEM', 'system', 'Số chứng từ', 805),

('action:ai-assistant', 'ACTION', 'ai', 'Nút Trợ lý trên thanh tiêu đề', 900),
('action:purchase-order.ocr', 'ACTION', 'purchasing', 'Nút Nhận dạng chứng từ (OCR) trên form đơn mua', 901),
('action:sales-order.ocr', 'ACTION', 'sales', 'Nút Nhận dạng chứng từ (OCR) trên form đơn bán', 902),
('action:excel-import', 'ACTION', 'common', 'Mọi nút Nhập từ Excel (danh mục, đầu kỳ, dòng đơn mua / đơn bán)', 903),
('action:excel-export', 'ACTION', 'common', 'Mọi lựa chọn Tải về / Xuất Excel (.xlsx); PDF, Word, In vẫn giữ', 904);
