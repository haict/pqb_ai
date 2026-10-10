package com.company.erp.inventory.application.dto;

import java.math.BigDecimal;

/** Tiến độ thu hồi theo khách (BR-INV-42): số đã giao và số đã thu hồi về. */
public record RecallCustomerResponse(Long partnerId, String partnerCode, String partnerName, BigDecimal deliveredQuantity,
                                     BigDecimal recoveredQuantity) {
}
