package com.company.erp.finance.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.finance.application.CashBookQueryService;
import com.company.erp.finance.application.dto.CashBookResponse;
import com.company.erp.finance.application.dto.CashFlowResponse;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sổ quỹ, sổ tiền gửi và dòng tiền thực tế (chỉ đọc, tính từ sổ quỹ). */
@Tag(name = "Sổ quỹ, dòng tiền")
@RestController
public class CashBookController {

    private final CashBookQueryService service;

    public CashBookController(CashBookQueryService service) {
        this.service = service;
    }

    @Operation(summary = "Sổ quỹ / sổ tiền gửi", description = "Số dư đầu kỳ, từng dòng tiền vào / ra kèm chứng từ "
            + "nguồn và số dư lũy kế; khoảng ngày tối đa 366 ngày.")
    @GetMapping("/api/v1/cash-book")
    @PreAuthorize("hasAuthority('cash-book:view')")
    public ApiResponse<CashBookResponse> cashBook(@AuthenticationPrincipal CurrentUser user,
                                                  @RequestParam Long cashAccountId, @RequestParam LocalDate dateFrom,
                                                  @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(service.cashBook(user, cashAccountId, dateFrom, dateTo));
    }

    @Operation(summary = "Dòng tiền thực tế", description = "Theo quỹ và theo nội dung thu chi trong khoảng ngày.")
    @GetMapping("/api/v1/cash-flow")
    @PreAuthorize("hasAuthority('cash-book:view')")
    public ApiResponse<CashFlowResponse> cashFlow(@AuthenticationPrincipal CurrentUser user,
                                                  @RequestParam Long companyId,
                                                  @RequestParam(required = false) Long branchId,
                                                  @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(service.cashFlow(user, companyId, branchId, dateFrom, dateTo));
    }
}
