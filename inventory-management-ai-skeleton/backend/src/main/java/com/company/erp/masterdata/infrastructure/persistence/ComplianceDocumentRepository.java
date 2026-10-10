package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.ComplianceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ComplianceDocumentRepository
        extends JpaRepository<ComplianceDocument, Long>, JpaSpecificationExecutor<ComplianceDocument> {

    boolean existsByDocumentTypeId(Long documentTypeId);
}
