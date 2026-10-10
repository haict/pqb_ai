package com.company.erp.system.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.RecordStatus;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Luồng duyệt theo loại chứng từ (BR-SYS-11): áp dụng khi khớp chi nhánh (trống = mọi chi nhánh), giá trị từ
 * {@code minAmount}, chiết khấu từ {@code minDiscountPercent}. Nhiều luồng cùng khớp thì chọn luồng ưu tiên cao nhất,
 * rồi luồng cụ thể nhất.
 */
@Entity
@Table(name = "approval_workflows")
public class ApprovalWorkflow extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private String documentType;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "min_discount_percent", precision = 9, scale = 4)
    private BigDecimal minDiscountPercent;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    @ElementCollection
    @CollectionTable(name = "approval_workflow_steps", joinColumns = @JoinColumn(name = "workflow_id"))
    @OrderBy("stepNo")
    private List<ApprovalStepDefinition> steps = new ArrayList<>();

    protected ApprovalWorkflow() {
    }

    public ApprovalWorkflow(Long companyId, String documentType) {
        this.companyId = companyId;
        this.documentType = documentType;
        this.status = RecordStatus.ACTIVE;
    }

    public void define(String name, Long branchId, BigDecimal minAmount, BigDecimal minDiscountPercent, int priority,
                       List<ApprovalStepDefinition> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Luồng duyệt phải có ít nhất một bước");
        }
        this.name = name;
        this.branchId = branchId;
        this.minAmount = minAmount;
        this.minDiscountPercent = minDiscountPercent;
        this.priority = priority;
        this.steps.clear();
        this.steps.addAll(steps);
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    /** Luồng có áp dụng cho chứng từ này không. Giá trị/chiết khấu chưa xác định thì không khớp ngưỡng. */
    public boolean matches(Long documentBranchId, BigDecimal amount, BigDecimal discountPercent) {
        if (status != RecordStatus.ACTIVE) {
            return false;
        }
        if (branchId != null && !branchId.equals(documentBranchId)) {
            return false;
        }
        if (minAmount != null && (amount == null || amount.compareTo(minAmount) < 0)) {
            return false;
        }
        return minDiscountPercent == null
                || (discountPercent != null && discountPercent.compareTo(minDiscountPercent) >= 0);
    }

    /** Độ cụ thể dùng phân xử khi cùng ưu tiên: có chi nhánh, có ngưỡng thì cụ thể hơn. */
    public int specificity() {
        return (branchId != null ? 4 : 0) + (minAmount != null ? 2 : 0) + (minDiscountPercent != null ? 1 : 0);
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getName() {
        return name;
    }

    public Long getBranchId() {
        return branchId;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMinDiscountPercent() {
        return minDiscountPercent;
    }

    public int getPriority() {
        return priority;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public List<ApprovalStepDefinition> getSteps() {
        return List.copyOf(steps);
    }
}
