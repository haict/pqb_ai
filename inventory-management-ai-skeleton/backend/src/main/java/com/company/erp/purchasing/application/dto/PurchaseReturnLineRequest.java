package com.company.erp.purchasing.application.dto;

import com.company.erp.common.domain.QualityStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dòng trả hàng / giảm giá hàng mua theo dòng đơn mua {@code orderLineId}. Trả hàng: {@code quantity} theo đơn vị của
 * dòng đơn mua, serial (hàng quản lý serial), {@code qualityStatus} của hàng xuất trả (trống: đạt). Giảm giá:
 * {@code amount} trước thuế. Đơn giá, thuế do server lấy theo dòng đơn mua.
 */
public record PurchaseReturnLineRequest(
        @NotNull Long orderLineId,
        @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        QualityStatus qualityStatus,
        @Size(max = 500) String description,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers) {
}
