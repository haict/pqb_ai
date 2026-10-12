package com.company.erp.sales.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Tiếp nhận hàng lỗi khách báo (BR-SAL-34): khách, phiếu giao / hóa đơn gốc, hàng, lô, serial, mô tả lỗi; kiểm tra (kết
 * quả); quyết định đổi / sửa / trả / bảo hành / từ chối, liên kết chứng từ trả hàng nếu có. Không tự đổi tồn, công nợ:
 * xử lý bằng chứng từ trả hàng / đổi hàng; sửa chữa, bảo hành ở Giai đoạn 9.
 */
@Entity
@Table(name = "sales_complaints")
public class SalesComplaint extends AuditableEntity {

    public static final String TYPE_CODE = "KN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "complaint_no", nullable = false, updatable = false)
    private String complaintNo;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "sales_invoice_id")
    private Long salesInvoiceId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "lot_no")
    private String lotNo;

    @Column(name = "serial_no")
    private String serialNo;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "inspection_result")
    private String inspectionResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private ComplaintDecision decision;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "sales_return_id")
    private Long salesReturnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ComplaintStatus status;

    @Column(name = "inspected_by")
    private Long inspectedBy;

    @Column(name = "inspected_at")
    private Instant inspectedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "sales_user_id")
    private Long salesUserId;

    protected SalesComplaint() {
    }

    /** Nội dung tiếp nhận. */
    public record Details(LocalDate receivedDate, Long deliveryId, Long salesInvoiceId, Long productId, String lotNo,
                          String serialNo, BigDecimal quantity, String description) {
    }

    public SalesComplaint(Long companyId, Long branchId, String complaintNo, Long customerId, Long salesUserId,
                          Details details) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.complaintNo = complaintNo;
        this.customerId = customerId;
        this.salesUserId = salesUserId;
        this.status = ComplaintStatus.OPEN;
        apply(details);
    }

    /** Sửa nội dung khi chưa kiểm tra. */
    public void update(Details details) {
        require(ComplaintStatus.OPEN, "sửa");
        apply(details);
    }

    private void apply(Details details) {
        this.receivedDate = details.receivedDate();
        this.deliveryId = details.deliveryId();
        this.salesInvoiceId = details.salesInvoiceId();
        this.productId = details.productId();
        this.lotNo = details.lotNo();
        this.serialNo = details.serialNo();
        this.quantity = details.quantity();
        this.description = details.description();
    }

    public void inspect(String result, Long actorId, Instant now) {
        require(ComplaintStatus.OPEN, "ghi kết quả kiểm tra");
        this.inspectionResult = result;
        this.status = ComplaintStatus.INSPECTED;
        this.inspectedBy = actorId;
        this.inspectedAt = now;
    }

    /** Quyết định xử lý; trả hàng / đổi hàng kèm chứng từ trả hàng đã lập. */
    public void resolve(ComplaintDecision decision, String note, Long salesReturnId, Long actorId, Instant now) {
        require(ComplaintStatus.INSPECTED, "quyết định xử lý");
        this.decision = decision;
        this.resolutionNote = note;
        this.salesReturnId = salesReturnId;
        this.status = ComplaintStatus.RESOLVED;
        this.resolvedBy = actorId;
        this.resolvedAt = now;
    }

    public void cancel(String reason) {
        if (status == ComplaintStatus.RESOLVED || status == ComplaintStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + complaintNo
                    + " đã xử lý xong / đã hủy.");
        }
        this.status = ComplaintStatus.CANCELLED;
        this.resolutionNote = reason;
    }

    private void require(ComplaintStatus expected, String action) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + complaintNo + " đang ở trạng thái "
                    + status + ", không " + action + " được.");
        }
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

    public String getComplaintNo() {
        return complaintNo;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public Long getSalesInvoiceId() {
        return salesInvoiceId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getDescription() {
        return description;
    }

    public String getInspectionResult() {
        return inspectionResult;
    }

    public ComplaintDecision getDecision() {
        return decision;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Long getSalesReturnId() {
        return salesReturnId;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public Long getInspectedBy() {
        return inspectedBy;
    }

    public Instant getInspectedAt() {
        return inspectedAt;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Long getSalesUserId() {
        return salesUserId;
    }
}
