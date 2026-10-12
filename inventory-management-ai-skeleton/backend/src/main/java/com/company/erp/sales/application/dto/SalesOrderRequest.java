package com.company.erp.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa đơn bán. {@code paymentTermDays} trống: theo khách. {@code reserveStock}: giữ hàng khi duyệt (D-37).
 * {@code paymentTerms}: lịch thu tiền nhiều đợt (tổng 100%), có thể trống. Sửa gửi {@code version}; chi nhánh, khách
 * không đổi sau khi lập.
 */
public record SalesOrderRequest(
        @NotNull Long branchId,
        @NotNull Long customerId,
        @NotNull Long warehouseId,
        @NotNull LocalDate documentDate,
        @Size(max = 200) String contactName,
        @Size(max = 500) String deliveryAddress,
        @Size(max = 200) String receiverName,
        @Size(max = 50) String receiverPhone,
        LocalDate expectedDate,
        @PositiveOrZero @Max(3650) Integer paymentTermDays,
        Boolean reserveStock,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<SalesLineRequest> lines,
        @Valid @Size(max = 12) List<PaymentTermRequest> paymentTerms,
        Integer version) {
}
