package com.company.erp.finance.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.finance.application.DebtQueryService;
import com.company.erp.finance.application.dto.AgingResponse;
import com.company.erp.finance.application.dto.DebtItemResponse;
import com.company.erp.finance.application.dto.DebtLedgerResponse;
import com.company.erp.finance.application.dto.DebtPrepaymentResponse;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tra cứu công nợ phải thu / phải trả (chỉ đọc). Không có endpoint sửa số dư công nợ (api.md mục 2): công nợ chỉ đổi
 * qua ghi sổ chứng từ.
 */
@Tag(name = "Công nợ")
@RestController
@RequestMapping("/api/v1")
public class DebtController {

    private final DebtQueryService queryService;

    public DebtController(DebtQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Khoản công nợ theo hóa đơn", description = "openOnly: chỉ khoản còn nợ; overdueOnly: chỉ "
            + "khoản quá hạn tại ngày hiện tại.")
    @GetMapping("/debt-items")
    @PreAuthorize("hasAuthority('debt:view')")
    public ApiResponse<PageResponse<DebtItemResponse>> items(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam DebtType debtType,
            @RequestParam(required = false) Long branchId, @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean openOnly,
            @RequestParam(required = false) Boolean overdueOnly, Pageable pageable) {
        DebtQueryService.DebtItemFilter filter = new DebtQueryService.DebtItemFilter(companyId, debtType, branchId,
                partnerId, keyword, openOnly, overdueOnly);
        return ApiResponse.ok(PageResponse.from(queryService.items(user, filter, pageable), Function.identity()));
    }

    @Operation(summary = "Tuổi nợ theo đối tượng (BR-AR-05)", description = "Mốc tuổi nợ lấy từ tham số "
            + "finance.aging-bucket-*-days; asOf mặc định hôm nay.")
    @GetMapping("/debt-items/aging")
    @PreAuthorize("hasAuthority('debt:view')")
    public ApiResponse<AgingResponse> aging(@AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam DebtType debtType, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            Pageable pageable) {
        return ApiResponse.ok(queryService.aging(user, companyId, debtType, branchId, partnerId, asOf, pageable));
    }

    @Operation(summary = "Sổ chi tiết công nợ theo đối tượng", description = "Số dư đầu kỳ, phát sinh, số dư lũy kế.")
    @GetMapping("/debt-ledger")
    @PreAuthorize("hasAuthority('debt:view')")
    public ApiResponse<DebtLedgerResponse> ledger(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam DebtType debtType, @RequestParam Long partnerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(queryService.ledger(user, companyId, debtType, partnerId, dateFrom, dateTo));
    }

    @Operation(summary = "Tiền trả trước còn chưa bù trừ", description = "Theo chi nhánh trong phạm vi (BR-AR-04).")
    @GetMapping("/debt-prepayments")
    @PreAuthorize("hasAuthority('debt:view')")
    public ApiResponse<List<DebtPrepaymentResponse>> prepayments(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam DebtType debtType, @RequestParam Long partnerId) {
        return ApiResponse.ok(queryService.prepayments(user, companyId, debtType, partnerId));
    }
}
