package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.DebtOpeningDocument;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtOpeningDocumentRepository extends JpaRepository<DebtOpeningDocument, Long>,
        JpaSpecificationExecutor<DebtOpeningDocument> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DebtOpeningDocument d where d.id = :id")
    Optional<DebtOpeningDocument> findByIdForUpdate(@Param("id") Long id);
}
