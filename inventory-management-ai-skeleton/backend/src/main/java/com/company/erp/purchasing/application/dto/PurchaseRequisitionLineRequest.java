package com.company.erp.purchasing.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng đề nghị mua: hàng, đơn vị tính, số lượng, nhà cung cấp gợi ý, ngày cần. */
public record PurchaseRequisitionLineRequest(
        @NotNull Long productId,
        @NotNull Long unitId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        Long suggestedSupplierId,
        LocalDate requiredDate,
        @Size(max = 500) String note) {
}
