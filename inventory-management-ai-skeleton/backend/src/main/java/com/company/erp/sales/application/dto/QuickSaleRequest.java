package com.company.erp.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Bán nhanh (BR-SAL-42): một lần thao tác lập đơn bán, phiếu giao (xuất kho, khách nhận ngay ngày chứng từ) và hóa đơn
 * bán, mỗi chứng từ riêng, liên kết. {@code createInvoice} trống: có lập hóa đơn. Đơn phải duyệt (giá / tín dụng) thì
 * dừng ở đơn chờ duyệt.
 */
public record QuickSaleRequest(
        @NotNull Long branchId,
        @NotNull Long customerId,
        @NotNull Long warehouseId,
        @NotNull LocalDate documentDate,
        @Size(max = 500) String deliveryAddress,
        @Size(max = 200) String receiverName,
        Boolean createInvoice,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 100) List<SalesLineRequest> lines) {
}
