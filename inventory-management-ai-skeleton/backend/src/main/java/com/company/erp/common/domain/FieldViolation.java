package com.company.erp.common.domain;

/** Lỗi gắn với một trường dữ liệu, trả cho client trong {@code errors[]}. */
public record FieldViolation(String field, String message) {
}
