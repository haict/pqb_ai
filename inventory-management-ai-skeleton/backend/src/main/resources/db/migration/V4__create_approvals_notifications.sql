-- Giai đoạn 1D: ma trận phê duyệt (BR-SYS-11, BR-GEN-05, D-04), tách biệt nhiệm vụ (BR-SYS-14),
-- thông báo trong ứng dụng (BR-SYS-25). Thiết kế: docs/03 mục 4.21.

-- Danh mục loại chứng từ: chế độ duyệt và tách biệt nhiệm vụ theo loại. Module nghiệp vụ thêm loại bằng migration.
CREATE TABLE sys_document_types (
    code                  VARCHAR(30)  NOT NULL,
    module                VARCHAR(30)  NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    approval_mode         VARCHAR(20)  NOT NULL,
    self_approval_allowed BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order            INT          NOT NULL DEFAULT 0,
    updated_by            BIGINT       NULL,
    updated_at            DATETIME(3)  NULL,
    version               INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_sys_document_types PRIMARY KEY (code),
    CONSTRAINT ck_sys_document_types_mode CHECK (approval_mode IN ('NONE', 'CONFIGURED', 'REQUIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Luồng duyệt: áp dụng khi khớp loại chứng từ, chi nhánh (NULL = mọi chi nhánh), ngưỡng giá trị, ngưỡng chiết khấu.
CREATE TABLE approval_workflows (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    company_id           BIGINT        NOT NULL,
    document_type        VARCHAR(30)   NOT NULL,
    name                 VARCHAR(200)  NOT NULL,
    branch_id            BIGINT        NULL,
    min_amount           DECIMAL(19,4) NULL,
    min_discount_percent DECIMAL(9,4)  NULL,
    priority             INT           NOT NULL DEFAULT 0,
    status               VARCHAR(20)   NOT NULL,
    created_by           BIGINT        NULL,
    created_at           DATETIME(3)   NOT NULL,
    updated_by           BIGINT        NULL,
    updated_at           DATETIME(3)   NOT NULL,
    version              INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_workflows PRIMARY KEY (id),
    CONSTRAINT fk_approval_workflows_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_approval_workflows_types FOREIGN KEY (document_type) REFERENCES sys_document_types (code),
    CONSTRAINT fk_approval_workflows_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    INDEX idx_approval_workflows_lookup (company_id, document_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE approval_workflow_steps (
    workflow_id   BIGINT      NOT NULL,
    step_no       INT         NOT NULL,
    approver_type VARCHAR(10) NOT NULL,
    role_id       BIGINT      NULL,
    user_id       BIGINT      NULL,
    CONSTRAINT pk_approval_workflow_steps PRIMARY KEY (workflow_id, step_no),
    CONSTRAINT fk_approval_workflow_steps_workflows FOREIGN KEY (workflow_id) REFERENCES approval_workflows (id),
    CONSTRAINT fk_approval_workflow_steps_roles FOREIGN KEY (role_id) REFERENCES sys_roles (id),
    CONSTRAINT fk_approval_workflow_steps_users FOREIGN KEY (user_id) REFERENCES sys_users (id),
    CONSTRAINT ck_approval_workflow_steps_approver CHECK (
        (approver_type = 'ROLE' AND role_id IS NOT NULL AND user_id IS NULL)
        OR (approver_type = 'USER' AND user_id IS NOT NULL AND role_id IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Ủy quyền duyệt có thời hạn (BR-SYS-11). document_type NULL = mọi loại chứng từ.
CREATE TABLE approval_delegations (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    company_id        BIGINT       NOT NULL,
    delegator_user_id BIGINT       NOT NULL,
    delegate_user_id  BIGINT       NOT NULL,
    document_type     VARCHAR(30)  NULL,
    valid_from        DATETIME(3)  NOT NULL,
    valid_to          DATETIME(3)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    reason            VARCHAR(500) NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME(3)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_delegations PRIMARY KEY (id),
    CONSTRAINT fk_approval_delegations_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_approval_delegations_delegator FOREIGN KEY (delegator_user_id) REFERENCES sys_users (id),
    CONSTRAINT fk_approval_delegations_delegate FOREIGN KEY (delegate_user_id) REFERENCES sys_users (id),
    CONSTRAINT fk_approval_delegations_types FOREIGN KEY (document_type) REFERENCES sys_document_types (code),
    CONSTRAINT ck_approval_delegations_users CHECK (delegator_user_id <> delegate_user_id),
    CONSTRAINT ck_approval_delegations_period CHECK (valid_from < valid_to),
    INDEX idx_approval_delegations_delegate (delegate_user_id, status, valid_to),
    INDEX idx_approval_delegations_delegator (delegator_user_id, status, valid_to)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Yêu cầu duyệt của một chứng từ. Mỗi chứng từ có tối đa một yêu cầu đang chờ (pending_flag + unique).
CREATE TABLE approval_requests (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    branch_id        BIGINT        NOT NULL,
    document_type    VARCHAR(30)   NOT NULL,
    document_id      BIGINT        NOT NULL,
    document_no      VARCHAR(50)   NULL,
    amount           DECIMAL(19,4) NULL,
    discount_percent DECIMAL(9,4)  NULL,
    workflow_id      BIGINT        NOT NULL,
    current_step     INT           NOT NULL,
    total_steps      INT           NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    requested_by     BIGINT        NOT NULL,
    requested_at     DATETIME(3)   NOT NULL,
    completed_at     DATETIME(3)   NULL,
    pending_flag     TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) STORED,
    version          INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_requests PRIMARY KEY (id),
    CONSTRAINT uk_approval_requests_pending UNIQUE (document_type, document_id, pending_flag),
    CONSTRAINT fk_approval_requests_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_approval_requests_branches FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_approval_requests_types FOREIGN KEY (document_type) REFERENCES sys_document_types (code),
    CONSTRAINT fk_approval_requests_workflows FOREIGN KEY (workflow_id) REFERENCES approval_workflows (id),
    CONSTRAINT fk_approval_requests_users FOREIGN KEY (requested_by) REFERENCES sys_users (id),
    INDEX idx_approval_requests_status (status, company_id, branch_id),
    INDEX idx_approval_requests_document (document_type, document_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Ảnh chụp các bước duyệt lúc gửi duyệt: sửa luồng duyệt sau đó không đổi yêu cầu đang chờ.
CREATE TABLE approval_request_steps (
    request_id    BIGINT      NOT NULL,
    step_no       INT         NOT NULL,
    approver_type VARCHAR(10) NOT NULL,
    role_id       BIGINT      NULL,
    user_id       BIGINT      NULL,
    CONSTRAINT pk_approval_request_steps PRIMARY KEY (request_id, step_no),
    CONSTRAINT fk_approval_request_steps_requests FOREIGN KEY (request_id) REFERENCES approval_requests (id),
    INDEX idx_approval_request_steps_role (role_id),
    INDEX idx_approval_request_steps_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Lịch sử xử lý yêu cầu duyệt; chỉ INSERT.
CREATE TABLE approval_actions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    request_id           BIGINT       NOT NULL,
    step_no              INT          NULL,
    action               VARCHAR(20)  NOT NULL,
    actor_user_id        BIGINT       NOT NULL,
    on_behalf_of_user_id BIGINT       NULL,
    comment              VARCHAR(500) NULL,
    acted_at             DATETIME(3)  NOT NULL,
    CONSTRAINT pk_approval_actions PRIMARY KEY (id),
    CONSTRAINT fk_approval_actions_requests FOREIGN KEY (request_id) REFERENCES approval_requests (id),
    CONSTRAINT fk_approval_actions_actor FOREIGN KEY (actor_user_id) REFERENCES sys_users (id),
    CONSTRAINT fk_approval_actions_on_behalf FOREIGN KEY (on_behalf_of_user_id) REFERENCES sys_users (id),
    INDEX idx_approval_actions_request (request_id, acted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Thông báo trong ứng dụng. event_key chống tạo trùng khi xử lý lại (BR-GEN-54).
CREATE TABLE notifications (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    event_key  VARCHAR(150)  NOT NULL,
    category   VARCHAR(30)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    message    VARCHAR(1000) NULL,
    link_type  VARCHAR(30)   NULL,
    link_id    BIGINT        NULL,
    created_at DATETIME(3)   NOT NULL,
    read_at    DATETIME(3)   NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uk_notifications_event UNIQUE (user_id, event_key),
    CONSTRAINT fk_notifications_users FOREIGN KEY (user_id) REFERENCES sys_users (id),
    INDEX idx_notifications_user (user_id, read_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('approval-workflow:view',   'SYSTEM', 'Xem luồng duyệt, cấu hình loại chứng từ', 190),
    ('approval-workflow:manage', 'SYSTEM', 'Cấu hình luồng duyệt, loại chứng từ',     191),
    ('access:grant-any',         'SYSTEM', 'Gán mọi vai trò, quyền (kể cả quyền mình không có; không tự gán cho mình)', 195);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON
    (r.code IN ('SYSTEM_ADMIN', 'DIRECTOR') AND p.code IN ('approval-workflow:view', 'approval-workflow:manage')) OR
    (r.code = 'SYSTEM_ADMIN' AND p.code = 'access:grant-any') OR
    (r.code = 'CHIEF_ACCOUNTANT' AND p.code = 'approval-workflow:view');
