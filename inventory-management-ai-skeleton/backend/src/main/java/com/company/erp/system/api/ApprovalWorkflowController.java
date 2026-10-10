package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.ApprovalWorkflowService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ApprovalWorkflowResponse;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateApprovalWorkflowRequest;
import com.company.erp.system.application.dto.UpdateApprovalWorkflowRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Luồng duyệt")
@RestController
@RequestMapping("/api/v1/approval-workflows")
public class ApprovalWorkflowController {

    private final ApprovalWorkflowService workflowService;

    public ApprovalWorkflowController(ApprovalWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Operation(summary = "Danh sách luồng duyệt của công ty")
    @GetMapping
    @PreAuthorize("hasAuthority('approval-workflow:view')")
    public ApiResponse<PageResponse<ApprovalWorkflowResponse>> search(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) RecordStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                workflowService.search(user, companyId, documentType, status, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('approval-workflow:view')")
    public ApiResponse<ApprovalWorkflowResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(workflowService.get(user, id));
    }

    @Operation(summary = "Tạo luồng duyệt", description = "Cần phạm vi toàn công ty. Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('approval-workflow:manage')")
    public ApiResponse<ApprovalWorkflowResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                        @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                        @Valid @RequestBody CreateApprovalWorkflowRequest request) {
        return ApiResponse.ok(workflowService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa luồng duyệt", description = "Yêu cầu duyệt đang chờ không bị ảnh hưởng.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('approval-workflow:manage')")
    public ApiResponse<ApprovalWorkflowResponse> update(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody UpdateApprovalWorkflowRequest request) {
        return ApiResponse.ok(workflowService.update(user, id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('approval-workflow:manage')")
    public ApiResponse<ApprovalWorkflowResponse> changeStatus(@AuthenticationPrincipal CurrentUser user,
                                                              @PathVariable Long id,
                                                              @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(workflowService.changeStatus(user, id, request));
    }
}
