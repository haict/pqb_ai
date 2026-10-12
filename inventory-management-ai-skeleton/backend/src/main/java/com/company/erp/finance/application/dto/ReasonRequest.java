package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do bắt buộc cho từ chối, hủy (api.md mục 3). */
public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
}
