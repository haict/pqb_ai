package com.company.erp.sales.domain;

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

/**
 * Hàng bán bị trả lại (TLB, BR-SAL-32) / giảm giá hàng bán (GGB, BR-SAL-36) theo một đơn bán đã ghi sổ (D-54). Trả
 * hàng đúng lô, hạn dùng, serial đã xuất của dòng đơn, nhập lại kho trạng thái CÁCH LY qua module kho, giảm doanh thu
 * và công nợ phải thu. Giảm giá chỉ giảm doanh thu và công nợ, không đổi tồn. Ghi sổ / đảo ở
 * {@code SalesReturnPostingService}.
 */
@Entity
@Table(name = "sales_returns")
public class SalesReturn extends SalesDocument {

    public record Header(LocalDate documentDate, LocalDate postingDate, String description) {
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    private SalesReturnType documentType;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "sales_order_id", nullable = false, updatable = false)
    private Long salesOrderId;

    @Column(name = "warehouse_id", updatable = false)
    private Long warehouseId;

    @Column(name = "inventory_document_id")
    private Long inventoryDocumentId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_date")
    private LocalDate reverseDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "return_id", nullable = false)
    @OrderBy("lineNo")
    private List<SalesReturnLine> lines = new ArrayList<>();

    protected SalesReturn() {
    }

    public SalesReturn(Long companyId, Long branchId, String documentNo, SalesReturnType documentType,
                          Long customerId, Long salesOrderId, Long warehouseId) {
        super(companyId, branchId, documentNo);
        this.documentType = documentType;
        this.customerId = customerId;
        this.salesOrderId = salesOrderId;
        this.warehouseId = warehouseId;
    }

    @Override
    public String typeCode() {
        return documentType.name();
    }

    public void replaceContent(Header header, List<SalesReturnLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Chứng từ phải có ít nhất một dòng.");
        }
        if (header.postingDate().isBefore(header.documentDate())) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.postingDate = header.postingDate();
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
        amount = lines.stream().map(SalesReturnLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        taxAmount = lines.stream().map(SalesReturnLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = amount.add(taxAmount);
    }

    public void markPosted(Long inventoryDocumentId, Long actorId, Instant now) {
        requireStatus(POSTABLE, "ghi sổ");
        setStatus(SalesDocumentStatus.POSTED);
        this.inventoryDocumentId = inventoryDocumentId;
        this.postedBy = actorId;
        this.postedAt = now;
    }

    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.POSTED), "đảo");
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        setStatus(SalesDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        setReason(reason);
    }

    public boolean isGoodsReturn() {
        return documentType == SalesReturnType.TLB;
    }

    public SalesReturnType getDocumentType() {
        return documentType;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getInventoryDocumentId() {
        return inventoryDocumentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public LocalDate getReverseDate() {
        return reverseDate;
    }

    public List<SalesReturnLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
