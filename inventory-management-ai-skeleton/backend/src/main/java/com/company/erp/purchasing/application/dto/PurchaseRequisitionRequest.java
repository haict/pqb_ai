package com.company.erp.purchasing.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa đề nghị mua. {@code requesterId} bỏ trống thì lấy người lập. Sửa gửi {@code version}; chi nhánh không đổi
 * sau khi lập.
 */
public record PurchaseRequisitionRequest(
        @NotNull Long branchId,
        @NotNull LocalDate documentDate,
        LocalDate requiredDate,
        Long requesterId,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<PurchaseRequisitionLineRequest> lines,
        Integer version) {
}
