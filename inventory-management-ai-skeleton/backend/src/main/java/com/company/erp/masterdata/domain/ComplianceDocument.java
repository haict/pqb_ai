package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Hồ sơ / giấy phép cụ thể gắn với hàng, lô, serial, đối tác hoặc công ty; theo dõi ngày hết hạn (BR-MD-44). */
@Entity
@Table(name = "compliance_documents")
public class ComplianceDocument extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "document_type_id", nullable = false, updatable = false)
    private Long documentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20, updatable = false)
    private ComplianceOwnerType ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "document_no", nullable = false, length = 100)
    private String documentNo;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issuer")
    private String issuer;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    protected ComplianceDocument() {
    }

    public ComplianceDocument(Long companyId, ComplianceDocumentType type, ComplianceOwnerType ownerType,
                              Long ownerId, String documentNo, LocalDate issuedDate, LocalDate expiryDate,
                              String issuer, String note) {
        if (type.getAppliesTo() != ownerType) {
            throw BusinessException.invalidField("ownerType", "Loại hồ sơ này không áp dụng cho đối tượng đã chọn.");
        }
        this.companyId = companyId;
        this.documentTypeId = type.getId();
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.status = RecordStatus.ACTIVE;
        update(type, documentNo, issuedDate, expiryDate, issuer, note);
    }

    public final void update(ComplianceDocumentType type, String documentNo, LocalDate issuedDate,
                             LocalDate expiryDate, String issuer, String note) {
        if (type.isHasExpiry() && expiryDate == null) {
            throw BusinessException.invalidField("expiryDate", "Loại hồ sơ này bắt buộc ngày hết hạn.");
        }
        if (issuedDate != null && expiryDate != null && expiryDate.isBefore(issuedDate)) {
            throw BusinessException.invalidField("expiryDate", "Ngày hết hạn phải sau ngày cấp.");
        }
        this.documentNo = documentNo;
        this.issuedDate = issuedDate;
        this.expiryDate = expiryDate;
        this.issuer = issuer;
        this.note = note;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getDocumentTypeId() {
        return documentTypeId;
    }

    public ComplianceOwnerType getOwnerType() {
        return ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getNote() {
        return note;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
