package com.company.erp.system.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Tra cứu kho cho phạm vi dữ liệu theo kho (BR-SYS-04). Module danh mục (masterdata) cung cấp bean triển khai;
 * module system không phụ thuộc ngược vào masterdata.
 */
public interface WarehouseDirectory {

    /** Kho tối thiểu cho kiểm tra phạm vi. */
    record WarehouseRef(Long id, Long companyId, Long branchId) {
    }

    Optional<WarehouseRef> find(Long warehouseId);

    List<WarehouseRef> findAll(Collection<Long> warehouseIds);
}
