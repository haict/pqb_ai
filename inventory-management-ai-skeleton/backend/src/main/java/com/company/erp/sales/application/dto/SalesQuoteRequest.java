package com.company.erp.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa báo giá. {@code validUntil} trống: ngày báo giá + {@code sales.quote-validity-days}. Sửa gửi
 * {@code version}; chi nhánh, khách không đổi sau khi lập.
 */
public record SalesQuoteRequest(
        @NotNull Long branchId,
        @NotNull Long customerId,
        @NotNull LocalDate documentDate,
        @Size(max = 200) String contactName,
        LocalDate validUntil,
        @Size(max = 500) String deliveryTerms,
        @Size(max = 500) String warrantyTerms,
        @Size(max = 500) String paymentTerms,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<SalesLineRequest> lines,
        Integer version) {
}
