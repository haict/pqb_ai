package com.company.erp.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Cấu hình số chứng từ của một loại chứng từ, có hiệu lực từ {@code effectiveFrom} (BR-SYS-10, docs/03 mục 4.21).
 * Cấu hình đã có hiệu lực không bị sửa; thay đổi tạo dòng mới hiệu lực từ chu kỳ sau.
 */
@Entity
@Table(name = "document_number_formats")
public class DocumentNumberFormat extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private String documentType;

    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix;

    @Column(name = "sequence_length", nullable = false)
    private int sequenceLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_cycle", nullable = false, length = 10)
    private NumberResetCycle resetCycle;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    protected DocumentNumberFormat() {
    }

    public DocumentNumberFormat(Long companyId, String documentType, DocumentNumberPattern pattern,
                                LocalDate effectiveFrom) {
        this.companyId = companyId;
        this.documentType = documentType;
        this.effectiveFrom = effectiveFrom;
        change(pattern);
    }

    /** Chỉ dùng cho cấu hình chưa có hiệu lực. */
    public void change(DocumentNumberPattern pattern) {
        this.prefix = pattern.prefix();
        this.sequenceLength = pattern.sequenceLength();
        this.resetCycle = pattern.resetCycle();
    }

    public DocumentNumberPattern pattern() {
        return new DocumentNumberPattern(prefix, sequenceLength, resetCycle);
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

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }
}
