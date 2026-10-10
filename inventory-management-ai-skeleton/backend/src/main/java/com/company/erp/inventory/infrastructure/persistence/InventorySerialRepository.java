package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.InventorySerial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventorySerialRepository
        extends JpaRepository<InventorySerial, Long>, JpaSpecificationExecutor<InventorySerial> {

    Optional<InventorySerial> findByProductIdAndSerialNo(Long productId, String serialNo);

    List<InventorySerial> findByProductIdAndSerialNoIn(Long productId, Collection<String> serialNos);

    boolean existsByIdAndCompanyId(Long id, Long companyId);

    /** Tạo serial nếu chưa có; serial đã có thì khóa ghi ngay (không lấy khóa chia sẻ rồi nâng, tránh deadlock). */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into inventory_serials
                (company_id, product_id, serial_no, lot_id, lifecycle_status, blocked, version,
                 created_by, created_at, updated_by, updated_at)
            values (:companyId, :productId, :serialNo, :lotId, 'ISSUED', false, 0, :actorId, :now, :actorId, :now)
            on duplicate key update version = version
            """)
    int insertIfAbsent(@Param("companyId") Long companyId, @Param("productId") Long productId,
                       @Param("serialNo") String serialNo, @Param("lotId") Long lotId,
                       @Param("actorId") Long actorId, @Param("now") Instant now);
}
