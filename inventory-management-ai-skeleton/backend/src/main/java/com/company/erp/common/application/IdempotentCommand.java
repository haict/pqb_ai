package com.company.erp.common.application;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Định danh một lệnh cần chống xử lý trùng.
 *
 * @param userId         người gửi (lấy từ security context, không lấy từ request)
 * @param key            giá trị header {@code Idempotency-Key}
 * @param endpoint       hành động, ví dụ {@code POST /api/v1/inventory-issues/15/post}
 * @param requestPayload nội dung yêu cầu dùng để phát hiện dùng lại key cho yêu cầu khác
 */
public record IdempotentCommand(Long userId, String key, String endpoint, Object requestPayload) {

    public static final String HEADER = "Idempotency-Key";

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9-]{8,100}");

    public IdempotentCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(endpoint, "endpoint");
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}
