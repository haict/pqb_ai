package com.company.erp.accounting.infrastructure.persistence;

import com.company.erp.accounting.domain.PostingRule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRuleRepository extends JpaRepository<PostingRule, Long>, JpaSpecificationExecutor<PostingRule> {

    List<PostingRule> findByCompanyIdAndDocumentTypeAndTransactionCode(Long companyId, String documentType,
                                                                       String transactionCode);

    @Query("""
            select count(r) > 0 from PostingRule r
            where r.companyId = :companyId and r.documentType = :documentType
              and r.transactionCode = :transactionCode
              and coalesce(r.productType, '') = :productType and coalesce(r.partnerRole, '') = :partnerRole
              and r.validFrom = :validFrom
            """)
    boolean existsCondition(@Param("companyId") Long companyId, @Param("documentType") String documentType,
                            @Param("transactionCode") String transactionCode,
                            @Param("productType") String productType, @Param("partnerRole") String partnerRole,
                            @Param("validFrom") LocalDate validFrom);
}
