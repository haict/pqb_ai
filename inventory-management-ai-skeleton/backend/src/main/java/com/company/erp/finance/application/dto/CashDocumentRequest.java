package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.AllocationMode;
import com.company.erp.finance.domain.CashDocumentType;
import com.company.erp.finance.domain.CashPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa chứng từ thu chi. {@code amount}: số tiền thu / chi của thu tiền khách, trả nhà cung cấp, chuyển tiền;
 * thu chi khác lấy tổng các dòng. Sửa phải gửi {@code version}; loại, nội dung, chi nhánh không đổi sau khi lập.
 */
public record CashDocumentRequest(
        @NotNull Long branchId,
        @NotNull CashDocumentType documentType,
        @NotNull CashPurpose purpose,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @NotNull Long cashAccountId,
        Long targetCashAccountId,
        Long partnerId,
        @Size(max = 255) String counterpartyName,
        AllocationMode allocationMode,
        @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 500) String description,
        @Valid @Size(max = 200) List<CashDocumentLineRequest> lines,
        Integer version) {

    public List<CashDocumentLineRequest> safeLines() {
        return lines == null ? List.of() : lines;
    }
}
