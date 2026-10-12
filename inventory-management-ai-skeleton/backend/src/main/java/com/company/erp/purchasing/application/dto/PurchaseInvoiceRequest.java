package com.company.erp.purchasing.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa hóa đơn mua. {@code sellerTaxCode} trống thì lấy MST nhà cung cấp; {@code dueDate} trống thì = ngày hóa
 * đơn + hạn thanh toán của đơn mua (hoặc của nhà cung cấp). Sửa gửi {@code version}; chi nhánh, nhà cung cấp không
 * đổi sau khi lập.
 */
public record PurchaseInvoiceRequest(
        @NotNull Long branchId,
        @NotNull Long supplierId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 20) String sellerTaxCode,
        @NotBlank @Size(max = 20) String invoiceSeries,
        @NotBlank @Size(max = 30) String invoiceNo,
        @NotNull LocalDate invoiceDate,
        LocalDate dueDate,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<PurchaseInvoiceLineRequest> lines,
        Integer version) {
}
