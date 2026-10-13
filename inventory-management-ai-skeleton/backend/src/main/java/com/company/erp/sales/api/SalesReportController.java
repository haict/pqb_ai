package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.sales.application.SalesReportService;
import com.company.erp.sales.application.dto.SalesSourceDocument;
import com.company.erp.sales.application.dto.SalesSummaryResponse;
import com.company.erp.sales.domain.SalesReportGrouping;
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

/** Báo cáo doanh số có truy ngược chứng từ (BR-SAL-51). */
@Tag(name = "Bán hàng - báo cáo")
@RestController
@RequestMapping("/api/v1/sales")
public class SalesReportController {

    private final SalesReportService reports;

    public SalesReportController(SalesReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "Báo cáo doanh số", description = "Nhóm theo SALES_USER, CUSTOMER, PRODUCT, BRANCH; từ đơn bán, "
            + "trả hàng / giảm giá đã ghi sổ theo ngày hạch toán. Lợi nhuận gộp: Giai đoạn 8.")
    @GetMapping("/reports/summary")
    @PreAuthorize("hasAuthority('sales-report:view')")
    public ApiResponse<SalesSummaryResponse> summary(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo,
            @RequestParam SalesReportGrouping groupBy) {
        return ApiResponse.ok(reports.summary(user, companyId, branchId, dateFrom, dateTo, groupBy));
    }

    @Operation(summary = "Chứng từ cấu thành một nhóm", description = "Truy ngược số liệu báo cáo doanh số.")
    @GetMapping("/reports/sources")
    @PreAuthorize("hasAuthority('sales-report:view')")
    public ApiResponse<List<SalesSourceDocument>> sources(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo,
            @RequestParam SalesReportGrouping groupBy, @RequestParam(required = false) Long key) {
        return ApiResponse.ok(reports.sources(user, companyId, branchId, dateFrom, dateTo, groupBy, key));
    }
}
