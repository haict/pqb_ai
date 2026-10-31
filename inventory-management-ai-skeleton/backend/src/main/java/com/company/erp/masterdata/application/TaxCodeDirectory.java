package com.company.erp.masterdata.application;

import com.company.erp.masterdata.application.dto.TaxCodeLookupResponse;

/** Nguồn tra cứu doanh nghiệp theo mã số thuế bên ngoài hệ thống (D-66). */
public interface TaxCodeDirectory {

    /**
     * @throws com.company.erp.common.domain.BusinessException {@code TAX_CODE_NOT_FOUND}, {@code TOO_MANY_REQUESTS},
     *         {@code TAX_CODE_LOOKUP_UNAVAILABLE}, {@code TAX_CODE_LOOKUP_TIMEOUT}
     */
    TaxCodeLookupResponse lookup(String taxCode);
}
