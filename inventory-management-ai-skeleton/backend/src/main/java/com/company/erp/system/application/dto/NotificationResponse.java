package com.company.erp.system.application.dto;

import java.time.Instant;

/** Thông báo; {@code linkType}/{@code linkId} trỏ tới chứng từ liên quan (loại chứng từ, ID). */
public record NotificationResponse(
        Long id,
        String category,
        String title,
        String message,
        String linkType,
        Long linkId,
        Instant createdAt,
        Instant readAt) {
}
