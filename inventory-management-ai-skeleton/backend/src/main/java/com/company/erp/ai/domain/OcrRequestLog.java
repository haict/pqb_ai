package com.company.erp.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Một lần dùng OCR (docs/14 mục 7, BR-AI-10): chỉ metadata, không lưu file hay nội dung nhận dạng. Chỉ INSERT.
 */
@Entity
@Immutable
@Table(name = "ai_ocr_requests")
public class OcrRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private OcrDocumentType documentType;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "page_count")
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OcrRequestStatus status;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OcrRequestLog() {
    }

    public OcrRequestLog(Long companyId, Long branchId, Long userId, OcrDocumentType documentType, String fileSha256,
                         long fileSize, Integer pageCount, String errorCode, int durationMs, String requestId,
                         Instant createdAt) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.userId = userId;
        this.documentType = documentType;
        this.fileSha256 = fileSha256;
        this.fileSize = fileSize;
        this.pageCount = pageCount;
        this.status = errorCode == null ? OcrRequestStatus.SUCCEEDED : OcrRequestStatus.FAILED;
        this.errorCode = errorCode;
        this.durationMs = durationMs;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public OcrRequestStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
