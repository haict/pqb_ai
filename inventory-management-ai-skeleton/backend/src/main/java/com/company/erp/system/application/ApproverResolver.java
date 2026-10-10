package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.domain.ApprovalDelegation;
import com.company.erp.system.domain.ApprovalRequest;
import com.company.erp.system.domain.ApprovalStepDefinition;
import com.company.erp.system.domain.ApproverType;
import com.company.erp.system.domain.Role;
import com.company.erp.system.infrastructure.persistence.ApprovalDelegationRepository;
import com.company.erp.system.infrastructure.persistence.ApprovalRequestRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Xác định ai được duyệt bước hiện tại của yêu cầu duyệt (BR-SYS-11):
 * <ul>
 *   <li>Bước theo vai trò: người dùng đang hoạt động có vai trò đó và có phạm vi dữ liệu trên chi nhánh của chứng từ.</li>
 *   <li>Bước theo người: đúng người được chỉ định.</li>
 *   <li>Người được ủy quyền (đang hiệu lực, đúng công ty và loại chứng từ) duyệt thay người ủy quyền.</li>
 * </ul>
 */
@Component
class ApproverResolver {

    /** Người duyệt bước: chính mình ({@code onBehalfOf} = {@code null}) hoặc duyệt thay người ủy quyền. */
    record Authorization(Long onBehalfOf) {
    }

    private final UserRepository userRepository;
    private final CurrentUserLoader currentUserLoader;
    private final ApprovalDelegationRepository delegationRepository;
    private final ApprovalRequestRepository requestRepository;
    private final Clock clock;

    ApproverResolver(UserRepository userRepository, CurrentUserLoader currentUserLoader,
                     ApprovalDelegationRepository delegationRepository, ApprovalRequestRepository requestRepository,
                     Clock clock) {
        this.userRepository = userRepository;
        this.currentUserLoader = currentUserLoader;
        this.delegationRepository = delegationRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    Authorization authorize(CurrentUser actor, ApprovalRequest request) {
        ApprovalStepDefinition step = request.currentStepDefinition();
        if (isEligible(actor, step, request)) {
            return new Authorization(null);
        }
        for (ApprovalDelegation delegation : effectiveDelegations(actor.id(), request)) {
            Optional<CurrentUser> delegator = currentUserLoader.findActive(delegation.getDelegatorUserId());
            if (delegator.isPresent() && isEligible(delegator.get(), step, request)) {
                return new Authorization(delegation.getDelegatorUserId());
            }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không phải người duyệt bước hiện tại của chứng từ này.");
    }

    /** Người nhận thông báo "chờ duyệt": người duyệt bước hiện tại và người họ đã ủy quyền. */
    Set<Long> currentApproverIds(ApprovalRequest request) {
        ApprovalStepDefinition step = request.currentStepDefinition();
        Set<Long> approvers = new LinkedHashSet<>();
        if (step.getApproverType() == ApproverType.USER) {
            currentUserLoader.findActive(step.getUserId()).ifPresent(user -> approvers.add(user.id()));
        } else {
            for (Long userId : userRepository.findActiveIdsByRoleId(step.getRoleId())) {
                currentUserLoader.findActive(userId)
                        .filter(user -> user.dataScope().canAccessBranch(request.getBranchId()))
                        .ifPresent(user -> approvers.add(user.id()));
            }
        }
        if (!approvers.isEmpty()) {
            Instant now = Instant.now(clock);
            delegationRepository.findEffectiveByDelegators(approvers, now).stream()
                    .filter(delegation -> delegation.getCompanyId().equals(request.getCompanyId()))
                    .filter(delegation -> delegation.isEffective(now, request.getDocumentType()))
                    .forEach(delegation -> approvers.add(delegation.getDelegateUserId()));
        }
        return approvers;
    }

    /**
     * Yêu cầu đang chờ mà người dùng duyệt được, theo vai trò/đích danh của chính mình hoặc của người ủy quyền.
     * Giá trị: người được duyệt thay ({@code null} khi duyệt bằng quyền của chính mình).
     */
    Map<Long, Optional<Long>> pendingFor(CurrentUser actor) {
        Map<Long, Optional<Long>> result = new HashMap<>();
        pendingIdsOf(actor).forEach(id -> result.put(id, Optional.empty()));
        Instant now = Instant.now(clock);
        for (ApprovalDelegation delegation : delegationRepository.findEffectiveForDelegate(actor.id(), now)) {
            Optional<CurrentUser> delegator = currentUserLoader.findActive(delegation.getDelegatorUserId());
            if (delegator.isEmpty()) {
                continue;
            }
            List<Long> ids = pendingIdsOf(delegator.get());
            requestRepository.findAllById(ids).stream()
                    .filter(request -> request.getCompanyId().equals(delegation.getCompanyId()))
                    .filter(request -> delegation.isEffective(now, request.getDocumentType()))
                    .forEach(request -> result.putIfAbsent(request.getId(),
                            Optional.of(delegation.getDelegatorUserId())));
        }
        return result;
    }

    private List<Long> pendingIdsOf(CurrentUser user) {
        Set<Long> ids = new LinkedHashSet<>(requestRepository.findPendingIdsForUser(user.id()));
        Set<Long> roleIds = activeRoleIds(user.id());
        if (!roleIds.isEmpty() && !user.dataScope().branchIds().isEmpty()) {
            ids.addAll(requestRepository.findPendingIdsForRoles(roleIds, user.dataScope().branchIds()));
        }
        return List.copyOf(ids);
    }

    private boolean isEligible(CurrentUser user, ApprovalStepDefinition step, ApprovalRequest request) {
        if (step.getApproverType() == ApproverType.USER) {
            return step.getUserId().equals(user.id());
        }
        return user.dataScope().canAccessBranch(request.getBranchId())
                && activeRoleIds(user.id()).contains(step.getRoleId());
    }

    private List<ApprovalDelegation> effectiveDelegations(Long delegateUserId, ApprovalRequest request) {
        Instant now = Instant.now(clock);
        return delegationRepository.findEffectiveForDelegate(delegateUserId, now).stream()
                .filter(delegation -> delegation.getCompanyId().equals(request.getCompanyId()))
                .filter(delegation -> delegation.isEffective(now, request.getDocumentType()))
                .toList();
    }

    private Set<Long> activeRoleIds(Long userId) {
        return userRepository.findWithAuthoritiesById(userId)
                .map(user -> user.getRoles().stream()
                        .filter(role -> role.getStatus() == RecordStatus.ACTIVE)
                        .map(Role::getId)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }
}
