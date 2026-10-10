package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.WarehouseLocation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long> {

    List<WarehouseLocation> findByWarehouseIdOrderByCode(Long warehouseId);

    List<WarehouseLocation> findByIdIn(Collection<Long> ids);

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);

    Optional<WarehouseLocation> findByWarehouseIdAndDefaultLocationTrue(Long warehouseId);
}
