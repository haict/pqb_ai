package com.company.erp.sales.application.dto;

import com.company.erp.sales.domain.ComplaintDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Quyết định xử lý hàng lỗi; đổi / trả hàng nên kèm {@code salesReturnId} (chứng từ trả hàng của cùng khách). */
public record ResolveComplaintRequest(
        @NotNull ComplaintDecision decision,
        @Size(max = 1000) String note,
        Long salesReturnId) {
}
