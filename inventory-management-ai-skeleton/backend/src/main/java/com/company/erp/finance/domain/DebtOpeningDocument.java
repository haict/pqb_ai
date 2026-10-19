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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Chứng từ công nợ đầu kỳ (CNDK, BR-MIG-01): số còn phải thu / phải trả / tạm ứng theo đối tượng và hạn thanh toán khi
 * chuyển từ hệ thống cũ. Không qua duyệt (D-60): DRAFT → POSTED; chứng từ đã duyệt theo luồng cũ vẫn ghi sổ được. Đảo
 * khi các khoản công nợ chưa được thanh toán.
 */
@Entity
@Table(name = "debt_opening_documents")
public class DebtOpeningDocument extends FinanceDocument {

    public static final String TYPE_CODE = "CNDK";
    private static final Set<FinanceDocumentStatus> POSTABLE =
            EnumSet.of(FinanceDocumentStatus.DRAFT, FinanceDocumentStatus.APPROVED);

    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", nullable = false, updatable = false)
    private DebtType debtType;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("lineNo")
    private List<DebtOpeningLine> lines = new ArrayList<>();

    protected DebtOpeningDocument() {
    }

    public DebtOpeningDocument(Long companyId, Long branchId, String documentNo, DebtType debtType) {
        super(companyId, branchId, documentNo);
        this.debtType = debtType;
        this.totalAmount = BigDecimal.ZERO;
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return totalAmount;
    }

    @Override
    public void markPosted(Long actorId, Instant now) {
        markPosted(POSTABLE, actorId, now);
    }

    /** Thay nội dung (chỉ khi DRAFT / REJECTED). */
    public void replaceContent(LocalDate documentDate, LocalDate postingDate, String description,
                               List<DebtOpeningLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Chứng từ phải có ít nhất một dòng.");
        }
        replaceHeader(documentDate, postingDate, description);
        // Giữ dòng cũ theo số thứ tự rồi thêm / bớt phần dư: Hibernate chèn dòng mới trước khi xóa dòng cũ, thay
        // toàn bộ sẽ vi phạm unique (document_id, line_no).
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
        this.totalAmount = newLines.stream().map(DebtOpeningLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public DebtType getDebtType() {
        return debtType;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<DebtOpeningLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
