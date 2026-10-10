package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.ApprovalAction;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {

    List<ApprovalAction> findByRequestIdInOrderByActedAtAscIdAsc(Collection<Long> requestIds);
}
