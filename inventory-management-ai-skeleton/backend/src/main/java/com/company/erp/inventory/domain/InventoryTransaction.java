package com.company.erp.inventory.domain;

import com.company.erp.common.domain.QualityStatus;
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
import org.hibernate.annotations.Immutable;

/** Một dòng sổ nhật ký kho (bất biến, chỉ INSERT). Số lượng luôn dương, chiều ở {@code direction}. */
@Entity
@Immutable
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "serial_id")
    private Long serialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false, length = 30)
    private QualityStatus qualityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 3)
    private MovementDirection direction;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "reference_line_id")
    private Long referenceLineId;

    @Column(name = "reference_no", nullable = false, length = 50)
    private String referenceNo;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryTransaction() {
    }

    @SuppressWarnings("java:S107")
    public InventoryTransaction(Long companyId, Long branchId, LocalDate postingDate, Instant postedAt,
                                StockKey key, TransactionType transactionType, MovementDirection direction,
                                BigDecimal quantity, BigDecimal unitCost, Long partnerId, Reference reference,
                                Long reversalOfId, Long actorId) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.postingDate = postingDate;
        this.postedAt = postedAt;
        this.productId = key.productId();
        this.warehouseId = key.warehouseId();
        this.locationId = key.locationId();
        this.lotId = key.lotId();
        this.serialId = key.serialId();
        this.qualityStatus = key.qualityStatus();
        this.transactionType = transactionType;
        this.direction = direction;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.amount = unitCost == null ? null : unitCost.multiply(quantity);
        this.partnerId = partnerId;
        this.referenceType = reference.type();
        this.referenceId = reference.id();
        this.referenceLineId = reference.lineId();
        this.referenceNo = reference.number();
        this.reversalOfId = reversalOfId;
        this.createdBy = actorId;
        this.createdAt = postedAt;
    }

    /** Chứng từ và dòng chứng từ nguồn của biến động. */
    public record Reference(String type, Long id, Long lineId, String number) {
    }

    public StockKey key() {
        return new StockKey(productId, warehouseId, locationId, lotId, serialId, qualityStatus);
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

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public Long getLotId() {
        return lotId;
    }

    public Long getSerialId() {
        return serialId;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public MovementDirection getDirection() {
        return direction;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public Long getReferenceLineId() {
        return referenceLineId;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }
}
