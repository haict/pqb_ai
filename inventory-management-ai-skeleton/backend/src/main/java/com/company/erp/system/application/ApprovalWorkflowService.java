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
import com.company.erp.system.application.dto.ApprovalStepRequest;
import com.company.erp.system.application.dto.ApprovalWorkflowResponse;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateApprovalWorkflowRequest;
import com.company.erp.system.application.dto.UpdateApprovalWorkflowRequest;
import com.company.erp.system.domain.ApprovalStepDefinition;
import com.company.erp.system.domain.ApprovalWorkflow;
import com.company.erp.system.domain.ApproverType;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.Role;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.ApprovalWorkflowRepository;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.DocumentTypeSettingRepository;
import com.company.erp.system.infrastructure.persistence.RoleRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cấu hình luồng duyệt (BR-SYS-11). Cần phạm vi toàn công ty để tạo, sửa. */
@Service
public class ApprovalWorkflowService {

    private static final Set<String> SORTABLE = Set.of("documentType", "name", "priority", "updatedAt");
    private static final String ENDPOINT_CREATE = "POST /api/v1/approval-workflows";

    private final ApprovalWorkflowRepository workflowRepository;
    private final DocumentTypeSettingRepository typeRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ApprovalResponseMapper mapper;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;

    public ApprovalWorkflowService(ApprovalWorkflowRepository workflowRepository,
                                   DocumentTypeSettingRepository typeRepository, BranchRepository branchRepository,
                                   RoleRepository roleRepository, UserRepository userRepository,
                                   ApprovalResponseMapper mapper, IdempotencyService idempotency,
                                   AuditLogService auditLog) {
        this.workflowRepository = workflowRepository;
        this.typeRepository = typeRepository;
        this.branchRepository = branchRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalWorkflowResponse> search(CurrentUser actor, Long companyId, String documentType,
                                                 RecordStatus status, Pageable pageable) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        return workflowRepository.findAll(Specs.allOf(
                        Specs.<ApprovalWorkflow>equalsIfPresent("companyId", companyId),
                        Specs.equalsIfPresent("documentType", documentType),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE,
                        Sort.by(Sort.Order.asc("documentType"), Sort.Order.desc("priority"))))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ApprovalWorkflowResponse get(CurrentUser actor, Long id) {
        return mapper.toResponse(findVisible(actor, id));
    }

    @Transactional
    public ApprovalWorkflowResponse create(CurrentUser actor, String idempotencyKey,
                                           CreateApprovalWorkflowRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_CREATE, request);
        return idempotency.execute(command, ApprovalWorkflowResponse.class, () -> {
            requireFullCompanyAccess(actor, request.companyId());
            if (!typeRepository.existsById(request.documentType())) {
                throw BusinessException.invalidField("documentType", "Loại chứng từ không tồn tại.");
            }
            ApprovalWorkflow workflow = new ApprovalWorkflow(request.companyId(), request.documentType());
            define(workflow, request.name(), request.branchId(), request.minAmount(), request.minDiscountPercent(),
                    request.priority(), request.steps());
            ApprovalWorkflowResponse response = mapper.toResponse(workflowRepository.saveAndFlush(workflow));
            auditLog.record(new AuditEntry(actor.id(), AuditActions.CREATE, AuditActions.APPROVAL_WORKFLOW,
                    workflow.getId(), null, null, null, response, workflow.getCompanyId(), null));
            return response;
        });
    }

    @Transactional
    public ApprovalWorkflowResponse update(CurrentUser actor, Long id, UpdateApprovalWorkflowRequest request) {
        ApprovalWorkflow workflow = findVisible(actor, id);
        requireFullCompanyAccess(actor, workflow.getCompanyId());
        workflow.verifyVersion(request.version());
        ApprovalWorkflowResponse before = mapper.toResponse(workflow);
        define(workflow, request.name(), request.branchId(), request.minAmount(), request.minDiscountPercent(),
                request.priority(), request.steps());
        ApprovalWorkflowResponse after = mapper.toResponse(workflowRepository.saveAndFlush(workflow));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.UPDATE, AuditActions.APPROVAL_WORKFLOW, id, null,
                null, before, after, workflow.getCompanyId(), null));
        return after;
    }

    @Transactional
    public ApprovalWorkflowResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        ApprovalWorkflow workflow = findVisible(actor, id);
        requireFullCompanyAccess(actor, workflow.getCompanyId());
        workflow.verifyVersion(request.version());
        ApprovalWorkflowResponse before = mapper.toResponse(workflow);
        workflow.changeStatus(request.status());
        ApprovalWorkflowResponse after = mapper.toResponse(workflowRepository.saveAndFlush(workflow));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.CHANGE_STATUS, AuditActions.APPROVAL_WORKFLOW, id,
                null, request.reason(), before, after, workflow.getCompanyId(), null));
        return after;
    }

    private void define(ApprovalWorkflow workflow, String name, Long branchId, BigDecimal minAmount,
                        BigDecimal minDiscountPercent, int priority, List<ApprovalStepRequest> stepRequests) {
        if (branchId != null) {
            Branch branch = branchRepository.findById(branchId)
                    .filter(found -> found.getCompanyId().equals(workflow.getCompanyId()))
                    .orElseThrow(() -> BusinessException.invalidField("branchId", "Chi nhánh không thuộc công ty."));
            if (branch.getStatus() != RecordStatus.ACTIVE) {
                throw BusinessException.invalidField("branchId", "Chi nhánh đã ngừng hoạt động.");
            }
        }
        List<ApprovalStepDefinition> steps = new ArrayList<>();
        for (int i = 0; i < stepRequests.size(); i++) {
            steps.add(toStep(i + 1, stepRequests.get(i), workflow.getCompanyId()));
        }
        workflow.define(name.trim(), branchId, minAmount, minDiscountPercent, priority, steps);
    }

    private ApprovalStepDefinition toStep(int stepNo, ApprovalStepRequest request, Long companyId) {
        String field = "steps[" + (stepNo - 1) + "]";
        if (request.approverType() == ApproverType.ROLE) {
            Role role = request.roleId() == null ? null : roleRepository.findById(request.roleId()).orElse(null);
            if (role == null || role.getStatus() != RecordStatus.ACTIVE || request.userId() != null) {
                throw BusinessException.invalidField(field, "Chọn một vai trò đang hoạt động.");
            }
            return new ApprovalStepDefinition(stepNo, ApproverType.ROLE, role.getId(), null);
        }
        User user = request.userId() == null ? null : userRepository.findById(request.userId()).orElse(null);
        if (user == null || !user.isActive() || !user.getCompanyId().equals(companyId) || request.roleId() != null) {
            throw BusinessException.invalidField(field, "Chọn một người dùng đang hoạt động của công ty.");
        }
        return new ApprovalStepDefinition(stepNo, ApproverType.USER, null, user.getId());
    }

    private ApprovalWorkflow findVisible(CurrentUser actor, Long id) {
        ApprovalWorkflow workflow = workflowRepository.findById(id).orElseThrow(BusinessException::notFound);
        if (!actor.dataScope().canAccessCompany(workflow.getCompanyId())) {
            throw BusinessException.notFound();
        }
        return workflow;
    }

    private static void requireFullCompanyAccess(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        if (!actor.dataScope().hasFullCompanyAccess(companyId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cần phạm vi dữ liệu toàn công ty để cấu hình luồng duyệt.");
        }
    }
}
