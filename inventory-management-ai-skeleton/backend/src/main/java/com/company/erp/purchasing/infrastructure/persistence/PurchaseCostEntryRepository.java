package com.company.erp.purchasing.infrastructure.persistence;

import com.company.erp.purchasing.domain.PurchaseCostEntry;
import java.util.Collection;
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

    /** Dòng phiếu nhận có điều chỉnh giá trị chưa đảo của chứng từ khác hóa đơn (phân bổ chi phí, giảm giá). */
    @Query("""
            select distinct e.referenceNo from PurchaseCostEntry e
            where e.receiptLineId in :lineIds and e.reversalOfId is null and e.referenceType <> :excludedType
              and not exists (select r.id from PurchaseCostEntry r where r.reversalOfId = e.id)
            """)
    List<String> findActiveReferences(@Param("lineIds") Collection<Long> lineIds,
                                      @Param("excludedType") String excludedType);
}
