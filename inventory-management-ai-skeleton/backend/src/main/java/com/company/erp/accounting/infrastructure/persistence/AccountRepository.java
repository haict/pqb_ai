package com.company.erp.accounting.infrastructure.persistence;

import com.company.erp.accounting.domain.Account;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    Optional<Account> findByCompanyIdAndCode(Long companyId, String code);

    List<Account> findByCompanyId(Long companyId);

    boolean existsByParentIdAndStatus(Long parentId, RecordStatus status);

    List<Account> findByIdIn(Collection<Long> ids);

    /** Khóa tài khoản cha khi thêm tài khoản con: không để vừa hạch toán vừa chuyển thành tài khoản tổng hợp. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /**
     * Đọc tài khoản dùng để hạch toán với khóa chia sẻ: thêm tài khoản con (khóa ghi tài khoản cha) phải chờ các
     * giao dịch đang hạch toán vào tài khoản đó kết thúc, và ngược lại.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> findForShare(@Param("ids") Collection<Long> ids);
}
