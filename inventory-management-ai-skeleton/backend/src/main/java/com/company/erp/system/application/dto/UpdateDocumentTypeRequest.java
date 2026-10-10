package com.company.erp.system.application.dto;

import com.company.erp.system.domain.ApprovalMode;
import jakarta.validation.constraints.NotNull;

/** Đổi chế độ duyệt và tách biệt nhiệm vụ (BR-SYS-14) của loại chứng từ. */
public record UpdateDocumentTypeRequest(
        @NotNull ApprovalMode approvalMode,
        @NotNull Boolean selfApprovalAllowed,
        @NotNull Integer version) {
}
