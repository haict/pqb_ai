package com.company.erp.purchasing.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.purchasing.application.PurchaseLedgerService;
import com.company.erp.purchasing.application.PurchaseLedgerService.Criteria;
import com.company.erp.purchasing.application.dto.PurchaseLedgerResponse;
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

/** Sổ chi tiết mua hàng (hiện trạng docs/HienTrang 14-15). */
@Tag(name = "Mua hàng - báo cáo")
@RestController
@RequestMapping("/api/v1/purchasing")
public class PurchaseLedgerController {

    private final PurchaseLedgerService ledger;

    public PurchaseLedgerController(PurchaseLedgerService ledger) {
        this.ledger = ledger;
    }

    @Operation(summary = "Sổ chi tiết mua hàng", description = "Dòng đơn mua, trả hàng, giảm giá hàng mua đã ghi sổ theo "
            + "ngày hạch toán, kèm tổng. Chứng từ đã đảo: dòng gốc và dòng âm theo ngày đảo. purchaserId: người lập "
            + "chứng từ. Lọc nhóm hàng gồm cả nhóm con. Tối đa 200 hàng / NCC chọn mỗi lần.")
    @GetMapping("/reports/detail-ledger")
    @PreAuthorize("hasAuthority('purchase-ledger:view')")
    public ApiResponse<PurchaseLedgerResponse> detailLedger(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo,
            @RequestParam(required = false) Long purchaserId, @RequestParam(required = false) Long supplierGroupId,
            @RequestParam(required = false) Long productCategoryId,
            @RequestParam(required = false) List<Long> productIds,
            @RequestParam(required = false) List<Long> supplierIds) {
        return ApiResponse.ok(ledger.ledger(user, new Criteria(companyId, branchId, dateFrom, dateTo, purchaserId,
                supplierGroupId, productCategoryId, productIds, supplierIds)));
    }
}
