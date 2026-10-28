-- D-64 (2026-10-27), đợt AI-1b: nhận dạng chứng từ (OCR) điền gợi ý vào form đơn mua.
-- Quyền ocr:use gán cho vai trò đang có quyền lập đơn mua hoặc đơn bán (Q-10).

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('ocr:use', 'AI', 'Nhận dạng chứng từ (OCR) để điền gợi ý vào form', 1100);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM sys_role_permissions rp
    JOIN sys_permissions p_old ON p_old.id = rp.permission_id
    JOIN sys_permissions p_new ON p_new.code = 'ocr:use'
WHERE p_old.code IN ('purchase-order:create', 'sales-order:create')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = p_new.id);

-- Nhật ký sử dụng OCR: chỉ metadata (BR-AI-10). Không lưu file, nội dung nhận dạng, MST hay số tiền.
-- Không phải sổ phụ: được xóa theo vòng đời khi có quyết định thời hạn lưu.
CREATE TABLE ai_ocr_requests (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    company_id     BIGINT       NOT NULL,
    branch_id      BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    document_type  VARCHAR(30)  NOT NULL,
    file_sha256    VARCHAR(64)  NULL,
    file_size      BIGINT       NOT NULL,
    page_count     INT          NULL,
    status         VARCHAR(20)  NOT NULL,
    error_code     VARCHAR(50)  NULL,
    duration_ms    INT          NOT NULL,
    request_id     VARCHAR(64)  NULL,
    created_at     DATETIME(3)  NOT NULL,
    CONSTRAINT pk_ai_ocr_requests PRIMARY KEY (id),
    CONSTRAINT fk_ai_ocr_requests_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_ai_ocr_requests_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_ai_ocr_requests_sys_users FOREIGN KEY (user_id) REFERENCES sys_users (id),
    INDEX idx_ai_ocr_requests_company_created (company_id, created_at),
    INDEX idx_ai_ocr_requests_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
