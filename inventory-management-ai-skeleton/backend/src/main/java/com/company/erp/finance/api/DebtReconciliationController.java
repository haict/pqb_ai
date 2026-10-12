package com.company.erp.finance.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.finance.application.DebtReconciliationService;
import com.company.erp.finance.application.dto.ConfirmReconciliationRequest;
import com.company.erp.finance.application.dto.DebtReconciliationRequest;
import com.company.erp.finance.application.dto.DebtReconciliationResponse;
import com.company.erp.finance.application.dto.DebtStatementResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.ReconciliationStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đối chiếu công nợ (BR-AR-06): bảng đối chiếu và biên bản xác nhận số dư với đối tượng. */
@Tag(name = "Đối chiếu công nợ")
@RestController
public class DebtReconciliationController {

    private static final String BASE = "/api/v1/debt-reconciliations";

    private final DebtReconciliationService service;

    public DebtReconciliationController(DebtReconciliationService service) {
        this.service = service;
    }

    @Operation(summary = "Bảng đối chiếu công nợ", description = "Số dư đầu, phát sinh theo chứng từ, số dư cuối, hóa "
            + "đơn còn nợ, trả trước. branchId trống = mọi chi nhánh trong phạm vi.")
    @GetMapping("/api/v1/debt-statements")
    @PreAuthorize("hasAuthority('debt-reconciliation:view')")
    public ApiResponse<DebtStatementResponse> statement(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam DebtType debtType, @RequestParam Long partnerId, @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(service.statement(user, companyId, branchId, debtType, partnerId, dateFrom, dateTo));
    }

    @GetMapping(BASE)
    @PreAuthorize("hasAuthority('debt-reconciliation:view')")
    public ApiResponse<PageResponse<DebtReconciliationResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) DebtType debtType,
            @RequestParam(required = false) Long partnerId, @RequestParam(required = false) ReconciliationStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new DebtReconciliationService
                .ReconciliationFilter(companyId, debtType, partnerId, status), pageable), Function.identity()));
    }

    @GetMapping(BASE + "/{id}")
    @PreAuthorize("hasAuthority('debt-reconciliation:view')")
    public ApiResponse<DebtReconciliationResponse> get(@AuthenticationPrincipal CurrentUser user,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập biên bản", description = "Chốt số dư hệ thống đến hết ngày đối chiếu. branchId trống "
            + "(toàn công ty) cần phạm vi toàn công ty: 403.")
    @PostMapping(BASE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('debt-reconciliation:manage')")
    public ApiResponse<DebtReconciliationResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody DebtReconciliationRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Ghi nhận xác nhận", description = "MATCHED khi số xác nhận bằng số hệ thống, ngược lại "
            + "DIFFERENT (xử lý bằng điều chỉnh công nợ có duyệt).")
    @PostMapping(BASE + "/{id}/confirm")
    @PreAuthorize("hasAuthority('debt-reconciliation:manage')")
    public ApiResponse<DebtReconciliationResponse> confirm(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ConfirmReconciliationRequest request) {
        return ApiResponse.ok(service.confirm(user, id, idempotencyKey, request));
    }

    @PostMapping(BASE + "/{id}/cancel")
    @PreAuthorize("hasAuthority('debt-reconciliation:manage')")
    public ApiResponse<DebtReconciliationResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }
}
