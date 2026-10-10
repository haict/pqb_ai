package com.company.erp.inventory.domain;

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

/**
 * Serial (BR-INV-02): duy nhất theo mã hàng; không tồn ở hai nơi (tồn mỗi serial tối đa 1 trên toàn bộ kho, kiểm
 * tra khi ghi sổ). Vòng đời và kho hiện tại là bản đọc nhanh, lịch sử ở sổ nhật ký kho.
 */
@Entity
@Table(name = "inventory_serials")
public class InventorySerial extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "serial_no", nullable = false, length = 100, updatable = false)
    private String serialNo;

    @Column(name = "lot_id", updatable = false)
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 30)
    private SerialStatus lifecycleStatus;

    @Column(name = "current_warehouse_id")
    private Long currentWarehouseId;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    protected InventorySerial() {
    }

    public InventorySerial(Long companyId, Long productId, String serialNo, Long lotId) {
        this.companyId = companyId;
        this.productId = productId;
        this.serialNo = serialNo;
        this.lotId = lotId;
        this.lifecycleStatus = SerialStatus.ISSUED;
    }

    public void moveTo(SerialStatus status, Long warehouseId) {
        this.lifecycleStatus = status;
        this.currentWarehouseId = warehouseId;
    }

    public void block(String reason) {
        if (blocked) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Serial " + serialNo + " đang bị khóa.");
        }
        this.blocked = true;
        this.blockReason = reason;
    }

    public void unblock() {
        if (!blocked) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Serial " + serialNo + " không bị khóa.");
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

    public String getSerialNo() {
        return serialNo;
    }

    public Long getLotId() {
        return lotId;
    }

    public SerialStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public Long getCurrentWarehouseId() {
        return currentWarehouseId;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockReason() {
        return blockReason;
    }
}
