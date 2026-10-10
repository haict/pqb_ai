package com.company.erp.inventory.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Phiếu kiểm kê một kho (BR-INV-31): DRAFT → COUNTING (chụp tồn, chặn nhập xuất trong kho, D-11) → SUBMITTED →
 * APPROVED → POSTED (sinh phiếu điều chỉnh chênh lệch). Bị từ chối thì quay lại COUNTING để đếm lại.
 */
@Entity
@Table(name = "stock_counts")
public class StockCount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "document_no", nullable = false, length = 50, updatable = false)
    private String documentNo;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StockCountStatus status;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "started_by")
    private Long startedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "adjustment_document_id")
    private Long adjustmentDocumentId;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "stock_count_id", nullable = false)
    @OrderBy("id")
    private List<StockCountLine> lines = new ArrayList<>();

    protected StockCount() {
    }

    public StockCount(Long companyId, Long branchId, Long warehouseId, String documentNo, LocalDate countDate,
                      String note) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.warehouseId = warehouseId;
        this.documentNo = documentNo;
        this.countDate = countDate;
        this.note = note;
        this.status = StockCountStatus.DRAFT;
    }

    public void start(Long actorId, Instant now, List<StockCountLine> snapshot) {
        requireStatus(EnumSet.of(StockCountStatus.DRAFT), "bắt đầu đếm");
        this.lines.clear();
        this.lines.addAll(snapshot);
        this.status = StockCountStatus.COUNTING;
        this.startedBy = actorId;
        this.startedAt = now;
    }

    public void addLine(StockCountLine line) {
        requireStatus(EnumSet.of(StockCountStatus.COUNTING), "nhập số đếm");
        this.lines.add(line);
    }

    public void requireCounting() {
        requireStatus(EnumSet.of(StockCountStatus.COUNTING), "nhập số đếm");
    }

    public void submit(Long actorId, Instant now) {
        requireStatus(EnumSet.of(StockCountStatus.COUNTING), "gửi duyệt");
        boolean uncounted = lines.stream().anyMatch(line -> line.getCountedQuantity() == null);
        if (uncounted) {
            throw BusinessException.invalidField("lines", "Còn dòng chưa nhập số đếm.");
        }
        this.status = StockCountStatus.SUBMITTED;
        this.submittedBy = actorId;
        this.submittedAt = now;
    }

    public void approve(Long actorId, Instant now) {
        requireStatus(EnumSet.of(StockCountStatus.SUBMITTED), "duyệt");
        this.status = StockCountStatus.APPROVED;
        this.approvedBy = actorId;
        this.approvedAt = now;
    }

    /** Từ chối: quay lại đếm, vẫn giữ khóa nhập xuất kho. */
    public void reject() {
        requireStatus(EnumSet.of(StockCountStatus.SUBMITTED), "từ chối");
        this.status = StockCountStatus.COUNTING;
    }

    public void markPosted(Long actorId, Instant now, Long adjustmentDocumentId) {
        requireStatus(EnumSet.of(StockCountStatus.APPROVED), "ghi sổ");
        this.status = StockCountStatus.POSTED;
        this.postedBy = actorId;
        this.postedAt = now;
        this.adjustmentDocumentId = adjustmentDocumentId;
    }

    public void cancel(Long actorId, Instant now, String reason) {
        requireStatus(EnumSet.of(StockCountStatus.DRAFT, StockCountStatus.COUNTING, StockCountStatus.SUBMITTED,
                StockCountStatus.APPROVED), "hủy");
        this.status = StockCountStatus.CANCELLED;
        this.cancelledBy = actorId;
        this.cancelledAt = now;
        this.cancelReason = reason;
    }

    /** Đang giữ khóa nhập xuất của kho (D-11). */
    public boolean holdsWarehouse() {
        return status == StockCountStatus.COUNTING || status == StockCountStatus.SUBMITTED
                || status == StockCountStatus.APPROVED;
    }

    private void requireStatus(Set<StockCountStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                    "Phiếu kiểm kê " + documentNo + " ở trạng thái " + status + ", không " + action + " được.");
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

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public LocalDate getCountDate() {
        return countDate;
    }

    public StockCountStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Long getStartedBy() {
        return startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public Long getPostedBy() {
        return postedBy;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Long getAdjustmentDocumentId() {
        return adjustmentDocumentId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<StockCountLine> getLines() {
        return List.copyOf(lines);
    }

    public List<StockCountLine> mutableLines() {
        return lines;
    }
}
