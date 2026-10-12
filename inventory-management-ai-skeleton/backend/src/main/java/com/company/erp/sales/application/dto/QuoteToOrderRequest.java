package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Chuyển báo giá khách đã chấp nhận thành đơn bán: kho xuất, ngày giao dự kiến, địa chỉ giao, giữ hàng khi duyệt. */
public record QuoteToOrderRequest(
        @NotNull Long warehouseId,
        LocalDate expectedDate,
        @Size(max = 500) String deliveryAddress,
        Boolean reserveStock) {
}
