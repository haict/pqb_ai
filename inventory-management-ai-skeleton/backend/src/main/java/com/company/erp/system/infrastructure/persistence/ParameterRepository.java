package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.Parameter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterRepository extends JpaRepository<Parameter, Long> {

    List<Parameter> findByCompanyId(Long companyId);

    Optional<Parameter> findByCompanyIdAndKey(Long companyId, String key);
}
