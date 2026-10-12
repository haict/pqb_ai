package com.company.erp.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa phiếu giao hàng theo đơn bán đã duyệt; kho xuất là kho của đơn. Địa chỉ, người nhận trống: theo đơn.
 * Sửa gửi {@code version}; đơn bán không đổi sau khi lập.
 */
public record SalesDeliveryRequest(
        @NotNull Long orderId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 500) String deliveryAddress,
        @Size(max = 200) String receiverName,
        @Size(max = 50) String receiverPhone,
        @Size(max = 200) String shipperName,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<SalesDeliveryLineRequest> lines,
        Integer version) {
}
