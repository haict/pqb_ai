package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.CashAccount;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashAccountRepository extends JpaRepository<CashAccount, Long>, JpaSpecificationExecutor<CashAccount> {

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    List<CashAccount> findByCompanyIdOrderByCode(Long companyId);

    /** Khóa ghi quỹ theo id tăng dần trước khi ghi sổ (BR-GEN-52, thứ tự khóa cố định docs/02 mục 6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CashAccount a where a.id in :ids order by a.id")
    List<CashAccount> findAllForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CashAccount a where a.id = :id")
    Optional<CashAccount> findByIdForUpdate(@Param("id") Long id);
}
