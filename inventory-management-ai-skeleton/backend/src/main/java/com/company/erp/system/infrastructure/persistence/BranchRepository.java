package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.Branch;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, Long>, JpaSpecificationExecutor<Branch> {

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    @Query("select b.id from Branch b where b.companyId in :companyIds")
    List<Long> findIdsByCompanyIdIn(@Param("companyIds") Collection<Long> companyIds);

    @Query("select distinct b.companyId from Branch b where b.id in :branchIds")
    List<Long> findCompanyIdsByIdIn(@Param("branchIds") Collection<Long> branchIds);
}
