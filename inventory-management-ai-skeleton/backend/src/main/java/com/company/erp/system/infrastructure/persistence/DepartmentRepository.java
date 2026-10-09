package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {

    boolean existsByBranchIdAndCode(Long branchId, String code);
}
