package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.UserAdminService;
import com.company.erp.system.application.dto.AssignDataScopesRequest;
import com.company.erp.system.application.dto.AssignRolesRequest;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateUserRequest;
import com.company.erp.system.application.dto.ResetPasswordRequest;
import com.company.erp.system.application.dto.UpdateUserRequest;
import com.company.erp.system.application.dto.UserResponse;
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

@Tag(name = "Người dùng")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAdminService userAdminService;

    public UserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<PageResponse<UserResponse>> search(@AuthenticationPrincipal CurrentUser user,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) RecordStatus status,
                                                          Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(userAdminService.search(user, keyword, status, pageable),
                Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<UserResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(userAdminService.get(user, id));
    }

    @Operation(summary = "Tạo người dùng", description = "Người dùng phải đổi mật khẩu ở lần đăng nhập đầu. "
            + "Vai trò và phạm vi không được vượt quyền của người tạo. Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:create')")
    public ApiResponse<UserResponse> create(@AuthenticationPrincipal CurrentUser user,
                                            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                            @Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userAdminService.create(user, idempotencyKey, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userAdminService.update(user, id, request));
    }

    @Operation(summary = "Kích hoạt / ngừng hoạt động người dùng", description = "Ngừng hoạt động thu hồi mọi phiên.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                  @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(userAdminService.changeStatus(user, id, request));
    }

    @Operation(summary = "Đặt lại mật khẩu", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:reset-password')")
    public ApiResponse<UserResponse> resetPassword(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                   @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                   @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(userAdminService.resetPassword(user, id, idempotencyKey, request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:assign-roles')")
    public ApiResponse<UserResponse> assignRoles(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                 @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.ok(userAdminService.assignRoles(user, id, request));
    }

    @PutMapping("/{id}/data-scopes")
    @PreAuthorize("hasAuthority('user:assign-scopes')")
    public ApiResponse<UserResponse> assignDataScopes(@AuthenticationPrincipal CurrentUser user,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody AssignDataScopesRequest request) {
        return ApiResponse.ok(userAdminService.assignDataScopes(user, id, request));
    }
}
