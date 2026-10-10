package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.Product;
import java.util.Collection;
import java.util.List;

public interface ProductRepository extends CodeNameRepository<Product> {

    boolean existsByCategoryId(Long categoryId);

    List<Product> findByCompanyIdAndCodeIn(Long companyId, Collection<String> codes);
}
