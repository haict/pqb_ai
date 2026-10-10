package com.company.erp.masterdata.application;

import com.company.erp.masterdata.domain.Warehouse;
import com.company.erp.masterdata.infrastructure.persistence.WarehouseRepository;
import com.company.erp.system.application.WarehouseDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cung cấp tra cứu kho cho phạm vi dữ liệu theo kho của module system (BR-SYS-04). */
@Component
class WarehouseDirectoryAdapter implements WarehouseDirectory {

    private final WarehouseRepository warehouseRepository;

    WarehouseDirectoryAdapter(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WarehouseRef> find(Long warehouseId) {
        return warehouseRepository.findById(warehouseId).map(WarehouseDirectoryAdapter::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseRef> findAll(Collection<Long> warehouseIds) {
        return warehouseRepository.findByIdIn(warehouseIds).stream().map(WarehouseDirectoryAdapter::toRef).toList();
    }

    private static WarehouseRef toRef(Warehouse warehouse) {
        return new WarehouseRef(warehouse.getId(), warehouse.getCompanyId(), warehouse.getBranchId());
    }
}
