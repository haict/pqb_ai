package com.company.erp.ai.domain;

/**
 * Trạng thái tin nhắn. Câu hỏi: PENDING (đang chờ AI) -> ANSWERED hoặc FAILED (cho phép gửi lại cùng
 * {@code Idempotency-Key}). Câu trả lời luôn COMPLETED.
 */
public enum AssistantMessageStatus {
    PENDING,
    ANSWERED,
    FAILED,
    COMPLETED
}
