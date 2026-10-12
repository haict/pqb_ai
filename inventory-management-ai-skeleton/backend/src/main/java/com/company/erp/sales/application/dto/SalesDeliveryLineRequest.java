package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dòng phiếu giao theo dòng đơn bán, số lượng theo đơn vị của dòng đơn. {@code lotNo} trống: FEFO; chọn lô khác lô
 * FEFO phải có {@code lotOverrideReason} (BR-INV-21). Hàng quản lý serial: danh sách serial.
 */
public record SalesDeliveryLineRequest(
        @NotNull Long orderLineId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @Size(max = 50) String lotNo,
        @Size(max = 500) String lotOverrideReason,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers,
        @Size(max = 500) String note) {
}
