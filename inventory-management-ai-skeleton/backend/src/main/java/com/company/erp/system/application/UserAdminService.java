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
import com.company.erp.system.application.dto.AssignDataScopesRequest;
import com.company.erp.system.application.dto.AssignRolesRequest;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateUserRequest;
import com.company.erp.system.application.dto.DataScopeDto;
import com.company.erp.system.application.dto.ResetPasswordRequest;
import com.company.erp.system.application.dto.UpdateUserRequest;
import com.company.erp.system.application.dto.UserResponse;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.Permission;
import com.company.erp.system.domain.RefreshToken.RevokeReason;
import com.company.erp.system.domain.Role;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.RoleRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản trị người dùng (BR-SYS-02..06). Chống leo thang quyền: chỉ gán vai trò có quyền nằm trong quyền
 * của người thao tác, chỉ gán phạm vi nằm trong phạm vi của người thao tác, không tự sửa quyền của mình.
 */
@Service
public class UserAdminService {

    private static final Set<String> SORTABLE = Set.of("username", "fullName", "createdAt", "lastLoginAt");
    private static final String ENDPOINT_CREATE = "POST /api/v1/users";
    private static final String ENDPOINT_RESET_PASSWORD = "POST /api/v1/users/{id}/reset-password";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthSessionTransactions sessions;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;
    private final Clock clock;
    private final WarehouseDirectory warehouseDirectory;

    public UserAdminService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            BranchRepository branchRepository,
                            PasswordEncoder passwordEncoder,
                            PasswordPolicy passwordPolicy,
                            AuthSessionTransactions sessions,
                            IdempotencyService idempotency,
                            AuditLogService auditLog,
                            Clock clock,
                            WarehouseDirectory warehouseDirectory) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessions = sessions;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
        this.clock = clock;
        this.warehouseDirectory = warehouseDirectory;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(CurrentUser actor, String keyword, RecordStatus status, Pageable pageable) {
        return userRepository.findAll(Specs.allOf(
                        visibleTo(actor),
                        Specs.keyword(keyword, "username", "fullName"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("username")))
                .map(SystemDtoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(CurrentUser actor, Long id) {
        return SystemDtoMapper.toResponse(findVisible(actor, id));
    }

    @Transactional
    public UserResponse create(CurrentUser actor, String idempotencyKey, CreateUserRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_CREATE, request);
        return idempotency.execute(command, UserResponse.class, () -> {
            requireCanPlaceUser(actor, request.companyId(), request.defaultBranchId());
            if (userRepository.existsByUsername(request.username())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE,
                        "Tên đăng nhập '" + request.username() + "' đã tồn tại.");
            }
            passwordPolicy.validate("initialPassword", request.initialPassword(), request.username());
            User user = new User(request.companyId(), request.defaultBranchId(), request.username(),
                    request.fullName(), request.email(), request.phone(),
                    passwordEncoder.encode(request.initialPassword()), request.ownDataOnly(), Instant.now(clock));
            user.replaceRoles(assignableRoles(actor, nullToEmpty(request.roleIds())));
            user.replaceDataScopes(assignableScopes(actor, request.dataScopes() == null ? List.of() : request.dataScopes()));
            UserResponse response = SystemDtoMapper.toResponse(userRepository.saveAndFlush(user));
            audit(actor, AuditActions.CREATE, user, null, null, response);
            return response;
        });
    }

    @Transactional
    public UserResponse update(CurrentUser actor, Long id, UpdateUserRequest request) {
        User user = findVisible(actor, id);
        user.verifyVersion(request.version());
        requireCanPlaceUser(actor, user.getCompanyId(), request.defaultBranchId());
        UserResponse before = SystemDtoMapper.toResponse(user);
        user.updateProfile(request.defaultBranchId(), request.fullName(), request.email(), request.phone(),
                request.ownDataOnly());
        UserResponse after = SystemDtoMapper.toResponse(userRepository.saveAndFlush(user));
        audit(actor, AuditActions.UPDATE, user, null, before, after);
        return after;
    }

    /** Ngừng hoạt động thì thu hồi mọi phiên; access token còn hạn bị từ chối ngay vì nạp lại người dùng mỗi request. */
    @Transactional
    public UserResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        User user = findVisibleOther(actor, id);
        user.verifyVersion(request.version());
        UserResponse before = SystemDtoMapper.toResponse(user);
        user.changeStatus(request.status());
        if (request.status() == RecordStatus.INACTIVE) {
            sessions.revokeAllSessions(user.getId(), RevokeReason.USER_DEACTIVATED);
        }
        UserResponse after = SystemDtoMapper.toResponse(userRepository.saveAndFlush(user));
        audit(actor, AuditActions.CHANGE_STATUS, user, request.reason(), before, after);
        return after;
    }

    /** Đặt lại mật khẩu: người dùng phải đổi ở lần đăng nhập sau; mọi phiên hiện có bị thu hồi. */
    @Transactional
    public UserResponse resetPassword(CurrentUser actor, Long id, String idempotencyKey, ResetPasswordRequest request) {
        // Không đưa mật khẩu (kể cả dạng băm) vào bảng chống trùng; khóa theo người dùng đích là đủ.
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                ENDPOINT_RESET_PASSWORD.replace("{id}", id.toString()), id);
        return idempotency.execute(command, UserResponse.class, () -> {
            User user = findVisibleOther(actor, id);
            passwordPolicy.validate("newPassword", request.newPassword(), user.getUsername());
            user.changePassword(passwordEncoder.encode(request.newPassword()), Instant.now(clock), true);
            sessions.revokeAllSessions(user.getId(), RevokeReason.PASSWORD_CHANGED);
            userRepository.saveAndFlush(user);
            userRepository.resetLoginFailures(user.getId());
            User reloaded = userRepository.findById(id).orElseThrow(BusinessException::notFound);
            audit(actor, AuditActions.RESET_PASSWORD, reloaded, null, null, null);
            return SystemDtoMapper.toResponse(reloaded);
        });
    }

    @Transactional
    public UserResponse assignRoles(CurrentUser actor, Long id, AssignRolesRequest request) {
        User user = findVisibleOther(actor, id);
        UserResponse before = SystemDtoMapper.toResponse(user);
        user.replaceRoles(assignableRoles(actor, request.roleIds()));
        UserResponse after = SystemDtoMapper.toResponse(userRepository.saveAndFlush(user));
        audit(actor, AuditActions.ASSIGN_ROLES, user, null, before.roles(), after.roles());
        return after;
    }

    @Transactional
    public UserResponse assignDataScopes(CurrentUser actor, Long id, AssignDataScopesRequest request) {
        User user = findVisibleOther(actor, id);
        UserResponse before = SystemDtoMapper.toResponse(user);
        user.replaceDataScopes(assignableScopes(actor, request.dataScopes()));
        UserResponse after = SystemDtoMapper.toResponse(userRepository.saveAndFlush(user));
        audit(actor, AuditActions.ASSIGN_SCOPES, user, null, before.dataScopes(), after.dataScopes());
        return after;
    }

    private static Specification<User> visibleTo(CurrentUser actor) {
        Specification<User> wholeCompany = Specs.in("companyId", actor.dataScope().fullCompanyIds());
        Specification<User> ownBranches = Specs.in("defaultBranchId", actor.dataScope().branchIds());
        return (root, query, cb) -> cb.or(wholeCompany.toPredicate(root, query, cb),
                ownBranches.toPredicate(root, query, cb));
    }

    private User findVisible(CurrentUser actor, Long id) {
        User user = userRepository.findById(id).orElseThrow(BusinessException::notFound);
        boolean visible = actor.dataScope().hasFullCompanyAccess(user.getCompanyId())
                || actor.dataScope().canAccessBranch(user.getDefaultBranchId());
        if (!visible) {
            throw BusinessException.notFound();
        }
        return user;
    }

    private User findVisibleOther(CurrentUser actor, Long id) {
        if (actor.id().equals(id)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Không thể tự thay đổi trạng thái, vai trò, phạm vi hoặc đặt lại mật khẩu của chính mình.");
        }
        return findVisible(actor, id);
    }

    private void requireCanPlaceUser(CurrentUser actor, Long companyId, Long defaultBranchId) {
        if (defaultBranchId != null) {
            Branch branch = branchRepository.findById(defaultBranchId)
                    .filter(found -> actor.dataScope().canAccessBranch(found.getId()))
                    .orElseThrow(() -> BusinessException.invalidField("defaultBranchId",
                            "Chi nhánh không tồn tại hoặc ngoài phạm vi của bạn."));
            if (!branch.getCompanyId().equals(companyId)) {
                throw BusinessException.invalidField("defaultBranchId", "Chi nhánh không thuộc công ty của người dùng.");
            }
            return;
        }
        if (!actor.dataScope().hasFullCompanyAccess(companyId)) {
            throw BusinessException.invalidField("companyId",
                    "Cần phạm vi toàn công ty để tạo người dùng không gắn chi nhánh.");
        }
    }

    private Set<Role> assignableRoles(CurrentUser actor, Collection<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != Set.copyOf(roleIds).size()) {
            throw BusinessException.invalidField("roleIds", "Có vai trò không tồn tại.");
        }
        for (Role role : roles) {
            if (role.getStatus() != RecordStatus.ACTIVE) {
                throw BusinessException.invalidField("roleIds", "Vai trò '" + role.getCode() + "' đã ngừng hoạt động.");
            }
            boolean withinActorPermissions = actor.hasPermission(AccessGrants.GRANT_ANY_PERMISSION)
                    || role.getPermissions().stream().map(Permission::getCode).allMatch(actor::hasPermission);
            if (!withinActorPermissions) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "Không thể gán vai trò '" + role.getCode() + "' có quyền vượt quá quyền của bạn.");
            }
        }
        return Set.copyOf(roles);
    }

    private List<User.ScopeAssignment> assignableScopes(CurrentUser actor, List<DataScopeDto> scopes) {
        for (DataScopeDto scope : scopes) {
            boolean allowed = switch (scope.type()) {
                case COMPANY -> actor.dataScope().hasFullCompanyAccess(scope.scopeId());
                case BRANCH -> actor.dataScope().canAccessBranch(scope.scopeId());
                case WAREHOUSE -> warehouseDirectory.find(scope.scopeId())
                        .map(warehouse -> actor.dataScope().canAccessWarehouse(warehouse.id(), warehouse.branchId()))
                        .orElseThrow(() -> BusinessException.invalidField("dataScopes",
                                "Kho #" + scope.scopeId() + " không tồn tại."));
            };
            if (!allowed) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "Không thể gán phạm vi " + scope.type() + " #" + scope.scopeId() + " nằm ngoài phạm vi của bạn.");
            }
        }
        return scopes.stream().map(scope -> new User.ScopeAssignment(scope.type(), scope.scopeId())).toList();
    }

    private void audit(CurrentUser actor, String action, User user, String reason, Object before, Object after) {
        auditLog.record(new AuditEntry(actor.id(), action, AuditActions.USER, user.getId(), user.getUsername(),
                reason, before, after, user.getCompanyId(), user.getDefaultBranchId()));
    }

    private static Set<Long> nullToEmpty(Set<Long> ids) {
        return ids == null ? Set.of() : ids;
    }
}
