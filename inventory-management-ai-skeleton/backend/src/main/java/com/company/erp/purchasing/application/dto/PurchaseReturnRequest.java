package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa trả hàng nhà cung cấp (THN) hoặc giảm giá hàng mua (GGM) theo một đơn mua đã ghi sổ của nhà cung cấp (mọi
 * dòng thuộc đơn đó, D-54). Sửa gửi {@code version}; loại, chi nhánh, nhà cung cấp, đơn mua không đổi sau khi lập.
 */
public record PurchaseReturnRequest(
        @NotNull PurchaseReturnType documentType,
        @NotNull Long branchId,
        @NotNull Long supplierId,
        @NotNull Long purchaseOrderId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<PurchaseReturnLineRequest> lines,
        Integer version) {
}
