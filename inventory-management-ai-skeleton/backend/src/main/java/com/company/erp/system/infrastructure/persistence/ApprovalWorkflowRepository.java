package com.company.erp.system.infrastructure.persistence;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.domain.ApprovalWorkflow;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApprovalWorkflowRepository
        extends JpaRepository<ApprovalWorkflow, Long>, JpaSpecificationExecutor<ApprovalWorkflow> {

    @EntityGraph(attributePaths = "steps")
    List<ApprovalWorkflow> findByCompanyIdAndDocumentTypeAndStatus(Long companyId, String documentType,
                                                                   RecordStatus status);
}
