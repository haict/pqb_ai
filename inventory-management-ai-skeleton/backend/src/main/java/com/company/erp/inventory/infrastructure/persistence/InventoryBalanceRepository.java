package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.InventoryBalance;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InventoryBalanceRepository
        extends JpaRepository<InventoryBalance, Long>, JpaSpecificationExecutor<InventoryBalance> {

    List<InventoryBalance> findByWarehouseIdAndOnHandQuantityGreaterThan(Long warehouseId, BigDecimal zero);
}
