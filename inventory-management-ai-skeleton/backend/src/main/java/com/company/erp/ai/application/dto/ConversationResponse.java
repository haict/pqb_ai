package com.company.erp.ai.application.dto;

import java.time.Instant;

public record ConversationResponse(Long id, String title, Instant createdAt, Instant lastMessageAt) {
}
