package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Đảo chứng từ: lý do bắt buộc; {@code postingDate} trống là hôm nay. */
public record ReverseRequest(@NotBlank @Size(max = 500) String reason, LocalDate postingDate) {
}
