package com.company.erp.inventory.application;

import com.company.erp.common.application.DocumentNumberRequest;
import com.company.erp.common.application.DocumentNumberService;
import com.company.erp.system.application.OrganizationQueryService;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Cấp số chứng từ kho theo cấu hình đánh số của loại chứng từ và mã chi nhánh (D-03, BR-SYS-30). */
@Component
class InventoryNumbering {

    private final DocumentNumberService numberService;
    private final OrganizationQueryService organization;

    InventoryNumbering(DocumentNumberService numberService, OrganizationQueryService organization) {
        this.numberService = numberService;
        this.organization = organization;
    }

    String next(Long companyId, Long branchId, String documentType, LocalDate documentDate) {
        String branchCode = organization.branch(branchId)
                .map(OrganizationQueryService.BranchRef::code)
                .orElseThrow(() -> new IllegalStateException("Thiếu chi nhánh #" + branchId));
        return numberService.nextNumber(new DocumentNumberRequest(companyId, branchId, branchCode, documentType,
                documentDate));
    }
}
