package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Số hóa đơn điện tử đã phát hành trên MISA (BR-SAL-31): ký hiệu, số, ngày, mã cơ quan thuế. */
public record EinvoiceReferenceRequest(
        @NotBlank @Size(max = 20) String series,
        @NotBlank @Size(max = 30) String number,
        @NotNull LocalDate date,
        @Size(max = 50) String taxAuthorityCode) {
}
