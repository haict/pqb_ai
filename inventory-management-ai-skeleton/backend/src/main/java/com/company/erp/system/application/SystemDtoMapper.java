package com.company.erp.system.application;

import com.company.erp.system.application.dto.BranchResponse;
import com.company.erp.system.application.dto.CompanyResponse;
import com.company.erp.system.application.dto.DataScopeDto;
import com.company.erp.system.application.dto.DepartmentResponse;
import com.company.erp.system.application.dto.LoginLogResponse;
import com.company.erp.system.application.dto.PermissionResponse;
import com.company.erp.system.application.dto.RoleResponse;
import com.company.erp.system.application.dto.UserResponse;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.Company;
import com.company.erp.system.domain.Department;
import com.company.erp.system.domain.LoginLog;
import com.company.erp.system.domain.Permission;
import com.company.erp.system.domain.Role;
import com.company.erp.system.domain.User;
import java.util.Comparator;

/** Chuyển entity sang DTO; entity không bao giờ ra khỏi module (coding-standard.md mục 8). */
final class SystemDtoMapper {

    private SystemDtoMapper() {
    }

    static CompanyResponse toResponse(Company company) {
        return new CompanyResponse(company.getId(), company.getCode(), company.getName(), company.getTaxCode(),
                company.getAddress(), company.getPhone(), company.getEmail(), company.getStatus(),
                company.getVersion(), company.getUpdatedAt());
    }

    static BranchResponse toResponse(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getCompanyId(), branch.getCode(), branch.getName(),
                branch.getAddress(), branch.getPhone(), branch.getStatus(), branch.getVersion(), branch.getUpdatedAt());
    }

    static DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(department.getId(), department.getBranchId(), department.getCode(),
                department.getName(), department.getStatus(), department.getVersion(), department.getUpdatedAt());
    }

    static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getCompanyId(), user.getDefaultBranchId(), user.getStatus(),
                user.isOwnDataOnly(), user.isMustChangePassword(), user.getLockedUntil(), user.getLastLoginAt(),
                user.getRoles().stream()
                        .sorted(Comparator.comparing(Role::getCode))
                        .map(role -> new UserResponse.RoleSummary(role.getId(), role.getCode(), role.getName()))
                        .toList(),
                user.getDataScopes().stream()
                        .map(scope -> new DataScopeDto(scope.getScopeType(), scope.getScopeId()))
                        .sorted(Comparator.comparing(DataScopeDto::type).thenComparing(DataScopeDto::scopeId))
                        .toList(),
                user.getVersion());
    }

    static RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.isSystemRole(), role.getStatus(),
                role.getPermissions().stream().map(Permission::getCode).sorted().toList(),
                role.getVersion());
    }

    static PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getCode(), permission.getModule(), permission.getName());
    }

    static LoginLogResponse toResponse(LoginLog log) {
        return new LoginLogResponse(log.getId(), log.getOccurredAt(), log.getEvent(), log.getUsername(),
                log.getUserId(), log.getIpAddress(), log.getUserAgent());
    }
}
