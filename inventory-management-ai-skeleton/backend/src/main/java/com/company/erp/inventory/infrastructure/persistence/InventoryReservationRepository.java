package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.InventoryReservation;
import com.company.erp.inventory.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long>, JpaSpecificationExecutor<InventoryReservation> {

    /** Khóa ghi chứng từ theo khóa chính trước khi đổi trạng thái (BR-GEN-51, BR-GEN-52). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from InventoryReservation d where d.id = :id")
    Optional<InventoryReservation> findByIdForUpdate(@Param("id") Long id);

    /** Giữ chỗ của chứng từ nguồn (mọi trạng thái), theo id. */
    @Query("""
            select r from InventoryReservation r
            where r.companyId = :companyId and r.sourceType = :sourceType and r.sourceId = :sourceId
            order by r.id
            """)
    List<InventoryReservation> findBySource(@Param("companyId") Long companyId,
                                            @Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    /** Khóa giữ chỗ theo id (gọi sau khi đã khóa tổng (hàng, kho) theo thứ tự). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.id in :ids order by r.id")
    List<InventoryReservation> findAllForUpdate(@Param("ids") Collection<Long> ids);

    /** Giữ chỗ còn hiệu lực đã quá hạn giữ (D-37), để nhả tự động. */
    @Query("""
            select r.id from InventoryReservation r
            where r.status = :status and r.expiresAt is not null and r.expiresAt <= :now
            order by r.id
            """)
    List<Long> findExpiredIds(@Param("status") ReservationStatus status, @Param("now") Instant now);
}
