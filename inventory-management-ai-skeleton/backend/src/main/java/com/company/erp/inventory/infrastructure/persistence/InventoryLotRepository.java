package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.InventoryLot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long>, JpaSpecificationExecutor<InventoryLot> {

    Optional<InventoryLot> findByProductIdAndLotNo(Long productId, String lotNo);

    boolean existsByIdAndCompanyId(Long id, Long companyId);

    /** Tạo lô nếu chưa có (một câu lệnh, an toàn khi hai phiếu cùng nhập một lô mới). */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert ignore into inventory_lots
                (company_id, product_id, lot_no, manufacture_date, expiry_date, is_temporary, blocked, version,
                 created_by, created_at, updated_by, updated_at)
            values (:companyId, :productId, :lotNo, :manufactureDate, :expiryDate, false, false, 0,
                    :actorId, :now, :actorId, :now)
            """)
    int insertIfAbsent(@Param("companyId") Long companyId, @Param("productId") Long productId,
                       @Param("lotNo") String lotNo, @Param("manufactureDate") LocalDate manufactureDate,
                       @Param("expiryDate") LocalDate expiryDate, @Param("actorId") Long actorId,
                       @Param("now") Instant now);
}
