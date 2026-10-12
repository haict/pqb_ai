package com.company.erp.purchasing.domain;

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
 * Điều chỉnh giá trị nhập theo dòng phiếu nhận hàng (chỉ INSERT, đảo bằng dòng âm): chênh lệch giá hóa đơn so với giá
 * tạm tính (BR-PUR-10), chi phí mua phân bổ (BR-PUR-12). Giai đoạn 8 dùng để tính lại giá vốn (BR-CST-02).
 */
@Entity
@Table(name = "purchase_cost_entries")
public class PurchaseCostEntry {

    /** Loại điều chỉnh. */
    public enum EntryType {
        INVOICE_PRICE,
        LANDED_COST,
        DISCOUNT
    }

    /** Chứng từ phát sinh điều chỉnh. */
    public record Reference(String type, Long id, Long lineId, String number) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "receipt_line_id", nullable = false, updatable = false)
    private Long receiptLineId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private EntryType entryType;

    @Column(name = "posting_date", nullable = false, updatable = false)
    private LocalDate postingDate;

    @Column(name = "base_quantity", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal baseQuantity;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "reference_type", nullable = false, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private Long referenceId;

    @Column(name = "reference_line_id", updatable = false)
    private Long referenceLineId;

    @Column(name = "reference_no", nullable = false, updatable = false)
    private String referenceNo;

    @Column(name = "reversal_of_id", updatable = false)
    private Long reversalOfId;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PurchaseCostEntry() {
    }

    @SuppressWarnings("java:S107")
    public PurchaseCostEntry(Long companyId, PurchaseReceiptLine line, Long warehouseId, EntryType entryType,
                             LocalDate postingDate, BigDecimal amount, Reference reference, Long actorId,
                             Instant now) {
        this.companyId = companyId;
        this.receiptLineId = line.getId();
        this.productId = line.getProductId();
        this.warehouseId = warehouseId;
        this.entryType = entryType;
        this.postingDate = postingDate;
        this.baseQuantity = line.getBaseQuantity();
        this.amount = amount;
        this.referenceType = reference.type();
        this.referenceId = reference.id();
        this.referenceLineId = reference.lineId();
        this.referenceNo = reference.number();
        this.createdBy = actorId;
        this.createdAt = now;
    }

    /** Dòng đảo: cùng dòng phiếu nhận, số tiền ngược dấu, tham chiếu dòng gốc. */
    public PurchaseCostEntry reversed(LocalDate date, Long actorId, Instant now) {
        PurchaseCostEntry reversal = new PurchaseCostEntry();
        reversal.companyId = companyId;
        reversal.receiptLineId = receiptLineId;
        reversal.productId = productId;
        reversal.warehouseId = warehouseId;
        reversal.entryType = entryType;
        reversal.postingDate = date;
        reversal.baseQuantity = baseQuantity;
        reversal.amount = amount.negate();
        reversal.referenceType = referenceType;
        reversal.referenceId = referenceId;
        reversal.referenceLineId = referenceLineId;
        reversal.referenceNo = referenceNo;
        reversal.reversalOfId = id;
        reversal.createdBy = actorId;
        reversal.createdAt = now;
        return reversal;
    }

    public Long getId() {
        return id;
    }

    public Long getReceiptLineId() {
        return receiptLineId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }
}
