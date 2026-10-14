package com.company.erp.reporting.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.CurrentUser;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra dùng chung của báo cáo (BR-RPT-12): quyền xem, phạm vi kho theo công ty / chi nhánh / kho được gán, khoảng
 * ngày. Ngoài phạm vi trả 404 như các module nghiệp vụ (BR-SYS-06).
 */
@Component
class ReportAccess {

    /** Khoảng ngày tối đa một lần xem, giữ truy vấn sổ nhật ký trong giới hạn hợp lý. */
    static final int MAX_RANGE_DAYS = 3660;

    private final MasterDataQueryService masterData;

    ReportAccess(MasterDataQueryService masterData) {
        this.masterData = masterData;
    }

    void requirePermission(CurrentUser actor, String permission) {
        if (!actor.hasPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * Kho được xem trong công ty; {@code null} nghĩa là mọi kho (phạm vi toàn công ty). Khi chọn một kho thì trả đúng
     * kho đó nếu trong phạm vi. Không có kho nào: tập chỉ chứa -1 (không khớp dòng nào).
     */
    Set<Long> warehouses(CurrentUser actor, Long companyId, Long warehouseId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        if (warehouseId != null) {
            WarehouseInfo warehouse = masterData.warehouse(warehouseId)
                    .filter(found -> found.companyId().equals(companyId))
                    .filter(found -> actor.dataScope().canAccessWarehouse(found.id(), found.branchId()))
                    .orElseThrow(() -> BusinessException.invalidField("warehouseId", "Kho không tồn tại hoặc ngoài phạm vi."));
            return Set.of(warehouse.id());
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

    /**
     * Chi nhánh được xem trong công ty; {@code null} nghĩa là mọi chi nhánh (phạm vi toàn công ty). Chọn một chi nhánh
     * thì phải trong phạm vi. Không có chi nhánh nào: tập chỉ chứa -1.
     */
    Set<Long> branches(CurrentUser actor, Long companyId, Long branchId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        if (branchId != null) {
            if (!actor.dataScope().canAccessBranch(branchId)) {
                throw BusinessException.invalidField("branchId", "Chi nhánh không tồn tại hoặc ngoài phạm vi.");
            }
            return Set.of(branchId);
        }
        if (actor.dataScope().hasFullCompanyAccess(companyId)) {
            return null;
        }
        Set<Long> ids = new HashSet<>(actor.dataScope().branchIds());
        ids.add(-1L);
        return ids;
    }

    /** "Chỉ dữ liệu của mình" (BR-SYS-04): ID người xem, hoặc {@code null} khi không bị giới hạn. */
    static Long owner(CurrentUser actor) {
        return actor.dataScope().ownDataOnly() ? actor.id() : null;
    }

    static void requireRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw BusinessException.invalidField("dateFrom", "Từ ngày phải trước đến ngày.");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw BusinessException.invalidField("dateTo", "Khoảng ngày tối đa " + MAX_RANGE_DAYS + " ngày.");
        }
    }
}
