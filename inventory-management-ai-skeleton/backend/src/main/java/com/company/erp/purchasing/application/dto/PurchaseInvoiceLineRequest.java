package com.company.erp.purchasing.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dòng hóa đơn mua. Hàng hóa: {@code receiptLineId} (dòng phiếu nhận hàng đã nhập kho), số lượng theo đơn vị của dòng
 * đó. Dịch vụ không qua kho: {@code productId}, {@code unitId}, có thể {@code orderLineId} (dòng đơn mua dịch vụ).
 * {@code taxRateId} trống thì theo dòng đơn mua. Thành tiền, thuế do server tính.
 */
public record PurchaseInvoiceLineRequest(
        Long receiptLineId,
        Long orderLineId,
        Long productId,
        Long unitId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
        Long taxRateId,
        Boolean promotional,
        @Size(max = 500) String description) {
}
