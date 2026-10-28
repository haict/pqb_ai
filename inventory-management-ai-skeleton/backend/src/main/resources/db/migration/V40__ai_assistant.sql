-- D-64 (2026-10-28), đợt AI-3: trợ lý L1 hỏi đáp cách dùng ERP từ tài liệu hướng dẫn (RAG), có lịch sử hội thoại.
-- Quyền assistant:use cho mọi người dùng nội bộ (Q-10): gán cho mọi vai trò hiện có; vai trò tạo sau thì gán khi lập.

INSERT INTO sys_permissions (code, module, name, sort_order) VALUES
    ('assistant:use', 'AI', 'Dùng trợ lý AI (hỏi đáp cách dùng ERP)', 1110);

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
    JOIN sys_permissions p ON p.code = 'assistant:use'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);

-- Hội thoại thuộc một người dùng; chỉ chủ sở hữu đọc / ghi (docs/14 mục 5.4). Không phải chứng từ, không phải sổ phụ:
-- người dùng tự xóa, tác vụ nền xóa sau ai.chat.retention-days (Q-05).
CREATE TABLE ai_conversations (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    title            VARCHAR(200)  NOT NULL,
    created_at       DATETIME(3)   NOT NULL,
    last_message_at  DATETIME(3)   NOT NULL,
    CONSTRAINT pk_ai_conversations PRIMARY KEY (id),
    CONSTRAINT fk_ai_conversations_companies FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_ai_conversations_sys_users FOREIGN KEY (user_id) REFERENCES sys_users (id),
    INDEX idx_ai_conversations_user_last (user_id, last_message_at),
    INDEX idx_ai_conversations_last (last_message_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tin nhắn: câu hỏi (USER: PENDING -> ANSWERED / FAILED) và câu trả lời (ASSISTANT, reply_to_id = câu hỏi).
-- citations: tên mục tài liệu hướng dẫn được trích, mỗi dòng một mục. Không lưu đoạn tài liệu, prompt.
CREATE TABLE ai_messages (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT        NOT NULL,
    role             VARCHAR(20)   NOT NULL,
    content          TEXT          NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    reply_to_id      BIGINT        NULL,
    citations        TEXT          NULL,
    grounded         BOOLEAN       NULL,
    error_code       VARCHAR(50)   NULL,
    duration_ms      INT           NULL,
    created_at       DATETIME(3)   NOT NULL,
    CONSTRAINT pk_ai_messages PRIMARY KEY (id),
    CONSTRAINT fk_ai_messages_ai_conversations FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_ai_messages_ai_messages FOREIGN KEY (reply_to_id) REFERENCES ai_messages (id) ON DELETE CASCADE,
    INDEX idx_ai_messages_conversation (conversation_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
