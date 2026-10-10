package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.MovementDirection;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng chứng từ kho theo đơn vị giao dịch; server quy đổi về đơn vị cơ sở theo hệ số có hiệu lực (BR-MD-21).
 * Lô nhập mới: {@code lotNo} + hạn dùng; lô đã có: {@code lotId}. Hàng quản lý serial: danh sách serial.
 */
public record InventoryLineRequest(
        @NotNull Long productId,
        @NotNull Long unitId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        MovementDirection direction,
        Long locationId,
        Long destinationLocationId,
        Long lotId,
        @Size(max = 50) String lotNo,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        QualityStatus qualityStatus,
        QualityStatus targetQualityStatus,
        @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitCost,
        @Size(max = 500) String lotOverrideReason,
        @Size(max = 500) String note,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers) {
}
