package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.BranchService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.BranchResponse;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateBranchRequest;
import com.company.erp.system.application.dto.UpdateBranchRequest;
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

@Tag(name = "Chi nhánh")
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @Operation(summary = "Danh sách chi nhánh trong phạm vi dữ liệu")
    @GetMapping
    @PreAuthorize("hasAuthority('branch:view')")
    public ApiResponse<PageResponse<BranchResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                            @RequestParam(required = false) Long companyId,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) RecordStatus status,
                                                            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(branchService.search(user, companyId, keyword, status, pageable),
                Function.identity()));
    }

    @Operation(summary = "Chi tiết chi nhánh", description = "Ngoài phạm vi dữ liệu: 404.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('branch:view')")
    public ApiResponse<BranchResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(branchService.get(user, id));
    }

    @Operation(summary = "Tạo chi nhánh", description = "Cần phạm vi toàn công ty. Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('branch:create')")
    public ApiResponse<BranchResponse> create(@AuthenticationPrincipal CurrentUser user,
                                              @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                              @Valid @RequestBody CreateBranchRequest request) {
        return ApiResponse.ok(branchService.create(user, idempotencyKey, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('branch:update')")
    public ApiResponse<BranchResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                              @Valid @RequestBody UpdateBranchRequest request) {
        return ApiResponse.ok(branchService.update(user, id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('branch:update')")
    public ApiResponse<BranchResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                    @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(branchService.changeStatus(user, id, request));
    }
}
