package com.company.erp.finance.api;

import com.company.erp.finance.application.DebtAdjustmentPostingService;
import com.company.erp.finance.application.DebtAdjustmentService;
import com.company.erp.finance.application.dto.DebtAdjustmentRequest;
import com.company.erp.finance.application.dto.DebtAdjustmentResponse;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.FinanceDocumentStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Điều chỉnh công nợ (DCCN, BR-AR-07; D-61 không qua duyệt) và thanh toán tạm ứng (TTTU, BR-CSH-04). */
@Tag(name = "Điều chỉnh công nợ, thanh toán tạm ứng")
@RestController
@RequestMapping("/api/v1/debt-adjustments")
public class DebtAdjustmentController {

    private final DebtAdjustmentService service;
    private final DebtAdjustmentPostingService postingService;

    public DebtAdjustmentController(DebtAdjustmentService service, DebtAdjustmentPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('debt-adjustment:view')")
    public ApiResponse<PageResponse<DebtAdjustmentResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String documentType, @RequestParam(required = false) DebtType debtType,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) FinanceDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new DebtAdjustmentService.DebtAdjustmentFilter(companyId, branchId, documentType,
                debtType, status, partnerId, dateFrom, dateTo, keyword), pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('debt-adjustment:view')")
    public ApiResponse<DebtAdjustmentResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập chứng từ", description = "Bắt buộc Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('debt-adjustment:create')")
    public ApiResponse<DebtAdjustmentResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody DebtAdjustmentRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('debt-adjustment:create')")
    public ApiResponse<DebtAdjustmentResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody DebtAdjustmentRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('debt-adjustment:create')")
    public ApiResponse<DebtAdjustmentResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Dòng giảm vượt số còn lại: 422 OVER_ALLOCATION; thiếu quy tắc hạch toán: 422 POSTING_RULE_NOT_FOUND.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('debt-adjustment:post')")
    public ApiResponse<DebtAdjustmentResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Khoản công nợ của dòng tăng đã được thanh toán: 409 HAS_DEPENDENT_DOCUMENTS.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('debt-adjustment:reverse')")
    public ApiResponse<DebtAdjustmentResponse> reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
