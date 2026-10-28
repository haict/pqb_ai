package com.company.erp.ai.application.dto;

import jakarta.validation.constraints.Size;

/** Tạo hội thoại; {@code title} trống thì lấy câu hỏi đầu tiên làm tiêu đề. */
public record ConversationRequest(@Size(max = 200) String title) {
}
