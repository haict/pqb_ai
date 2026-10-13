package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.SalesReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa trả hàng bán (TLB) hoặc giảm giá hàng bán (GGB) theo một đơn bán đã ghi sổ của khách (mọi dòng thuộc đơn
 * đó, D-54). Sửa gửi {@code version}; loại, chi nhánh, khách, đơn bán không đổi sau khi lập.
 */
public record SalesReturnRequest(
        @NotNull SalesReturnType documentType,
        @NotNull Long branchId,
        @NotNull Long customerId,
        @NotNull Long salesOrderId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<SalesReturnLineRequest> lines,
        Integer version) {
}
