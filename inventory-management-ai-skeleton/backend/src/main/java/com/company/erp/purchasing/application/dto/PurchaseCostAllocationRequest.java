package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.CostAllocationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa phân bổ chi phí mua hàng (BR-PUR-12): các dòng chi phí và các phiếu nhận hàng đã nhập kho nhận phân bổ
 * (mọi dòng nhập kho của các phiếu đó). Server tính phân bổ; sửa gửi {@code version}.
 */
public record PurchaseCostAllocationRequest(
        @NotNull Long branchId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @NotNull CostAllocationMethod method,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 50) List<CostAllocationSourceRequest> sources,
        @NotEmpty @Size(max = 50) List<@NotNull Long> receiptIds,
        Integer version) {
}
