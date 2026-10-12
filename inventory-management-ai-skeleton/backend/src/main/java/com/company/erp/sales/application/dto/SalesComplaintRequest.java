package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tiếp nhận hàng lỗi khách báo (BR-SAL-34): khách, phiếu giao / hóa đơn gốc (tùy chọn), hàng, lô, serial, số lượng,
 * mô tả lỗi. Sửa gửi {@code version} khi chưa kiểm tra.
 */
public record SalesComplaintRequest(
        @NotNull Long branchId,
        @NotNull Long customerId,
        @NotNull LocalDate receivedDate,
        Long deliveryId,
        Long salesInvoiceId,
        @NotNull Long productId,
        @Size(max = 50) String lotNo,
        @Size(max = 100) String serialNo,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @NotBlank @Size(max = 1000) String description,
        Integer version) {
}
