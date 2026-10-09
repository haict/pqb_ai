package com.company.erp.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Bộ đếm số chứng từ theo (công ty, chi nhánh, loại chứng từ, kỳ) - docs/03 mục 4.13.
 * Chỉ được cấp số khi dòng đã bị khóa {@code SELECT ... FOR UPDATE}.
 */
@Entity
@Table(name = "document_sequences")
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "period_key", nullable = false, length = 10)
    private String periodKey;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentSequence() {
    }

    /** Trả số hiện tại và tăng bộ đếm. */
    public long allocate(Instant now) {
        long allocated = nextValue;
        nextValue = allocated + 1;
        updatedAt = now;
        return allocated;
    }

    public long nextValue() {
        return nextValue;
    }
}
