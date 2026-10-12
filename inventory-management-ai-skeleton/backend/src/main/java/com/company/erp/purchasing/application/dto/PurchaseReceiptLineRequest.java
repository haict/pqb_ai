package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.InspectionResult;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng phiếu nhận hàng: dòng đơn mua, số lượng theo đơn vị của dòng đơn mua, kết quả kiểm nhận (lỗi phải có lý do),
 * lô / hạn dùng / serial / vị trí cho dòng nhập kho (đạt hoặc cách ly).
 */
public record PurchaseReceiptLineRequest(
        @NotNull Long orderLineId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @NotNull InspectionResult inspectionResult,
        @Size(max = 500) String rejectReason,
        Long locationId,
        @Size(max = 50) String lotNo,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers,
        @Size(max = 500) String note) {
}
