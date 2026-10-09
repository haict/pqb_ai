package com.company.erp.system.application;

import java.util.Set;

/**
 * Người dùng đã xác thực của request hiện tại, nạp từ database ở mỗi request (khóa tài khoản,
 * đổi quyền có hiệu lực ngay). Lấy trong controller bằng {@code @AuthenticationPrincipal CurrentUser}.
 */
public record CurrentUser(
        Long id,
        String username,
        String fullName,
        Long companyId,
        Long defaultBranchId,
        boolean mustChangePassword,
        Set<String> permissions,
        DataScope dataScope) {

    public CurrentUser {
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
