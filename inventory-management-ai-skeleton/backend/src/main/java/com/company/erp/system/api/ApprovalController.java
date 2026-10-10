package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.system.application.ApprovalQueryService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ApprovalRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Việc chờ duyệt. Duyệt / từ chối thực hiện ở endpoint hành động của chứng từ ({@code POST /{resource}/{id}/approve}). */
@Tag(name = "Phê duyệt")
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalQueryService queryService;

    public ApprovalController(ApprovalQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Chứng từ đang chờ tôi duyệt (kể cả duyệt thay theo ủy quyền)")
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<ApprovalRequestResponse>> pending(@AuthenticationPrincipal CurrentUser user,
                                                                      Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(queryService.pending(user, pageable), Function.identity()));
    }

    @Operation(summary = "Chi tiết yêu cầu duyệt", description = "Không liên quan đến yêu cầu: 404.")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ApprovalRequestResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(queryService.get(user, id));
    }
}
