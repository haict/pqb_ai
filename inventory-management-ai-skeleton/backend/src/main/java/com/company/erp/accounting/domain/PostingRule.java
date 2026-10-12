package com.company.erp.accounting.domain;

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

/**
 * Quy tắc hạch toán (BR-ACC-02, ADR-11): loại chứng từ + mã nghiệp vụ (thêm loại hàng, vai trò đối tượng nếu cần)
 * ghi Nợ / Có tài khoản nào. Vế để trống: tài khoản do chứng từ truyền vào (ví dụ tài khoản tiền của quỹ).
 * Điều kiện và ngày bắt đầu không đổi sau khi tạo; đổi quy tắc bằng cách đặt ngày hết hiệu lực cho quy tắc cũ và tạo
 * quy tắc mới, để bút toán cũ vẫn truy được quy tắc đã dùng.
 */
@Entity
@Table(name = "posting_rules")
public class PostingRule extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "document_type", nullable = false, updatable = false)
    private String documentType;

    @Column(name = "transaction_code", nullable = false, updatable = false)
    private String transactionCode;

    @Column(name = "product_type", updatable = false)
    private String productType;

    @Column(name = "partner_role", updatable = false)
    private String partnerRole;

    @Column(name = "debit_account_id")
    private Long debitAccountId;

    @Column(name = "credit_account_id")
    private Long creditAccountId;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    protected PostingRule() {
    }

    public PostingRule(Long companyId, String documentType, String transactionCode, String productType,
                       String partnerRole, LocalDate validFrom) {
        this.companyId = companyId;
        this.documentType = documentType;
        this.transactionCode = transactionCode;
        this.productType = productType;
        this.partnerRole = partnerRole;
        this.validFrom = validFrom;
        this.status = RecordStatus.ACTIVE;
    }

    /**
     * Tài khoản, ngày hết hiệu lực, diễn giải. Vế để trống là tài khoản do chứng từ truyền; để trống cả hai vế dùng cho
     * nghiệp vụ mà chứng từ quyết định cả hai tài khoản (thu chi khác, chuyển tiền nội bộ), quy tắc vẫn bắt buộc có để
     * bật nghiệp vụ (ADR-11, sửa đổi 4C).
     */
    public void configure(Long debitAccountId, Long creditAccountId, LocalDate validTo, String description) {
        if (debitAccountId != null && debitAccountId.equals(creditAccountId)) {
            throw BusinessException.invalidField("creditAccountId", "Tài khoản Nợ và Có phải khác nhau.");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw BusinessException.invalidField("validTo", "Ngày hết hiệu lực phải từ ngày bắt đầu trở đi.");
        }
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.validTo = validTo;
        this.description = description;
    }

    public boolean appliesOn(LocalDate date) {
        return status == RecordStatus.ACTIVE && !date.isBefore(validFrom)
                && (validTo == null || !date.isAfter(validTo));
    }

    public boolean matches(String lineProductType, String linePartnerRole) {
        return (productType == null || productType.equals(lineProductType))
                && (partnerRole == null || partnerRole.equals(linePartnerRole));
    }

    /** Số điều kiện bổ sung; quy tắc cụ thể hơn được ưu tiên. */
    public int specificity() {
        return (productType == null ? 0 : 1) + (partnerRole == null ? 0 : 1);
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

    public String getDocumentType() {
        return documentType;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public String getProductType() {
        return productType;
    }

    public String getPartnerRole() {
        return partnerRole;
    }

    public Long getDebitAccountId() {
        return debitAccountId;
    }

    public Long getCreditAccountId() {
        return creditAccountId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getDescription() {
        return description;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
