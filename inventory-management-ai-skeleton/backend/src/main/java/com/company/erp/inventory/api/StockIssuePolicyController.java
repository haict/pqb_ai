package com.company.erp.inventory.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.inventory.application.StockIssuePolicyService;
import com.company.erp.inventory.application.dto.CommentRequest;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.StockIssuePolicyRequest;
import com.company.erp.inventory.application.dto.StockIssuePolicyResponse;
import com.company.erp.inventory.domain.PolicyStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Chính sách xuất vượt tồn khả dụng (BR-GEN-31, D-10): có hiệu lực sau khi duyệt, dùng dần hạn mức khi ghi sổ. */
@Tag(name = "Chính sách xuất vượt tồn")
@RestController
@RequestMapping("/api/v1/stock-issue-policies")
public class StockIssuePolicyController {

    private final StockIssuePolicyService service;

    public StockIssuePolicyController(StockIssuePolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('stock-policy:view')")
    public ApiResponse<PageResponse<StockIssuePolicyResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) PolicyStatus status, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, companyId, warehouseId, status, pageable),
                Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('stock-policy:view')")
    public ApiResponse<StockIssuePolicyResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('stock-policy:create')")
    public ApiResponse<StockIssuePolicyResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody StockIssuePolicyRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('stock-policy:create')")
    public ApiResponse<StockIssuePolicyResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.submit(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('stock-policy:approve')")
    public ApiResponse<StockIssuePolicyResponse> approve(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) CommentRequest request) {
        return ApiResponse.ok(service.approve(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('stock-policy:approve')")
    public ApiResponse<StockIssuePolicyResponse> reject(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.reject(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('stock-policy:create')")
    public ApiResponse<StockIssuePolicyResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }
}
