package com.company.erp.system.application;

import java.util.Objects;

/**
 * Nội dung một thông báo trong ứng dụng.
 *
 * @param eventKey khóa sự kiện: cùng người nhận + cùng khóa chỉ tạo một thông báo (chạy lại không trùng, BR-GEN-54)
 * @param category nhóm thông báo, ví dụ {@code APPROVAL}
 * @param linkType loại chứng từ liên quan (mã loại chứng từ), có thể {@code null}
 * @param linkId   ID chứng từ liên quan, có thể {@code null}
 */
public record NotificationMessage(
        String eventKey,
        String category,
        String title,
        String message,
        String linkType,
        Long linkId) {

    private static final int MAX_TITLE = 200;
    private static final int MAX_MESSAGE = 1000;

    public NotificationMessage {
        Objects.requireNonNull(eventKey, "eventKey");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(title, "title");
        title = title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
        message = message != null && message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
    }
}
