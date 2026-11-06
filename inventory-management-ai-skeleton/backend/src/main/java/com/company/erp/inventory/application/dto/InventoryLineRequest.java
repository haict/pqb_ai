package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.ItemCondition;
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
 * {@code itemCondition}: máy mới / cũ, chỉ cho dòng nhập hàng thuộc nhóm máy (BR-INV-43); trống thì giữ tình trạng của
 * serial / lô đã có, serial / lô mới là máy mới.
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
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers,
        ItemCondition itemCondition) {

    /** Dòng không khai báo tình trạng máy (chứng từ nguồn module khác, nhập Excel không có cột tình trạng). */
    public InventoryLineRequest(Long productId, Long unitId, BigDecimal quantity, MovementDirection direction,
                                Long locationId, Long destinationLocationId, Long lotId, String lotNo,
                                LocalDate manufactureDate, LocalDate expiryDate, QualityStatus qualityStatus,
                                QualityStatus targetQualityStatus, BigDecimal unitCost, String lotOverrideReason,
                                String note, List<String> serialNumbers) {
        this(productId, unitId, quantity, direction, locationId, destinationLocationId, lotId, lotNo, manufactureDate,
                expiryDate, qualityStatus, targetQualityStatus, unitCost, lotOverrideReason, note, serialNumbers, null);
    }
}
