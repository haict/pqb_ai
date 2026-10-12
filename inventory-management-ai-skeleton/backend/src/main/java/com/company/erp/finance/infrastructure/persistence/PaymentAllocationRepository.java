package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.PaymentAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    /** Phân bổ của một chứng từ nguồn (mọi trạng thái), theo thứ tự ghi. */
    @Query("""
            select a from PaymentAllocation a
            where a.companyId = :companyId and a.sourceType = :sourceType and a.sourceId = :sourceId
            order by a.id
            """)
    List<PaymentAllocation> findBySource(@Param("companyId") Long companyId, @Param("sourceType") String sourceType,
                                         @Param("sourceId") Long sourceId);
}
