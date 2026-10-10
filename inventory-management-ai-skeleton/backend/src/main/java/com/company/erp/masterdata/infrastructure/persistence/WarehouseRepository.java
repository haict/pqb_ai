package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.Warehouse;
import java.util.Collection;
import java.util.List;

public interface WarehouseRepository extends CodeNameRepository<Warehouse> {

    List<Warehouse> findByIdIn(Collection<Long> ids);

    List<Warehouse> findByBranchIdIn(Collection<Long> branchIds);
}
