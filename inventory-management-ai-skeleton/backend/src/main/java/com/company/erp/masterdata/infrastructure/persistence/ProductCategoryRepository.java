package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.ProductCategory;
import java.util.List;

public interface ProductCategoryRepository extends CodeNameRepository<ProductCategory> {

    boolean existsByParentId(Long parentId);

    List<ProductCategory> findByCompanyId(Long companyId);
}
