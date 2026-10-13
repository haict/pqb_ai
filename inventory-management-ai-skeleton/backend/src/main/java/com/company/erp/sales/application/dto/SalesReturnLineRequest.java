package com.company.erp.sales.application.dto;

import com.company.erp.common.domain.QualityStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dòng trả hàng / giảm giá hàng bán theo dòng đơn bán {@code orderLineId}. Trả hàng: {@code lotNo} (lô đã xuất của
 * dòng, bắt buộc với hàng quản lý lô), {@code quantity} theo đơn vị của dòng, serial (hàng quản lý serial),
 * {@code qualityStatus} khi nhập lại (trống: CÁCH LY, BR-SAL-32). Giảm giá: {@code amount} trước thuế. Đơn giá, thuế
 * do server lấy theo dòng đơn.
 */
public record SalesReturnLineRequest(
        @NotNull Long orderLineId,
        @Size(max = 50) String lotNo,
        @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        QualityStatus qualityStatus,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers,
        @Size(max = 500) String description) {
}
