package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.ProductStockLevel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockLevelRepository extends JpaRepository<ProductStockLevel, ProductStockLevel.Key> {

    List<ProductStockLevel> findByProductId(Long productId);

    List<ProductStockLevel> findByWarehouseIdInAndMinQuantityIsNotNull(Collection<Long> warehouseIds);
}
