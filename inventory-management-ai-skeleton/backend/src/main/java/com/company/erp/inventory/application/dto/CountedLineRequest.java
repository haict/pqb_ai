package com.company.erp.inventory.application.dto;

import com.company.erp.common.domain.QualityStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Số đếm một dòng: {@code lineId} có sẵn (dòng chụp tồn), hoặc dòng phát sinh (hàng tìm thấy ngoài sổ) với
 * hàng, vị trí, lô / serial đã có trong danh mục lô / serial, trạng thái.
 */
public record CountedLineRequest(
        Long lineId,
        Long productId,
        Long locationId,
        Long lotId,
        Long serialId,
        QualityStatus qualityStatus,
        @NotNull @PositiveOrZero @Digits(integer = 16, fraction = 3) BigDecimal countedQuantity,
        @Size(max = 500) String note) {
}
