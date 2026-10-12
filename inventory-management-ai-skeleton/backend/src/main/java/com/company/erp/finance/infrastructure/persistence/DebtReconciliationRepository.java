package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.DebtReconciliation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtReconciliationRepository extends JpaRepository<DebtReconciliation, Long>,
        JpaSpecificationExecutor<DebtReconciliation> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DebtReconciliation r where r.id = :id")
    Optional<DebtReconciliation> findByIdForUpdate(@Param("id") Long id);
}
