package com.company.erp.reporting.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.reporting.application.CashForecastService;
import com.company.erp.reporting.application.dto.CashForecastResponse.Forecast;
import com.company.erp.reporting.application.dto.CashForecastResponse.Items;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dòng tiền dự kiến M10 đợt 10D (BR-RPT-09, 30, BR-CSH-06, ADR-18). Quyền {@code cash-forecast:view}. */
@Tag(name = "Báo cáo - dòng tiền dự kiến")
@RestController
@RequestMapping("/api/v1/reports")
public class CashForecastController {

    private final CashForecastService forecasts;

    public CashForecastController(CashForecastService forecasts) {
        this.forecasts = forecasts;
    }

    @Operation(summary = "Dòng tiền dự kiến", description = "Số dư quỹ / ngân hàng hôm nay; dự thu (phải thu còn lại theo "
            + "hạn, đơn bán đã duyệt chưa ghi sổ), dự chi (phải trả còn lại theo hạn, đơn mua đã duyệt chưa ghi sổ) theo "
            + "kỳ từ hôm nay. granularity DAY / WEEK (mặc định) / MONTH, periods mặc định 12. Cột OVERDUE: ngày dự kiến "
            + "trước hôm nay; LATER: sau kỳ cuối.")
    @GetMapping("/cash-flow-forecast")
    @PreAuthorize("hasAuthority('cash-forecast:view')")
    public ApiResponse<Forecast> forecast(@AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) Long branchId, @RequestParam(required = false) String granularity,
            @RequestParam(required = false) Integer periods) {
        return ApiResponse.ok(forecasts.forecast(user, companyId, branchId, granularity, periods));
    }

    @Operation(summary = "Khoản cấu thành dòng tiền dự kiến", description = "BR-RPT-11. Lọc source (RECEIVABLE, "
            + "SALES_ORDER, PAYABLE, PURCHASE_ORDER) và khoảng ngày dự kiến của cột; tổng bằng số trong ô.")
    @GetMapping("/cash-flow-forecast/items")
    @PreAuthorize("hasAuthority('cash-forecast:view')")
    public ApiResponse<Items> items(@AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) Long branchId, @RequestParam(required = false) String source,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo) {
        return ApiResponse.ok(forecasts.items(user, companyId, branchId, source, dateFrom, dateTo));
    }
}
