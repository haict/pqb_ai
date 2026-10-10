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
 * Yêu cầu duyệt một chứng từ, đi lần lượt từng bước của luồng duyệt đã chụp lúc gửi (BR-SYS-11).
 * Mỗi chứng từ có tối đa một yêu cầu đang chờ (unique trên cột sinh {@code pending_flag}).
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

    public ApprovalRequest(Long companyId, Long branchId, String documentType, Long documentId, String documentNo,
                           BigDecimal amount, BigDecimal discountPercent, ApprovalWorkflow workflow, Long requestedBy,
                           Instant now) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentNo = documentNo;
        this.amount = amount;
        this.discountPercent = discountPercent;
        this.workflowId = workflow.getId();
        workflow.getSteps().forEach(step -> steps.add(step.copy()));
        this.totalSteps = steps.size();
        this.currentStep = steps.get(0).getStepNo();
        this.status = ApprovalRequestStatus.PENDING;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
    }

    public ApprovalStepDefinition currentStepDefinition() {
        requirePending();
        return steps.stream().filter(step -> step.getStepNo() == currentStep).findFirst()
                .orElseThrow(() -> new IllegalStateException("Không có bước duyệt " + currentStep));
    }

    /** Duyệt bước hiện tại; trả {@code true} khi đã duyệt bước cuối (yêu cầu hoàn tất). */
    public boolean approveCurrentStep(Instant now) {
        requirePending();
        int index = stepIndex(currentStep);
        if (index == steps.size() - 1) {
            status = ApprovalRequestStatus.APPROVED;
            completedAt = now;
            return true;
        }
        currentStep = steps.get(index + 1).getStepNo();
        return false;
    }

    public void reject(Instant now) {
        requirePending();
        status = ApprovalRequestStatus.REJECTED;
        completedAt = now;
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
