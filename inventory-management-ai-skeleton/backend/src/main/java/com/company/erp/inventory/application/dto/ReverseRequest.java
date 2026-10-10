package com.company.erp.inventory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Đảo chứng từ đã ghi sổ: lý do bắt buộc; ngày hạch toán là ngày đảo (BR-GEN-21), trống thì là hôm nay. */
public record ReverseRequest(@NotBlank @Size(max = 500) String reason, LocalDate postingDate) {
}
