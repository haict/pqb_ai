package com.company.erp.sales.infrastructure.persistence;

import com.company.erp.sales.domain.SalesComplaint;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesComplaintRepository extends JpaRepository<SalesComplaint, Long>,
        JpaSpecificationExecutor<SalesComplaint> {

    /** Khóa ghi trước khi đổi trạng thái. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SalesComplaint c where c.id = :id")
    Optional<SalesComplaint> findByIdForUpdate(@Param("id") Long id);

    boolean existsBySalesOrderId(Long salesOrderId);
}
