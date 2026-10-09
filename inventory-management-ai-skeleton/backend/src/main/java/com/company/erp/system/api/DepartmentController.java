package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.DepartmentService;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateDepartmentRequest;
import com.company.erp.system.application.dto.DepartmentResponse;
import com.company.erp.system.application.dto.UpdateDepartmentRequest;
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

@Tag(name = "Phòng ban")
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('department:view')")
    public ApiResponse<PageResponse<DepartmentResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                                @RequestParam(required = false) Long branchId,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) RecordStatus status,
                                                                Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                departmentService.search(user, branchId, keyword, status, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:view')")
    public ApiResponse<DepartmentResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(departmentService.get(user, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('department:create')")
    public ApiResponse<DepartmentResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                  @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                  @Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.ok(departmentService.create(user, idempotencyKey, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department:update')")
    public ApiResponse<DepartmentResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                  @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.ok(departmentService.update(user, id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('department:update')")
    public ApiResponse<DepartmentResponse> changeStatus(@AuthenticationPrincipal CurrentUser user,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(departmentService.changeStatus(user, id, request));
    }
}
