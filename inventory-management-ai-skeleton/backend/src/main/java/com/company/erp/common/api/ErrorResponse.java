package com.company.erp.common.api;

import java.time.Instant;
import java.util.List;

/** Phản hồi lỗi thống nhất (docs/04 mục 1.3). Không chứa stack trace, SQL, tên bảng. */
public record ErrorResponse(
        boolean success,
        String code,
        String message,
        List<FieldError> errors,
        String requestId,
        Instant timestamp) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, List<FieldError> errors, String requestId) {
        return new ErrorResponse(false, code, message, List.copyOf(errors), requestId, Instant.now());
    }
}
