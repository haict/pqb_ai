package com.company.erp.system.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Thông tin chứng từ gửi duyệt, do module sở hữu chứng từ cung cấp.
 *
 * @param amount          giá trị chứng từ để xét ngưỡng; {@code null} khi chưa xác định (chỉ khớp luồng không ngưỡng)
 * @param discountPercent chiết khấu cao nhất (%) để xét ngưỡng; {@code null} khi không áp dụng
 */
public record ApprovalSubmission(
        Long companyId,
        Long branchId,
        String documentType,
        Long documentId,
        String documentNo,
        BigDecimal amount,
        BigDecimal discountPercent) {

    public ApprovalSubmission {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(branchId, "branchId");
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(documentId, "documentId");
    }
}
