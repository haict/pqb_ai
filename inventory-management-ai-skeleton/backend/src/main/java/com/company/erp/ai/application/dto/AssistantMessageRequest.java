package com.company.erp.ai.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Câu hỏi gửi trợ lý; độ dài tối đa theo {@code ai.chat.max-message-chars} (lỗi ASSISTANT_MESSAGE_TOO_LONG). */
public record AssistantMessageRequest(@NotBlank String content) {
}
