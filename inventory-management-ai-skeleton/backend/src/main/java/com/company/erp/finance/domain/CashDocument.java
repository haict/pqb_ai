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
import java.util.Objects;

/**
 * Chứng từ thu chi (BR-CSH-02, ADR-13): phiếu thu, phiếu chi, giấy báo có, ủy nhiệm chi, chuyển tiền nội bộ. Nhóm A:
 * DRAFT → SUBMITTED → APPROVED → POSTED; SUBMITTED → REJECTED → sửa → gửi lại; hủy khi chưa ghi sổ; đảo khi đã ghi
 * sổ (vòng đời ở {@link FinanceDocument}). Loại chứng từ, nội dung không đổi sau khi lập.
 */
@Entity
@Table(name = "cash_documents")
public class CashDocument extends FinanceDocument {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    private CashDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private CashPurpose purpose;

    @Column(name = "cash_account_id", nullable = false)
    private Long cashAccountId;

    @Column(name = "target_cash_account_id")
    private Long targetCashAccountId;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_mode")
    private AllocationMode allocationMode;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "prepayment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal prepaymentAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("lineNo")
    private List<CashDocumentLine> lines = new ArrayList<>();

    protected CashDocument() {
    }

    public CashDocument(Long companyId, Long branchId, String documentNo, CashDocumentType documentType,
                        CashPurpose purpose) {
        super(companyId, branchId, documentNo);
        if (purpose.direction() != documentType.direction()) {
            throw BusinessException.invalidField("purpose", "Nội dung không phù hợp với loại chứng từ "
                    + documentType + ".");
        }
        this.documentType = documentType;
        this.purpose = purpose;
        this.allocatedAmount = BigDecimal.ZERO;
        this.prepaymentAmount = BigDecimal.ZERO;
    }

    @Override
    public String typeCode() {
        return documentType.name();
    }

    @Override
    public BigDecimal approvalAmount() {
        return totalAmount;
    }

    /**
     * Nội dung chứng từ; {@code amount} là số tiền thu / chi của thu tiền khách, trả nhà cung cấp, chuyển tiền (thu
     * chi khác lấy tổng các dòng).
     */
    public record Content(LocalDate documentDate, LocalDate postingDate, Long cashAccountId, Long targetCashAccountId,
                          Long partnerId, String counterpartyName, AllocationMode allocationMode, BigDecimal amount,
                          String description) {
    }

    /** Thay nội dung (chỉ khi DRAFT / REJECTED) và kiểm tra cấu trúc theo nội dung chứng từ. */
    public void replaceContent(Content content, List<CashDocumentLine> newLines) {
        requireStatus(EDITABLE, "sửa");
        BigDecimal total = switch (purpose) {
            case CUSTOMER_RECEIPT, SUPPLIER_PAYMENT, ADVANCE_RETURN -> debtTotal(content, newLines);
            case OTHER_RECEIPT, OTHER_PAYMENT -> otherTotal(newLines);
            case INTERNAL_TRANSFER -> transferTotal(content, newLines);
            case EMPLOYEE_ADVANCE -> advanceTotal(content, newLines);
        };
        replaceHeader(content.documentDate(), content.postingDate(), content.description());
        this.cashAccountId = content.cashAccountId();
        this.targetCashAccountId = purpose == CashPurpose.INTERNAL_TRANSFER ? content.targetCashAccountId() : null;
        this.partnerId = content.partnerId();
        this.counterpartyName = content.counterpartyName();
        this.allocationMode = purpose.settlesDebt() ? content.allocationMode() : null;
        this.totalAmount = total;
        // Giữ dòng cũ theo số thứ tự rồi thêm / bớt phần dư: thay toàn bộ sẽ vi phạm unique (document_id, line_no).
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

    private static BigDecimal debtTotal(Content content, List<CashDocumentLine> newLines) {
        if (content.partnerId() == null) {
            throw BusinessException.invalidField("partnerId", "Chọn đối tượng thanh toán.");
        }
        if (content.allocationMode() == null) {
            throw BusinessException.invalidField("allocationMode", "Chọn cách phân bổ.");
        }
        BigDecimal amount = requirePositive(content.amount());
        if (content.allocationMode() == AllocationMode.AUTO && !newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Phân bổ tự động không nhập dòng phân bổ.");
        }
        if (newLines.stream().anyMatch(line -> line.getDebtItemId() == null)) {
            throw BusinessException.invalidField("lines", "Dòng phân bổ phải chọn hóa đơn.");
        }
        if (sum(newLines).compareTo(amount) > 0) {
            throw BusinessException.invalidField("lines", "Tổng phân bổ vượt số tiền chứng từ.");
        }
        return amount;
    }

    private static BigDecimal advanceTotal(Content content, List<CashDocumentLine> newLines) {
        if (content.partnerId() == null) {
            throw BusinessException.invalidField("partnerId", "Chọn nhân viên tạm ứng.");
        }
        if (!newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Tạm ứng không có dòng.");
        }
        return requirePositive(content.amount());
    }

    private static BigDecimal otherTotal(List<CashDocumentLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Chứng từ phải có ít nhất một dòng.");
        }
        if (newLines.stream().anyMatch(line -> line.getAccountId() == null)) {
            throw BusinessException.invalidField("lines", "Dòng thu chi khác phải chọn tài khoản đối ứng.");
        }
        return sum(newLines);
    }

    private static BigDecimal transferTotal(Content content, List<CashDocumentLine> newLines) {
        if (content.targetCashAccountId() == null) {
            throw BusinessException.invalidField("targetCashAccountId", "Chọn quỹ / tài khoản nhận.");
        }
        if (Objects.equals(content.targetCashAccountId(), content.cashAccountId())) {
            throw BusinessException.invalidField("targetCashAccountId", "Quỹ nhận phải khác quỹ chuyển.");
        }
        if (!newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Chuyển tiền nội bộ không có dòng.");
        }
        return requirePositive(content.amount());
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw BusinessException.invalidField("amount", "Số tiền phải lớn hơn 0.");
        }
        return amount;
    }

    private static BigDecimal sum(List<CashDocumentLine> newLines) {
        return newLines.stream().map(CashDocumentLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Kết quả phân bổ lúc ghi sổ: phần vào hóa đơn và phần trả trước. */
    public void recordSettlement(BigDecimal allocated, BigDecimal prepayment) {
        if (allocated.add(prepayment).compareTo(totalAmount) != 0) {
            throw new IllegalStateException("Phân bổ " + getDocumentNo() + " không khớp số tiền chứng từ");
        }
        this.allocatedAmount = allocated;
        this.prepaymentAmount = prepayment;
    }

    public CashDocumentType getDocumentType() {
        return documentType;
    }

    public CashPurpose getPurpose() {
        return purpose;
    }

    public Long getCashAccountId() {
        return cashAccountId;
    }

    public Long getTargetCashAccountId() {
        return targetCashAccountId;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public AllocationMode getAllocationMode() {
        return allocationMode;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public BigDecimal getPrepaymentAmount() {
        return prepaymentAmount;
    }

    public List<CashDocumentLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
