package com.company.erp.common.domain;

import java.util.List;

/**
 * Lỗi nghiệp vụ có mã ổn định. Module cụ thể kế thừa để đặt tên có nghĩa
 * (ví dụ {@code InsufficientStockException}).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<FieldViolation> violations;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<FieldViolation> violations) {
        super(message);
        this.errorCode = errorCode;
        this.violations = List.copyOf(violations);
    }

    /** Lỗi validation nghiệp vụ trên một trường (ví dụ mật khẩu không đạt chính sách). */
    public static BusinessException invalidField(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, List.of(new FieldViolation(field, message)));
    }

    public static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
