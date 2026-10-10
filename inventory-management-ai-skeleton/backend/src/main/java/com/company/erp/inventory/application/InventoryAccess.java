package com.company.erp.inventory.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.CurrentUser;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra dùng chung của module kho: quyền theo loại chứng từ (security.md mục 2), phạm vi kho (BR-SYS-06: ngoài
 * phạm vi trả 404), audit.
 */
@Component
class InventoryAccess {

    static final String ENTITY_DOCUMENT = "INVENTORY_DOCUMENT";
    static final String ENTITY_STOCK_COUNT = "STOCK_COUNT";
    static final String ENTITY_RECALL = "RECALL";
    static final String ENTITY_POLICY = "STOCK_ISSUE_POLICY";
    static final String ENTITY_LOT = "INVENTORY_LOT";
    static final String ENTITY_SERIAL = "INVENTORY_SERIAL";
    static final String ENTITY_RESERVATION = "INVENTORY_RESERVATION";

    private final MasterDataQueryService masterData;
    private final AuditLogService auditLog;

    InventoryAccess(MasterDataQueryService masterData, AuditLogService auditLog) {
        this.masterData = masterData;
        this.auditLog = auditLog;
    }

    void requirePermission(CurrentUser actor, String permission) {
        if (!actor.hasPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /** Kho trong phạm vi của người dùng; ngoài phạm vi hoặc không tồn tại: 404. */
    WarehouseInfo visibleWarehouse(CurrentUser actor, Long warehouseId) {
        return masterData.warehouse(warehouseId)
                .filter(warehouse -> canAccess(actor, warehouse))
                .orElseThrow(BusinessException::notFound);
    }

    /** Kho dùng để lập chứng từ: trong phạm vi và đang hoạt động. */
    WarehouseInfo usableWarehouse(CurrentUser actor, Long warehouseId, String field) {
        WarehouseInfo warehouse = masterData.warehouse(warehouseId)
                .filter(found -> canAccess(actor, found))
                .orElseThrow(() -> BusinessException.invalidField(field, "Kho không tồn tại hoặc ngoài phạm vi."));
        if (!warehouse.active()) {
            throw BusinessException.invalidField(field, "Kho " + warehouse.code() + " đã ngừng hoạt động.");
        }
        return warehouse;
    }

    boolean canAccess(CurrentUser actor, WarehouseInfo warehouse) {
        return actor.dataScope().canAccessCompany(warehouse.companyId())
                && actor.dataScope().canAccessWarehouse(warehouse.id(), warehouse.branchId());
    }

    boolean canAccessWarehouse(CurrentUser actor, Long warehouseId) {
        return warehouseId != null && masterData.warehouse(warehouseId).filter(found -> canAccess(actor, found))
                .isPresent();
    }

    /**
     * Kho được xem trong công ty để lọc danh sách; {@code null} nghĩa là toàn công ty (không cần lọc theo kho).
     * Công ty ngoài phạm vi: 404.
     */
    Set<Long> scopeWarehouseIds(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        if (actor.dataScope().hasFullCompanyAccess(companyId)) {
            return null;
        }
        Set<Long> ids = new HashSet<>(actor.dataScope().warehouseIds());
        masterData.warehousesOfBranches(actor.dataScope().branchIds()).stream()
                .filter(warehouse -> warehouse.companyId().equals(companyId))
                .forEach(warehouse -> ids.add(warehouse.id()));
        ids.add(-1L);
        return ids;
    }

    void audit(CurrentUser actor, String action, String entityType, Long entityId, String documentNo, String reason,
               Object before, Object after, Long companyId, Long branchId) {
        auditLog.record(new AuditEntry(actor.id(), action, entityType, entityId, documentNo, reason, before, after,
                companyId, branchId));
    }
}
