package com.company.erp.inventory.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.inventory.application.RecallService;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.RecallRequest;
import com.company.erp.inventory.application.dto.RecallResponse;
import com.company.erp.inventory.domain.RecallStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
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

/** Thu hồi lô / serial (BR-INV-42): kích hoạt thì khóa lô / serial; theo dõi khách đã nhận và số đã thu hồi. */
@Tag(name = "Thu hồi")
@RestController
@RequestMapping("/api/v1/recalls")
public class RecallController {

    private final RecallService service;

    public RecallController(RecallService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('recall:view')")
    public ApiResponse<PageResponse<RecallResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) RecallStatus status,
            @RequestParam(required = false) Long productId, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, companyId, status, productId, pageable),
                Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('recall:view')")
    public ApiResponse<RecallResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('recall:create')")
    public ApiResponse<RecallResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody RecallRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Kích hoạt thu hồi", description = "Từ DRAFT (D-61 không qua duyệt): khóa lô / serial.")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('recall:manage')")
    public ApiResponse<RecallResponse> activate(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.activate(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('recall:create')")
    public ApiResponse<RecallResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('recall:manage')")
    public ApiResponse<RecallResponse> close(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.close(user, id, idempotencyKey, request));
    }
}
