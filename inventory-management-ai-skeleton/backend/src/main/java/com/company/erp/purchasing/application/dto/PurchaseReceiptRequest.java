package com.company.erp.purchasing.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa phiếu nhận hàng theo đơn mua. Chi nhánh, nhà cung cấp, kho lấy theo đơn mua. {@code postingDate} trống
 * thì bằng ngày chứng từ. Sửa gửi {@code version}; đơn mua không đổi sau khi lập.
 */
public record PurchaseReceiptRequest(
        @NotNull Long purchaseOrderId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 50) String deliveryNoteNo,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<PurchaseReceiptLineRequest> lines,
        Integer version) {
}
