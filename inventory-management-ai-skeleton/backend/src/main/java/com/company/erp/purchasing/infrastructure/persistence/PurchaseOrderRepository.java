package com.company.erp.purchasing.infrastructure.persistence;

import com.company.erp.purchasing.domain.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>,
        JpaSpecificationExecutor<PurchaseOrder> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PurchaseOrder d where d.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    /** Id đơn chứa các dòng, tăng dần (khóa từng đơn theo thứ tự này). */
    @Query("select distinct d.id from PurchaseOrder d join d.lines l where l.id in :lineIds order by d.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("select distinct d from PurchaseOrder d join d.lines l where l.id in :lineIds")
    List<PurchaseOrder> findByLineIds(@Param("lineIds") Collection<Long> lineIds);
}
