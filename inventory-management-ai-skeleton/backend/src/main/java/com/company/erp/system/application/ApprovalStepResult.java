package com.company.erp.system.application;

/**
 * Kết quả duyệt một bước.
 *
 * @param completed {@code true}: đã duyệt bước cuối, module chuyển chứng từ sang APPROVED
 */
public record ApprovalStepResult(Long requestId, boolean completed, int approvedSteps, int totalSteps) {
}
