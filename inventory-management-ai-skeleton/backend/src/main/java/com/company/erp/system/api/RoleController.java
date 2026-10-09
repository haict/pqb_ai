package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.RoleService;
import com.company.erp.system.application.dto.AssignPermissionsRequest;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateRoleRequest;
import com.company.erp.system.application.dto.PermissionResponse;
import com.company.erp.system.application.dto.RoleResponse;
import com.company.erp.system.application.dto.UpdateRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vai trò và quyền")
@RestController
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/api/v1/roles")
    @PreAuthorize("hasAuthority('role:view')")
    public ApiResponse<PageResponse<RoleResponse>> search(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) RecordStatus status,
                                                          Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(roleService.search(keyword, status, pageable), Function.identity()));
    }

    @GetMapping("/api/v1/roles/{id}")
    @PreAuthorize("hasAuthority('role:view')")
    public ApiResponse<RoleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping("/api/v1/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('role:create')")
    public ApiResponse<RoleResponse> create(@AuthenticationPrincipal CurrentUser user,
                                            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                            @Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(roleService.create(user, idempotencyKey, request));
    }

    @PutMapping("/api/v1/roles/{id}")
    @PreAuthorize("hasAuthority('role:update')")
    public ApiResponse<RoleResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                            @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok(roleService.update(user, id, request));
    }

    @PatchMapping("/api/v1/roles/{id}/status")
    @PreAuthorize("hasAuthority('role:update')")
    public ApiResponse<RoleResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                  @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(roleService.changeStatus(user, id, request));
    }

    @Operation(summary = "Thay toàn bộ quyền của vai trò", description = "Chỉ gán được quyền mà bạn đang có.")
    @PutMapping("/api/v1/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:assign-permissions')")
    public ApiResponse<RoleResponse> assignPermissions(@AuthenticationPrincipal CurrentUser user,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody AssignPermissionsRequest request) {
        return ApiResponse.ok(roleService.assignPermissions(user, id, request));
    }

    @Operation(summary = "Danh mục quyền của hệ thống")
    @GetMapping("/api/v1/permissions")
    @PreAuthorize("hasAuthority('role:view')")
    public ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.ok(roleService.permissions());
    }
}
