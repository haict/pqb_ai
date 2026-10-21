package com.company.erp.system.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Yêu cầu duyệt một chứng từ lập trước D-61 (BR-SYS-11 cũ), các bước duyệt chụp lúc gửi. Từ D-61 không tạo yêu cầu
 * mới; chỉ đọc lịch sử và hủy yêu cầu còn treo khi chứng từ bị hủy.
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private String documentType;

    @Column(name = "document_id", nullable = false, updatable = false)
    private Long documentId;

    @Column(name = "document_no", length = 50, updatable = false)
    private String documentNo;

    @Column(name = "amount", precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "discount_percent", precision = 9, scale = 4, updatable = false)
    private BigDecimal discountPercent;

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "total_steps", nullable = false, updatable = false)
    private int totalSteps;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalRequestStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @ElementCollection
    @CollectionTable(name = "approval_request_steps", joinColumns = @JoinColumn(name = "request_id"))
    @OrderBy("stepNo")
    private List<ApprovalStepDefinition> steps = new ArrayList<>();

    protected ApprovalRequest() {
    }

    public void cancel(Instant now) {
        requirePending();
        status = ApprovalRequestStatus.CANCELLED;
        completedAt = now;
    }

    /** Thứ tự (từ 1) của bước hiện tại trong luồng, để hiển thị "bước 2/3". */
    public int currentStepPosition() {
        return stepIndex(currentStep) + 1;
    }

    private int stepIndex(int stepNo) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStepNo() == stepNo) {
                return i;
            }
        }
        throw new IllegalStateException("Không có bước duyệt " + stepNo);
    }

    private void requirePending() {
        if (status != ApprovalRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Yêu cầu duyệt đã kết thúc.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public ApprovalRequestStatus getStatus() {
        return status;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<ApprovalStepDefinition> getSteps() {
        return List.copyOf(steps);
    }
}
