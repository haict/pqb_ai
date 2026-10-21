package com.company.erp.reporting.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.reporting.application.DashboardService;
import com.company.erp.reporting.application.dto.DashboardResponse.BacklogDetails;
import com.company.erp.reporting.application.dto.DashboardResponse.DebtDetails;
import com.company.erp.reporting.application.dto.DashboardResponse.ExpiryDetails;
import com.company.erp.reporting.application.dto.DashboardResponse.Summary;
import com.company.erp.reporting.application.dto.DashboardResponse.TradeDetails;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard điều hành M10 đợt 10C (BR-RPT-01, 02, 04..06, 10..13, ADR-18). Quyền {@code dashboard:view}. */
@Tag(name = "Dashboard điều hành")
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAuthority('dashboard:view')")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @Operation(summary = "Chỉ tiêu dashboard", description = "Doanh thu và giá trị mua trong kỳ (chứng từ đã ghi sổ, chưa "
            + "thuế, trừ trả lại / giảm giá, đảo theo ngày đảo); công nợ còn lại, quá hạn theo tuổi nợ; lô đã / sắp hết "
            + "hạn (tham số inventory.expiry-warning-days); đơn mua / bán chưa giao đủ; top hàng, khách, NCC (top 1..50, "
            + "mặc định 10). Giá vốn, lợi nhuận: Giai đoạn 8.")
    @GetMapping("/summary")
    public ApiResponse<Summary> summary(@AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) Long branchId, @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo, @RequestParam(required = false) Integer top) {
        return ApiResponse.ok(dashboard.summary(user, companyId, branchId, dateFrom, dateTo, top));
    }

    @Operation(summary = "Chi tiết doanh thu / giá trị mua", description = "BR-RPT-11. Chứng từ cấu thành chỉ tiêu, số "
            + "tiền có dấu; lọc partnerId / productId để xem một dòng top.")
    @GetMapping("/{kind:revenue|purchases}/details")
    public ApiResponse<TradeDetails> tradeDetails(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String kind, @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo,
            @RequestParam(required = false) Long partnerId, @RequestParam(required = false) Long productId) {
        return ApiResponse.ok(dashboard.tradeDetails(user, kind, companyId, branchId, dateFrom, dateTo, partnerId,
                productId));
    }

    @Operation(summary = "Chi tiết công nợ", description = "BR-RPT-11. Khoản công nợ còn lại tính đến hôm nay, số ngày "
            + "quá hạn.")
    @GetMapping("/{type:receivables|payables}/details")
    public ApiResponse<DebtDetails> debtDetails(@AuthenticationPrincipal CurrentUser user, @PathVariable String type,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId) {
        return ApiResponse.ok(dashboard.debtDetails(user, type, companyId, branchId));
    }

    @Operation(summary = "Chi tiết hàng đã / sắp hết hạn", description = "BR-RPT-11. Lô × kho còn tồn, số ngày còn lại "
            + "(âm: đã quá hạn).")
    @GetMapping("/expiring/details")
    public ApiResponse<ExpiryDetails> expiryDetails(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId) {
        return ApiResponse.ok(dashboard.expiryDetails(user, companyId, branchId));
    }

    @Operation(summary = "Chi tiết đơn chưa giao đủ", description = "BR-RPT-11. Đơn mua nháp (và đã duyệt trước D-61) "
            + "chưa ghi sổ; đơn bán chưa ghi sổ hoặc đã ghi sổ khách chưa nhận.")
    @GetMapping("/{kind:purchase-backlog|sales-backlog}/details")
    public ApiResponse<BacklogDetails> backlogDetails(@AuthenticationPrincipal CurrentUser user,
            @PathVariable String kind, @RequestParam Long companyId, @RequestParam(required = false) Long branchId) {
        return ApiResponse.ok(dashboard.backlogDetails(user, kind, companyId, branchId));
    }
}
