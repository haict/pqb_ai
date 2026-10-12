package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.Size;

/** Ý kiến khi duyệt (không bắt buộc). */
public record CommentRequest(@Size(max = 500) String comment) {
}
