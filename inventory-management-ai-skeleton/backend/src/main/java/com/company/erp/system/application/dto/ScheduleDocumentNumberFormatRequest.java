package com.company.erp.system.application.dto;

import com.company.erp.common.domain.NumberResetCycle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Đặt cấu hình số chứng từ. Loại chứng từ đã cấp số: hiệu lực từ chu kỳ sau; chưa cấp số: hiệu lực ngay.
 * Ngày hiệu lực do server quyết định.
 */
public record ScheduleDocumentNumberFormatRequest(
        @NotNull Long companyId,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,30}", message = "Mã loại gồm 1-30 ký tự A-Z, 0-9, _")
        String documentType,
        @NotBlank @Pattern(regexp = "[A-Z0-9]{1,10}", message = "Tiền tố gồm 1-10 ký tự A-Z, 0-9") String prefix,
        @NotNull @Min(3) @Max(10) Integer sequenceLength,
        @NotNull NumberResetCycle resetCycle) {
}
