package com.company.erp.purchasing.infrastructure.persistence;

import com.company.erp.purchasing.domain.PurchaseCostEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseCostEntryRepository extends JpaRepository<PurchaseCostEntry, Long> {

    /** Dòng gốc (chưa phải dòng đảo) của chứng từ phát sinh, để đảo. */
    @Query("""
            select e from PurchaseCostEntry e
            where e.referenceType = :type and e.referenceId = :id and e.reversalOfId is null
            order by e.id
            """)
    List<PurchaseCostEntry> findOriginals(@Param("type") String type, @Param("id") Long id);
}
