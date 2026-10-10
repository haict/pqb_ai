package com.company.erp.masterdata.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Đặt giá một hàng theo đơn vị và bậc số lượng; đã có thì cập nhật giá. */
public record PriceListItemRequest(
        @NotNull Long productId,
        @NotNull Long unitId,
        @PositiveOrZero @Digits(integer = 16, fraction = 3) BigDecimal minQuantity,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice) {
}
