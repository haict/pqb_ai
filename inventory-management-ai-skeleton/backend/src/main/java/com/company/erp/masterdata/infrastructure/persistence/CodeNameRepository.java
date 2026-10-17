package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.CodeNameEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/** Repository chung cho danh mục mã - tên theo công ty. */
@NoRepositoryBean
public interface CodeNameRepository<E extends CodeNameEntity> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    Optional<E> findByCompanyIdAndCode(Long companyId, String code);

    List<E> findByCompanyIdAndCodeIn(Long companyId, Collection<String> codes);
}
