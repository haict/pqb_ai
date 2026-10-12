package com.company.erp.finance.api;

import com.company.erp.finance.application.DebtOffsetPostingService;
import com.company.erp.finance.application.DebtOffsetService;
import com.company.erp.finance.application.dto.DebtOffsetRequest;
import com.company.erp.finance.application.dto.DebtOffsetResponse;
import com.company.erp.finance.domain.OffsetType;
import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.finance.application.dto.CommentRequest;
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

/** Chứng từ bù trừ công nợ (DTCN, BR-AR-04): trả trước với hóa đơn, phải thu với phải trả cùng đối tượng. */
@Tag(name = "Bù trừ công nợ")
@RestController
@RequestMapping("/api/v1/debt-offsets")
public class DebtOffsetController {

    private final DebtOffsetService service;
    private final DebtOffsetPostingService postingService;

    public DebtOffsetController(DebtOffsetService service, DebtOffsetPostingService postingService) {
        this.service = service;
        this.postingService = postingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('debt-offset:view')")
    public ApiResponse<PageResponse<DebtOffsetResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) OffsetType offsetType, @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) FinanceDocumentStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, new DebtOffsetService.DebtOffsetFilter(companyId, branchId, offsetType, status, partnerId,
                dateFrom, dateTo, keyword), pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('debt-offset:view')")
    public ApiResponse<DebtOffsetResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @Operation(summary = "Lập chứng từ", description = "Bắt buộc Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('debt-offset:create')")
    public ApiResponse<DebtOffsetResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody DebtOffsetRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa (DRAFT, REJECTED)", description = "Gửi version; đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('debt-offset:create')")
    public ApiResponse<DebtOffsetResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody DebtOffsetRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('debt-offset:create')")
    public ApiResponse<DebtOffsetResponse> submit(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('debt-offset:approve')")
    public ApiResponse<DebtOffsetResponse> approve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('debt-offset:approve')")
    public ApiResponse<DebtOffsetResponse> reject(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('debt-offset:create')")
    public ApiResponse<DebtOffsetResponse> cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Ghi sổ", description = "Trả trước không đủ / vượt số còn lại: 422 OVER_ALLOCATION; kỳ công nợ khóa: 422 PERIOD_LOCKED.")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('debt-offset:post')")
    public ApiResponse<DebtOffsetResponse> post(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(postingService.post(user, id, idempotencyKey));
    }

    @Operation(summary = "Đảo", description = "Bỏ đối trừ; bắt buộc lý do.")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('debt-offset:reverse')")
    public ApiResponse<DebtOffsetResponse> reverse(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(postingService.reverse(user, id, idempotencyKey, request));
    }
}
