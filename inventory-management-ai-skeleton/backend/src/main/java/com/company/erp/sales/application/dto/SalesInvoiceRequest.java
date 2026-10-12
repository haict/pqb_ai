package com.company.erp.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa hóa đơn bán. {@code invoiceDate} trống: ngày chứng từ; {@code dueDate} trống: ngày hóa đơn + hạn thanh toán
 * của đơn (hoặc của khách). Thông tin người mua trống: theo khách. Sửa gửi {@code version}.
 */
public record SalesInvoiceRequest(
        @NotNull Long branchId,
        @NotNull Long customerId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        LocalDate invoiceDate,
        LocalDate dueDate,
        @Size(max = 255) String buyerName,
        @Size(max = 20) String buyerTaxCode,
        @Size(max = 500) String buyerAddress,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<SalesInvoiceLineRequest> lines,
        Integer version) {
}
