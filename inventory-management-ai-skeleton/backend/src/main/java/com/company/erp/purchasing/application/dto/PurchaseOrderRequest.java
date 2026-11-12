package com.company.erp.purchasing.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa đơn mua (D-54). {@code postingDate} trống thì bằng ngày chứng từ. Hóa đơn của nhà cung cấp: ký hiệu, số,
 * ngày đều không bắt buộc (D-75); MST người bán luôn lấy theo nhà cung cấp, không nhập tay. {@code paymentTermDays} trống
 * thì lấy số ngày nợ nhà cung cấp; {@code dueDate} trống thì = ngày hóa đơn (hoặc ngày hạch toán) + số ngày nợ.
 * Kho nhận theo từng dòng (D-76). {@code currencyCode} trống là VND (P1 chỉ VND, D-24). Sửa gửi {@code version}; chi nhánh không đổi sau khi lập.
 */
public record PurchaseOrderRequest(
        @NotNull Long branchId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @NotNull Long supplierId,
        LocalDate expectedDate,
        @PositiveOrZero @Max(3650) Integer paymentTermDays,
        @Pattern(regexp = "[A-Z]{3}") String currencyCode,
        @Size(max = 20) String invoiceSeries,
        @Size(max = 30) String invoiceNo,
        LocalDate invoiceDate,
        LocalDate dueDate,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<PurchaseOrderLineRequest> lines,
        Integer version) {
}
