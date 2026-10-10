package com.company.erp.system.application;

/**
 * Kết quả gửi duyệt.
 *
 * @param approvalRequired {@code false}: không cần duyệt, module chuyển chứng từ sang đã duyệt ngay (BR-GEN-05)
 * @param requestId        yêu cầu duyệt đã tạo, {@code null} khi không cần duyệt
 */
public record ApprovalSubmitResult(boolean approvalRequired, Long requestId) {

    public static ApprovalSubmitResult notRequired() {
        return new ApprovalSubmitResult(false, null);
    }
}
