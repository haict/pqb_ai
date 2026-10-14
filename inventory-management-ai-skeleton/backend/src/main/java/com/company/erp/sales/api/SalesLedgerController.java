package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.sales.application.SalesLedgerService;
import com.company.erp.sales.application.SalesLedgerService.Criteria;
import com.company.erp.sales.application.dto.SalesLedgerResponse;
import com.company.erp.sales.domain.SalesLedgerGrouping;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sổ chi tiết bán hàng theo hàng hóa (mẫu S16-DNN, BR-RPT-26). */
@Tag(name = "Bán hàng - báo cáo")
@RestController
@RequestMapping("/api/v1/sales")
public class SalesLedgerController {

    private final SalesLedgerService ledger;

    public SalesLedgerController(SalesLedgerService ledger) {
        this.ledger = ledger;
    }

    @Operation(summary = "Sổ chi tiết bán hàng", description = "Dòng đơn bán, trả lại, giảm giá đã ghi sổ theo ngày "
            + "hạch toán, nhóm theo hàng hóa (và khách hàng / nhóm hàng khi groupBy = CUSTOMER / PRODUCT_CATEGORY). "
            + "Chứng từ đã đảo: dòng gốc và dòng âm theo ngày đảo. Lọc nhóm hàng gồm cả nhóm con. Tối đa 200 hàng / "
            + "khách chọn mỗi lần. Giá vốn, lãi gộp: null cho đến khi có giá vốn (Giai đoạn 8).")
    @GetMapping("/reports/detail-ledger")
    @PreAuthorize("hasAuthority('sales-ledger:view')")
    public ApiResponse<SalesLedgerResponse> detailLedger(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo,
            @RequestParam(required = false) Long salesUserId, @RequestParam(required = false) Long customerGroupId,
            @RequestParam(required = false) Long productCategoryId,
            @RequestParam(required = false) List<Long> productIds,
            @RequestParam(required = false) List<Long> customerIds,
            @RequestParam(defaultValue = "NONE") SalesLedgerGrouping groupBy) {
        return ApiResponse.ok(ledger.ledger(user, new Criteria(companyId, branchId, dateFrom, dateTo, salesUserId,
                customerGroupId, productCategoryId, productIds, customerIds, groupBy)));
    }
}
