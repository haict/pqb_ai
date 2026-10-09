package com.company.erp.system.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/** Tạo người dùng; người dùng phải đổi mật khẩu ban đầu ở lần đăng nhập đầu tiên. */
public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,50}", message = "Tên đăng nhập gồm 3-50 ký tự a-z, 0-9, . _ -")
        String username,
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @NotNull Long companyId,
        Long defaultBranchId,
        boolean ownDataOnly,
        @NotBlank @Size(max = 200) String initialPassword,
        Set<Long> roleIds,
        @Valid List<DataScopeDto> dataScopes) {

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + "]";
    }
}
