package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dòng hóa đơn bán: hàng hóa theo {@code deliveryLineId} (dòng phiếu giao đã xuất kho), dịch vụ theo
 * {@code orderLineId} (dòng dịch vụ của đơn bán). Số lượng theo đơn vị của dòng nguồn; giá, chiết khấu, thuế theo đơn.
 */
public record SalesInvoiceLineRequest(
        Long deliveryLineId,
        Long orderLineId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @Size(max = 500) String description) {
}
