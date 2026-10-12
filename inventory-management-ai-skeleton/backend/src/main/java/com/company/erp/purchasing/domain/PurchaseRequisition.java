package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Đề nghị mua hàng (DNM, BR-PUR-01): hàng, số lượng, ngày cần; đơn mua tham chiếu dòng đề nghị. */
@Entity
@Table(name = "purchase_requisitions")
public class PurchaseRequisition extends PurchaseDocument {

    public static final String TYPE_CODE = "DNM";

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @Column(name = "requester_id")
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private RequisitionSource sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "source_no", updatable = false)
    private String sourceNo;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "requisition_id", nullable = false)
    @OrderBy("lineNo")
    private List<PurchaseRequisitionLine> lines = new ArrayList<>();

    protected PurchaseRequisition() {
    }

    public PurchaseRequisition(Long companyId, Long branchId, String documentNo, RequisitionSource sourceType) {
        super(companyId, branchId, documentNo);
        this.sourceType = sourceType;
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return null;
    }

    @Override
    protected boolean fulfilled() {
        return lines.stream().allMatch(line -> line.remainingQuantity().signum() == 0);
    }

    @Override
    protected boolean hasProgress() {
        return lines.stream().anyMatch(line -> line.getOrderedQuantity().signum() > 0);
    }

    public void replaceContent(LocalDate documentDate, LocalDate requiredDate, Long requesterId, String description,
                               List<PurchaseRequisitionLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Đề nghị mua phải có ít nhất một dòng.");
        }
        replaceHeader(documentDate, description);
        this.requiredDate = requiredDate;
        this.requesterId = requesterId;
        for (int i = 0; i < newLines.size(); i++) {
            if (i < lines.size()) {
                lines.get(i).copyFrom(newLines.get(i));
            } else {
                lines.add(newLines.get(i));
            }
        }
        while (lines.size() > newLines.size()) {
            lines.remove(lines.size() - 1);
        }
    }

    /** Đơn mua duyệt / hủy cập nhật số đã đặt của dòng đề nghị (chỉ khi đề nghị còn mở). */
    public void addOrdered(Long lineId, BigDecimal delta, String field) {
        if (delta.signum() > 0) {
            requireStatus(OPEN, "đặt mua theo");
        }
        line(lineId).addOrdered(delta, field);
        refreshProgress();
    }

    public void close(Long actorId, Instant now, String reason) {
        requireStatus(OPEN, "đóng");
        lines.forEach(PurchaseRequisitionLine::closeRemaining);
        markClosed(actorId, now, reason);
    }

    public PurchaseRequisitionLine line(Long lineId) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Dòng đề nghị mua #" + lineId + " không thuộc "
                        + getDocumentNo()));
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    /** Chứng từ nguồn (đơn bán) của đề nghị mua tự sinh. */
    public void linkSource(Long sourceId, String sourceNo) {
        this.sourceId = sourceId;
        this.sourceNo = sourceNo;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public RequisitionSource getSourceType() {
        return sourceType;
    }

    public List<PurchaseRequisitionLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
