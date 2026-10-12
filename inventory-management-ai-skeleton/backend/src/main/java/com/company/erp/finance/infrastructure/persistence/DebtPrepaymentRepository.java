package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.DebtPrepayment;
import com.company.erp.finance.domain.DebtType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtPrepaymentRepository extends JpaRepository<DebtPrepayment, Long> {

    /**
     * Tạo dòng trả trước nếu chưa có. {@code ON DUPLICATE KEY UPDATE} lấy khóa ghi ngay trên dòng đã có, tránh deadlock
     * của cách INSERT IGNORE rồi FOR UPDATE (docs/02 mục 6).
     */
    @Modifying
    @Query(value = """
            INSERT INTO debt_prepayments (company_id, branch_id, debt_type, partner_id, amount, updated_at)
            VALUES (:companyId, :branchId, :debtType, :partnerId, 0, UTC_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE amount = amount
            """, nativeQuery = true)
    void ensureRow(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                   @Param("debtType") String debtType, @Param("partnerId") Long partnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from DebtPrepayment p
            where p.companyId = :companyId and p.branchId = :branchId and p.debtType = :debtType
              and p.partnerId = :partnerId
            """)
    Optional<DebtPrepayment> findForUpdate(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                           @Param("debtType") DebtType debtType, @Param("partnerId") Long partnerId);

    @Query("""
            select p from DebtPrepayment p
            where p.companyId = :companyId and p.branchId = :branchId and p.debtType = :debtType
              and p.partnerId = :partnerId
            """)
    Optional<DebtPrepayment> find(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                  @Param("debtType") DebtType debtType, @Param("partnerId") Long partnerId);

    List<DebtPrepayment> findByCompanyIdAndDebtTypeAndPartnerId(Long companyId, DebtType debtType, Long partnerId);
}
