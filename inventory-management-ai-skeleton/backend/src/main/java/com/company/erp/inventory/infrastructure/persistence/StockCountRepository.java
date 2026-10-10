package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.StockCount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockCountRepository extends JpaRepository<StockCount, Long>, JpaSpecificationExecutor<StockCount> {

    /** Khóa ghi chứng từ theo khóa chính trước khi đổi trạng thái (BR-GEN-51, BR-GEN-52). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StockCount d where d.id = :id")
    Optional<StockCount> findByIdForUpdate(@Param("id") Long id);
}
