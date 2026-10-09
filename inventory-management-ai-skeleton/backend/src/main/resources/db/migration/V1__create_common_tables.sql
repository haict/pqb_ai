-- Giai đoạn 0: bảng hạ tầng dùng chung (docs/03 mục 4.13-4.15).
-- company_id / branch_id chưa có FK vì bảng companies, branches được tạo ở Giai đoạn 1;
-- FK sẽ được thêm bằng migration của Giai đoạn 1.

CREATE TABLE document_sequences (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    branch_id     BIGINT       NOT NULL,
    document_type VARCHAR(30)  NOT NULL,
    period_key    VARCHAR(10)  NOT NULL,
    next_value    BIGINT       NOT NULL DEFAULT 1,
    version       INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    CONSTRAINT pk_document_sequences PRIMARY KEY (id),
    CONSTRAINT uk_document_sequences_key UNIQUE (company_id, branch_id, document_type, period_key),
    CONSTRAINT ck_document_sequences_next_value CHECK (next_value >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sys_idempotency_keys (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    endpoint        VARCHAR(200) NOT NULL,
    response_status INT          NULL,
    response_body   JSON         NULL,
    created_at      DATETIME(3)  NOT NULL,
    expires_at      DATETIME(3)  NOT NULL,
    CONSTRAINT pk_sys_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uk_sys_idempotency_keys_user_key UNIQUE (user_id, idempotency_key),
    INDEX idx_sys_idempotency_keys_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Chỉ INSERT qua ứng dụng (docs/03 mục 1.4).
CREATE TABLE sys_audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at   DATETIME(3)  NOT NULL,
    actor_user_id BIGINT       NULL,
    action        VARCHAR(50)  NOT NULL,
    entity_type   VARCHAR(60)  NOT NULL,
    entity_id     BIGINT       NULL,
    document_no   VARCHAR(50)  NULL,
    reason        VARCHAR(500) NULL,
    before_value  JSON         NULL,
    after_value   JSON         NULL,
    request_id    VARCHAR(64)  NULL,
    ip_address    VARCHAR(45)  NULL,
    company_id    BIGINT       NULL,
    branch_id     BIGINT       NULL,
    CONSTRAINT pk_sys_audit_logs PRIMARY KEY (id),
    INDEX idx_sys_audit_logs_entity (entity_type, entity_id),
    INDEX idx_sys_audit_logs_actor (actor_user_id, occurred_at),
    INDEX idx_sys_audit_logs_occurred_at (occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
