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
 * Chứng từ bù trừ công nợ (DTCN, BR-AR-04): trả trước với hóa đơn, hoặc phải thu với phải trả cùng đối tượng. Bỏ đối
 * trừ bằng đảo chứng từ.
 */
@Entity
@Table(name = "debt_offset_documents")
public class DebtOffsetDocument extends FinanceDocument {

    public static final String TYPE_CODE = "DTCN";

    @Enumerated(EnumType.STRING)
    @Column(name = "offset_type", nullable = false, updatable = false)
    private OffsetType offsetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", updatable = false)
    private DebtType debtType;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("lineNo")
    private List<DebtOffsetLine> lines = new ArrayList<>();

    protected DebtOffsetDocument() {
    }

    /** {@code debtType} bắt buộc với bù trừ trả trước, bỏ trống với bù trừ phải thu - phải trả. */
    public DebtOffsetDocument(Long companyId, Long branchId, String documentNo, OffsetType offsetType,
                              DebtType debtType) {
        super(companyId, branchId, documentNo);
        if ((offsetType == OffsetType.PREPAYMENT) != (debtType != null)) {
            throw BusinessException.invalidField("debtType", offsetType == OffsetType.PREPAYMENT
                    ? "Bù trừ trả trước phải chọn loại công nợ." : "Bù trừ phải thu - phải trả không chọn loại công nợ.");
        }
        this.offsetType = offsetType;
        this.debtType = debtType;
        this.totalAmount = BigDecimal.ZERO;
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }


    /**
     * Thay nội dung (chỉ DRAFT). {@code total} là số tiền bù trừ: bằng tổng dòng với bù trừ trả trước,
     * bằng tổng dòng phải thu và bằng tổng dòng phải trả với bù trừ phải thu - phải trả (kiểm tra ở tầng ứng dụng).
     */
    public void replaceContent(LocalDate documentDate, LocalDate postingDate, String description, Long partnerId,
                               BigDecimal total, List<DebtOffsetLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Chứng từ phải có ít nhất một dòng.");
        }
        replaceHeader(documentDate, postingDate, description);
        this.partnerId = partnerId;
        this.totalAmount = total;
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

    public OffsetType getOffsetType() {
        return offsetType;
    }

    public DebtType getDebtType() {
        return debtType;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<DebtOffsetLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
