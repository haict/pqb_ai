package com.company.erp.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Đề xuất lô theo FEFO (BR-INV-21): lô hết hạn sớm nhất trước. */
public record FefoAllocationResponse(Long lotId, String lotNo, LocalDate expiryDate, Long locationId,
                                     BigDecimal availableQuantity, BigDecimal suggestedQuantity) {
}
