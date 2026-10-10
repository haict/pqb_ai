package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.ReferenceCategory;
import com.company.erp.masterdata.domain.ReferenceItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReferenceItemRepository
        extends JpaRepository<ReferenceItem, Long>, JpaSpecificationExecutor<ReferenceItem> {

    boolean existsByCompanyIdAndCategoryAndCode(Long companyId, ReferenceCategory category, String code);

    Optional<ReferenceItem> findByCompanyIdAndCategoryAndCode(Long companyId, ReferenceCategory category, String code);
}
