package com.company.erp.purchasing.application.dto;

import com.company.erp.purchasing.domain.PurchaseReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa trả hàng nhà cung cấp (THN) hoặc giảm giá hàng mua (GGM). {@code purchaseInvoiceId}: bắt buộc với giảm
 * giá; với trả hàng thì có khi hàng đã có hóa đơn (mọi dòng theo hóa đơn đó), trống khi trả hàng chưa có hóa đơn.
 * Sửa gửi {@code version}; loại, chi nhánh, nhà cung cấp, hóa đơn không đổi sau khi lập.
 */
public record PurchaseReturnRequest(
        @NotNull PurchaseReturnType documentType,
        @NotNull Long branchId,
        @NotNull Long supplierId,
        Long purchaseInvoiceId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<PurchaseReturnLineRequest> lines,
        Integer version) {
}
