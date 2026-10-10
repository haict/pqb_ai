package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.ApprovalDelegation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalDelegationRepository
        extends JpaRepository<ApprovalDelegation, Long>, JpaSpecificationExecutor<ApprovalDelegation> {

    /** Ủy quyền đang hiệu lực mà người dùng là người được ủy quyền. */
    @Query("""
            select d from ApprovalDelegation d
            where d.delegateUserId = :userId and d.status = com.company.erp.system.domain.DelegationStatus.ACTIVE
              and d.validFrom <= :now and d.validTo > :now
            """)
    List<ApprovalDelegation> findEffectiveForDelegate(@Param("userId") Long userId, @Param("now") Instant now);

    /** Ủy quyền đang hiệu lực của các người ủy quyền (để thông báo cả người được ủy quyền). */
    @Query("""
            select d from ApprovalDelegation d
            where d.delegatorUserId in :userIds and d.status = com.company.erp.system.domain.DelegationStatus.ACTIVE
              and d.validFrom <= :now and d.validTo > :now
            """)
    List<ApprovalDelegation> findEffectiveByDelegators(@Param("userIds") Collection<Long> userIds,
                                                       @Param("now") Instant now);
}
