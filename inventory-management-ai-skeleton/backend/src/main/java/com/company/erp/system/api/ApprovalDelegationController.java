package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.system.application.ApprovalDelegationService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.CreateDelegationRequest;
import com.company.erp.system.application.dto.DelegationResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mọi người dùng đã đăng nhập ủy quyền được phần duyệt của mình; kiểm tra chi tiết ở service. */
@Tag(name = "Ủy quyền duyệt")
@RestController
@RequestMapping("/api/v1/approval-delegations")
public class ApprovalDelegationController {

    private final ApprovalDelegationService delegationService;

    public ApprovalDelegationController(ApprovalDelegationService delegationService) {
        this.delegationService = delegationService;
    }

    @Operation(summary = "Ủy quyền liên quan đến tôi (người quản lý luồng duyệt: của cả công ty)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<DelegationResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                                Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(delegationService.search(user, pageable), Function.identity()));
    }

    @Operation(summary = "Ủy quyền duyệt có thời hạn", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DelegationResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                  @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                  @Valid @RequestBody CreateDelegationRequest request) {
        return ApiResponse.ok(delegationService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Thu hồi ủy quyền")
    @PostMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> revoke(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        delegationService.revoke(user, id);
        return ApiResponse.ok(null);
    }
}
