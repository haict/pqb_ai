package com.company.erp.sales.application.dto;

import com.company.erp.common.domain.QualityStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dòng trả hàng / giảm giá hàng bán. Trả hàng chưa có hóa đơn: {@code deliveryLineId}; trả hàng đã có hóa đơn và giảm
 * giá: {@code invoiceLineId}. Trả hàng: {@code lotNo} (lô đã xuất của dòng giao, bắt buộc với hàng quản lý lô),
 * {@code quantity} theo đơn vị của dòng, serial (hàng quản lý serial), {@code qualityStatus} khi nhập lại (trống: CÁCH LY,
 * BR-SAL-32). Giảm giá: {@code amount} trước thuế. Đơn giá, thuế do server lấy theo dòng nguồn.
 */
public record SalesReturnLineRequest(
        Long deliveryLineId,
        Long invoiceLineId,
        @Size(max = 50) String lotNo,
        @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        QualityStatus qualityStatus,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers,
        @Size(max = 500) String description) {
}
