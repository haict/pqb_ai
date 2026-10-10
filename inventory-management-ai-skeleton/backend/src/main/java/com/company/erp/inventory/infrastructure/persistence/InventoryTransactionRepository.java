package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.InventoryTransaction;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, Long>, JpaSpecificationExecutor<InventoryTransaction> {

    List<InventoryTransaction> findByReferenceTypeAndReferenceIdOrderByIdAsc(String referenceType, Long referenceId);

    boolean existsByCompanyId(Long companyId);

    @Query("select distinct t.productId from InventoryTransaction t where t.productId in :productIds")
    List<Long> findProductIdsWithTransactions(@Param("productIds") Collection<Long> productIds);

    /** Chứng từ khác đã xuất từ cùng lô / serial ở cùng kho sau thời điểm ghi sổ gốc (chứng từ phụ thuộc). */
    @Query("""
            select distinct t.referenceNo from InventoryTransaction t
            where t.warehouseId = :warehouseId and t.productId = :productId
              and ((:lotId is null and t.lotId is null) or t.lotId = :lotId)
              and ((:serialId is null and t.serialId is null) or t.serialId = :serialId)
              and t.direction = com.company.erp.inventory.domain.MovementDirection.OUT
              and t.postedAt >= :after and t.referenceId <> :excludeReferenceId
            """)
    List<String> findDependentReferences(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId,
                                         @Param("lotId") Long lotId, @Param("serialId") Long serialId,
                                         @Param("after") Instant after,
                                         @Param("excludeReferenceId") Long excludeReferenceId);
}
