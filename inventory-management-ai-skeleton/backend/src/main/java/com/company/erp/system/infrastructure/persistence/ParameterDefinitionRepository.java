package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.ParameterDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterDefinitionRepository extends JpaRepository<ParameterDefinition, String> {

    List<ParameterDefinition> findAllByOrderBySortOrderAscKeyAsc();
}
