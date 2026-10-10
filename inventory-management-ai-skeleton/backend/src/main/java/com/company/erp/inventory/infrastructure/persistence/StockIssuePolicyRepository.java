package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.StockIssuePolicy;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockIssuePolicyRepository extends JpaRepository<StockIssuePolicy, Long>, JpaSpecificationExecutor<StockIssuePolicy> {

    /** Khóa ghi chứng từ theo khóa chính trước khi đổi trạng thái (BR-GEN-51, BR-GEN-52). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StockIssuePolicy d where d.id = :id")
    Optional<StockIssuePolicy> findByIdForUpdate(@Param("id") Long id);
}
