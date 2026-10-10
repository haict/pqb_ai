package com.company.erp.system.application;

import com.company.erp.system.application.dto.ApprovalRequestResponse;
import com.company.erp.system.application.dto.ApprovalStepResponse;
import com.company.erp.system.application.dto.ApprovalWorkflowResponse;
import com.company.erp.system.domain.ApprovalAction;
import com.company.erp.system.domain.ApprovalRequest;
import com.company.erp.system.domain.ApprovalRequestStatus;
import com.company.erp.system.domain.ApprovalStepDefinition;
import com.company.erp.system.domain.ApprovalWorkflow;
import com.company.erp.system.domain.Role;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.ApprovalActionRepository;
import com.company.erp.system.infrastructure.persistence.RoleRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Chuyển yêu cầu duyệt, luồng duyệt sang DTO kèm tên người, tên vai trò (nạp theo lô, tránh N+1). */
@Component
class ApprovalResponseMapper {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApprovalActionRepository actionRepository;

    ApprovalResponseMapper(UserRepository userRepository, RoleRepository roleRepository,
                           ApprovalActionRepository actionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.actionRepository = actionRepository;
    }

    List<ApprovalRequestResponse> toResponses(List<ApprovalRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ApprovalAction>> actions = actionRepository
                .findByRequestIdInOrderByActedAtAscIdAsc(requests.stream().map(ApprovalRequest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ApprovalAction::getRequestId));
        List<ApprovalStepDefinition> steps = requests.stream().flatMap(request -> request.getSteps().stream()).toList();
        Map<Long, String> users = userNames(Stream.concat(
                Stream.concat(requests.stream().map(ApprovalRequest::getRequestedBy),
                        steps.stream().map(ApprovalStepDefinition::getUserId)),
                actions.values().stream().flatMap(List::stream)
                        .flatMap(action -> Stream.of(action.getActorUserId(), action.getOnBehalfOfUserId())))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> roles = roleNames(steps);
        return requests.stream()
                .map(request -> toResponse(request, actions.getOrDefault(request.getId(), List.of()), users, roles))
                .toList();
    }

    ApprovalWorkflowResponse toResponse(ApprovalWorkflow workflow) {
        List<ApprovalStepDefinition> steps = workflow.getSteps();
        Map<Long, String> users = userNames(steps.stream().map(ApprovalStepDefinition::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return new ApprovalWorkflowResponse(workflow.getId(), workflow.getCompanyId(), workflow.getDocumentType(),
                workflow.getName(), workflow.getBranchId(), workflow.getMinAmount(), workflow.getMinDiscountPercent(),
                workflow.getPriority(), workflow.getStatus(), toStepResponses(steps, users, roleNames(steps)),
                workflow.getVersion(), workflow.getUpdatedAt());
    }

    private ApprovalRequestResponse toResponse(ApprovalRequest request, List<ApprovalAction> actions,
                                               Map<Long, String> users, Map<Long, String> roles) {
        int position = request.getStatus() == ApprovalRequestStatus.PENDING
                ? request.currentStepPosition()
                : request.getTotalSteps();
        return new ApprovalRequestResponse(request.getId(), request.getCompanyId(), request.getBranchId(),
                request.getDocumentType(), request.getDocumentId(), request.getDocumentNo(), request.getAmount(),
                request.getStatus(), position, request.getTotalSteps(), request.getRequestedBy(),
                users.get(request.getRequestedBy()), request.getRequestedAt(), request.getCompletedAt(),
                toStepResponses(request.getSteps(), users, roles),
                actions.stream()
                        .map(action -> new ApprovalRequestResponse.Action(action.getAction(), action.getStepNo(),
                                users.get(action.getActorUserId()), users.get(action.getOnBehalfOfUserId()),
                                action.getComment(), action.getActedAt()))
                        .toList());
    }

    private static List<ApprovalStepResponse> toStepResponses(List<ApprovalStepDefinition> steps,
                                                              Map<Long, String> users, Map<Long, String> roles) {
        return steps.stream()
                .map(step -> new ApprovalStepResponse(step.getStepNo(), step.getApproverType(), step.getRoleId(),
                        roles.get(step.getRoleId()), step.getUserId(), users.get(step.getUserId())))
                .toList();
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private Map<Long, String> roleNames(List<ApprovalStepDefinition> steps) {
        List<Long> ids = steps.stream().map(ApprovalStepDefinition::getRoleId).filter(Objects::nonNull).distinct()
                .toList();
        return roleRepository.findAllById(ids).stream().collect(Collectors.toMap(Role::getId, Role::getName));
    }
}
