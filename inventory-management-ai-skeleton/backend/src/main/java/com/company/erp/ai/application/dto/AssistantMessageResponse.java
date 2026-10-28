package com.company.erp.ai.application.dto;

import com.company.erp.ai.domain.AssistantMessageRole;
import com.company.erp.ai.domain.AssistantMessageStatus;
import java.time.Instant;
import java.util.List;

/**
 * Tin nhắn trong hội thoại. Câu trả lời: {@code citations} là tên mục tài liệu hướng dẫn được trích; {@code grounded}
 * false khi tài liệu không có nội dung phù hợp. Nội dung do AI tạo, giao diện ghi rõ cần kiểm tra lại (docs/14 mục 5.4).
 */
public record AssistantMessageResponse(Long id, AssistantMessageRole role, String content,
                                       AssistantMessageStatus status, List<String> citations, Boolean grounded,
                                       Instant createdAt) {

    public AssistantMessageResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
