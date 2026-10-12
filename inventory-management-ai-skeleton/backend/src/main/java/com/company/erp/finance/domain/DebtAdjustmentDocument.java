package com.company.erp.finance.domain;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Điều chỉnh công nợ (DCCN, BR-AR-07: xử lý chênh lệch đối chiếu, có duyệt) hoặc thanh toán tạm ứng (TTTU, BR-CSH-04:
 * chi phí thực tế trừ vào tạm ứng, chỉ dòng giảm). Một đối tượng, một loại công nợ mỗi chứng từ.
 */
@Entity
@Table(name = "debt_adjustment_documents")
public class DebtAdjustmentDocument extends FinanceDocument {

    public static final String ADJUSTMENT = "DCCN";
    public static final String ADVANCE_SETTLEMENT = "TTTU";

    @Column(name = "document_type", nullable = false, updatable = false)
    private String documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", nullable = false, updatable = false)
    private DebtType debtType;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "reconciliation_id")
    private Long reconciliationId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("lineNo")
    private List<DebtAdjustmentLine> lines = new ArrayList<>();

    protected DebtAdjustmentDocument() {
    }

    public DebtAdjustmentDocument(Long companyId, Long branchId, String documentNo, String documentType,
                                  DebtType debtType) {
        super(companyId, branchId, documentNo);
        if (!ADJUSTMENT.equals(documentType) && !ADVANCE_SETTLEMENT.equals(documentType)) {
            throw BusinessException.invalidField("documentType", "Loại chứng từ phải là DCCN hoặc TTTU.");
        }
        if (ADVANCE_SETTLEMENT.equals(documentType) && debtType != DebtType.ADVANCE) {
            throw BusinessException.invalidField("debtType", "Thanh toán tạm ứng chỉ áp dụng cho công nợ tạm ứng.");
        }
        this.documentType = documentType;
        this.debtType = debtType;
        this.totalAmount = BigDecimal.ZERO;
    }

    @Override
    public String typeCode() {
        return documentType;
    }

    @Override
    public BigDecimal approvalAmount() {
        return totalAmount;
    }

    /** Thay nội dung (chỉ DRAFT / REJECTED); thanh toán tạm ứng chỉ có dòng giảm. */
    public void replaceContent(LocalDate documentDate, LocalDate postingDate, String description, Long partnerId,
                               Long reconciliationId, List<DebtAdjustmentLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Chứng từ phải có ít nhất một dòng.");
        }
        if (ADVANCE_SETTLEMENT.equals(documentType)
                && newLines.stream().anyMatch(line -> line.getDirection() != AdjustmentDirection.DECREASE)) {
            throw BusinessException.invalidField("lines", "Thanh toán tạm ứng chỉ có dòng trừ vào tạm ứng.");
        }
        replaceHeader(documentDate, postingDate, description);
        this.partnerId = partnerId;
        this.reconciliationId = reconciliationId;
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
        this.totalAmount = newLines.stream().map(DebtAdjustmentLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getDocumentType() {
        return documentType;
    }

    public DebtType getDebtType() {
        return debtType;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public Long getReconciliationId() {
        return reconciliationId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<DebtAdjustmentLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
