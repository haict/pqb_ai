package com.company.erp.finance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Đảo chứng từ đã ghi sổ: lý do bắt buộc; ngày đảo mặc định hôm nay (BR-GEN-21). */
public record ReverseRequest(@NotBlank @Size(max = 500) String reason, LocalDate postingDate) {
}
