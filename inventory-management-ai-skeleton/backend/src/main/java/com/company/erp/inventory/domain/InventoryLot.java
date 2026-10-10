package com.company.erp.inventory.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Lô hàng (BR-INV-01): (hàng, số lô) duy nhất và chỉ có một hạn dùng. Khóa lô chặn xuất trên toàn bộ kho (BR-INV-41).
 */
@Entity
@Table(name = "inventory_lots")
public class InventoryLot extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "lot_no", nullable = false, length = 50, updatable = false)
    private String lotNo;

    @Column(name = "manufacture_date", updatable = false)
    private LocalDate manufactureDate;

    @Column(name = "expiry_date", updatable = false)
    private LocalDate expiryDate;

    @Column(name = "is_temporary", nullable = false, updatable = false)
    private boolean temporary;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    protected InventoryLot() {
    }

    public InventoryLot(Long companyId, Long productId, String lotNo, LocalDate manufactureDate, LocalDate expiryDate) {
        this.companyId = companyId;
        this.productId = productId;
        this.lotNo = lotNo;
        this.manufactureDate = manufactureDate;
        this.expiryDate = expiryDate;
    }

    /** Lô đã có: hạn dùng nhập lại phải trùng hạn dùng đã ghi (BR-INV-01). */
    public void requireSameExpiry(LocalDate expiry, String field) {
        if (!Objects.equals(expiryDate, expiry)) {
            throw BusinessException.invalidField(field, "Lô " + lotNo + " đã có hạn dùng "
                    + (expiryDate == null ? "trống" : expiryDate) + "; một lô chỉ có một hạn dùng.");
        }
    }

    public boolean isExpiredOn(LocalDate date) {
        return expiryDate != null && expiryDate.isBefore(date);
    }

    public void block(String reason) {
        if (blocked) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Lô " + lotNo + " đang bị khóa.");
        }
        this.blocked = true;
        this.blockReason = reason;
    }

    public void unblock() {
        if (!blocked) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Lô " + lotNo + " không bị khóa.");
        }
        this.blocked = false;
        this.blockReason = null;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public LocalDate getManufactureDate() {
        return manufactureDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockReason() {
        return blockReason;
    }
}
