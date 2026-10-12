package com.company.erp.accounting.domain;

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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bút toán (BR-ACC-02, 03): sinh từ chứng từ nguồn khi ghi sổ, luôn cân Nợ = Có, không sửa tay. Hủy bằng bút toán
 * đảo (đổi vế từng dòng), bút toán gốc chuyển {@link JournalEntryStatus#REVERSED}.
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "entry_no", nullable = false, updatable = false)
    private String entryNo;

    @Column(name = "posting_date", nullable = false, updatable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, updatable = false)
    private JournalEntryKind entryKind;

    @Column(name = "source_type", nullable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "source_no", nullable = false, updatable = false)
    private String sourceNo;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JournalEntryStatus status;

    @Column(name = "reversal_of_id", updatable = false)
    private Long reversalOfId;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
    @OrderBy("lineNo")
    private List<JournalEntryLine> lines = new ArrayList<>();

    protected JournalEntry() {
    }

    public record Header(Long companyId, Long branchId, String entryNo, LocalDate postingDate,
                         JournalEntryKind entryKind, String sourceType, Long sourceId, String sourceNo,
                         String description, Long reversalOfId, Long actorId, Instant now) {
    }

    /** Tạo bút toán từ các vế đã xác định tài khoản; không cân thì từ chối (BR-ACC-03). */
    public static JournalEntry create(Header header, List<JournalLineDraft> drafts) {
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("Bút toán không có dòng");
        }
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalLineDraft draft : drafts) {
            if (draft.amount() == null || draft.amount().signum() <= 0) {
                throw BusinessException.invalidField("lines", "Số tiền hạch toán phải lớn hơn 0.");
            }
            if (draft.debit()) {
                debit = debit.add(draft.amount());
            } else {
                credit = credit.add(draft.amount());
            }
        }
        if (debit.compareTo(credit) != 0) {
            throw new BusinessException(ErrorCode.UNBALANCED_JOURNAL, "Bút toán không cân: Nợ "
                    + debit.toPlainString() + ", Có " + credit.toPlainString() + ".");
        }
        JournalEntry entry = new JournalEntry();
        entry.companyId = header.companyId();
        entry.branchId = header.branchId();
        entry.entryNo = header.entryNo();
        entry.postingDate = header.postingDate();
        entry.entryKind = header.entryKind();
        entry.sourceType = header.sourceType();
        entry.sourceId = header.sourceId();
        entry.sourceNo = header.sourceNo();
        entry.description = header.description();
        entry.reversalOfId = header.reversalOfId();
        entry.createdBy = header.actorId();
        entry.createdAt = header.now();
        entry.updatedAt = header.now();
        entry.totalAmount = debit;
        entry.status = JournalEntryStatus.POSTED;
        int lineNo = 1;
        for (JournalLineDraft draft : drafts) {
            entry.lines.add(new JournalEntryLine(lineNo++, draft));
        }
        return entry;
    }

    /** Các vế của bút toán đảo: từng dòng đổi vế Nợ / Có. */
    public List<JournalLineDraft> reversalLines() {
        return lines.stream().map(JournalEntryLine::reversed).toList();
    }

    public void markReversed(Instant now) {
        if (status != JournalEntryStatus.POSTED || reversalOfId != null) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Bút toán " + entryNo
                    + " đã đảo hoặc là bút toán đảo.");
        }
        this.status = JournalEntryStatus.REVERSED;
        this.updatedAt = now;
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

    public String getEntryNo() {
        return entryNo;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public JournalEntryKind getEntryKind() {
        return entryKind;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public JournalEntryStatus getStatus() {
        return status;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }

    public List<JournalEntryLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
