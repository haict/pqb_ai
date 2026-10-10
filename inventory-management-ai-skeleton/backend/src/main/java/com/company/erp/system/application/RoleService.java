package com.company.erp.system.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.system.application.dto.AssignPermissionsRequest;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateRoleRequest;
import com.company.erp.system.application.dto.PermissionResponse;
import com.company.erp.system.application.dto.RoleResponse;
import com.company.erp.system.application.dto.UpdateRoleRequest;
import com.company.erp.system.domain.Permission;
import com.company.erp.system.domain.Role;
import com.company.erp.system.infrastructure.persistence.PermissionRepository;
import com.company.erp.system.infrastructure.persistence.RoleRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vai trò và gán quyền (BR-SYS-02, BR-SYS-03). Chỉ gán được quyền mà người thao tác đang có. */
@Service
public class RoleService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt");
    private static final String ENDPOINT_CREATE = "POST /api/v1/roles";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;
    private final UserRepository userRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                       IdempotencyService idempotency, AuditLogService auditLog, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> search(String keyword, RecordStatus status, Pageable pageable) {
        return roleRepository.findAll(Specs.allOf(
                        Specs.<Role>keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(SystemDtoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        return SystemDtoMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions() {
        return permissionRepository.findAllByOrderByModuleAscSortOrderAsc().stream()
                .map(SystemDtoMapper::toResponse)
                .toList();
    }

    @Transactional
    public RoleResponse create(CurrentUser actor, String idempotencyKey, CreateRoleRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_CREATE, request);
        return idempotency.execute(command, RoleResponse.class, () -> {
            if (roleRepository.existsByCode(request.code())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE, "Mã vai trò '" + request.code() + "' đã tồn tại.");
            }
            Role role = roleRepository.saveAndFlush(new Role(request.code(), request.name(), request.description()));
            RoleResponse response = SystemDtoMapper.toResponse(role);
            audit(actor, AuditActions.CREATE, role, null, null, response);
            return response;
        });
    }

    @Transactional
    public RoleResponse update(CurrentUser actor, Long id, UpdateRoleRequest request) {
        Role role = find(id);
        role.verifyVersion(request.version());
        RoleResponse before = SystemDtoMapper.toResponse(role);
        role.update(request.name(), request.description());
        RoleResponse after = SystemDtoMapper.toResponse(roleRepository.saveAndFlush(role));
        audit(actor, AuditActions.UPDATE, role, null, before, after);
        return after;
    }

    @Transactional
    public RoleResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        Role role = find(id);
        role.verifyVersion(request.version());
        if (role.isSystemRole() && request.status() == RecordStatus.INACTIVE) {
            throw BusinessException.invalidField("status", "Không thể ngừng hoạt động vai trò hệ thống.");
        }
        RoleResponse before = SystemDtoMapper.toResponse(role);
        role.changeStatus(request.status());
        RoleResponse after = SystemDtoMapper.toResponse(roleRepository.saveAndFlush(role));
        audit(actor, AuditActions.CHANGE_STATUS, role, request.reason(), before, after);
        return after;
    }

    @Transactional
    public RoleResponse assignPermissions(CurrentUser actor, Long id, AssignPermissionsRequest request) {
        Role role = find(id);
        if (holdsRole(actor, role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không thể thay đổi quyền của vai trò bạn đang giữ.");
        }
        Set<String> requested = Set.copyOf(request.permissionCodes());
        List<Permission> permissions = permissionRepository.findByCodeIn(requested);
        if (permissions.size() != requested.size()) {
            Set<String> unknown = new HashSet<>(requested);
            permissions.forEach(permission -> unknown.remove(permission.getCode()));
            throw BusinessException.invalidField("permissionCodes", "Quyền không tồn tại: " + unknown);
        }
        // Quản trị có quyền "gán mọi quyền" cấu hình được vai trò nghiệp vụ mà bản thân không dùng (docs/07 mục 2).
        List<String> beyondActor = actor.hasPermission(AccessGrants.GRANT_ANY_PERMISSION) ? List.of()
                : requested.stream().filter(code -> !actor.hasPermission(code)).sorted().toList();
        if (!beyondActor.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không thể gán quyền bạn không có: " + beyondActor);
        }
        RoleResponse before = SystemDtoMapper.toResponse(role);
        role.replacePermissions(Set.copyOf(permissions));
        RoleResponse after = SystemDtoMapper.toResponse(roleRepository.saveAndFlush(role));
        audit(actor, AuditActions.ASSIGN_PERMISSIONS, role, null, before.permissions(), after.permissions());
        return after;
    }

    private Role find(Long id) {
        return roleRepository.findWithPermissionsById(id).orElseThrow(BusinessException::notFound);
    }

    private void audit(CurrentUser actor, String action, Role role, String reason, Object before, Object after) {
        auditLog.record(new AuditEntry(actor.id(), action, AuditActions.ROLE, role.getId(), role.getCode(), reason,
                before, after, null, null));
    }

    /** Không tự nâng quyền của mình qua việc sửa quyền của vai trò mình đang giữ. */
    private boolean holdsRole(CurrentUser actor, Role role) {
        return userRepository.findWithAuthoritiesById(actor.id())
                .map(user -> user.getRoles().stream().anyMatch(held -> held.getId().equals(role.getId())))
                .orElse(false);
    }
}
