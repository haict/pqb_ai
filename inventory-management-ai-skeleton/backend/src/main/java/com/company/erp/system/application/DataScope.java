package com.company.erp.system.application;

import java.util.Set;

/**
 * Phạm vi dữ liệu của người dùng (BR-SYS-04, BR-SYS-06). Module nghiệp vụ dùng để lọc truy vấn và
 * kiểm tra truy cập theo ID; dữ liệu ngoài phạm vi trả 404.
 *
 * @param fullCompanyIds công ty được truy cập toàn bộ (phạm vi COMPANY)
 * @param companyIds     mọi công ty có ít nhất một chi nhánh được truy cập
 * @param branchIds      mọi chi nhánh được truy cập (gồm chi nhánh của {@code fullCompanyIds})
 * @param warehouseIds   kho được gán riêng (phạm vi WAREHOUSE); chi nhánh và công ty của kho này không nằm trong
 *                       {@code branchIds} nhưng công ty có trong {@code companyIds} (xem danh mục dùng chung)
 * @param ownDataOnly    chỉ thấy dữ liệu do mình phụ trách (áp dụng ở module nghiệp vụ)
 */
public record DataScope(
        Set<Long> fullCompanyIds,
        Set<Long> companyIds,
        Set<Long> branchIds,
        Set<Long> warehouseIds,
        boolean ownDataOnly) {

    public DataScope {
        fullCompanyIds = Set.copyOf(fullCompanyIds);
        companyIds = Set.copyOf(companyIds);
        branchIds = Set.copyOf(branchIds);
        warehouseIds = Set.copyOf(warehouseIds);
    }

    public boolean canAccessCompany(Long companyId) {
        return companyId != null && companyIds.contains(companyId);
    }

    public boolean hasFullCompanyAccess(Long companyId) {
        return companyId != null && fullCompanyIds.contains(companyId);
    }

    public boolean canAccessBranch(Long branchId) {
        return branchId != null && branchIds.contains(branchId);
    }

    /** Kho thuộc chi nhánh trong phạm vi, hoặc được gán riêng theo kho. */
    public boolean canAccessWarehouse(Long warehouseId, Long warehouseBranchId) {
        return canAccessBranch(warehouseBranchId) || (warehouseId != null && warehouseIds.contains(warehouseId));
    }
}
