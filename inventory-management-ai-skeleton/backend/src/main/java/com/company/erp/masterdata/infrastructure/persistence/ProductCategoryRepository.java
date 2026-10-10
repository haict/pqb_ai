package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.ProductCategory;

public interface ProductCategoryRepository extends CodeNameRepository<ProductCategory> {

    boolean existsByParentId(Long parentId);
}
