package com.company.erp.system.infrastructure.persistence;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.domain.Company;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {

    boolean existsByCode(String code);

    List<Company> findByStatus(RecordStatus status);
}
