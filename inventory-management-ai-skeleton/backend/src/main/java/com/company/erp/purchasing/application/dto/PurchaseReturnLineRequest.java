package com.company.erp.purchasing.application.dto;

import com.company.erp.common.domain.QualityStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dòng trả hàng / giảm giá hàng mua. Trả hàng chưa có hóa đơn: {@code receiptLineId}; trả hàng đã có hóa đơn và giảm
 * giá: {@code invoiceLineId}. Trả hàng: {@code quantity} theo đơn vị của dòng nguồn, serial (hàng quản lý serial),
 * {@code qualityStatus} của hàng xuất trả (trống: theo kết quả kiểm nhận). Giảm giá: {@code amount} trước thuế.
 * Đơn giá, thuế do server lấy theo dòng nguồn.
 */
public record PurchaseReturnLineRequest(
        Long receiptLineId,
        Long invoiceLineId,
        @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        QualityStatus qualityStatus,
        @Size(max = 500) String description,
        @Size(max = 1000) List<@NotBlank @Size(max = 100) String> serialNumbers) {
}
