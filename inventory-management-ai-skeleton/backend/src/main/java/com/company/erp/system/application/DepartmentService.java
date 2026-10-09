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
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateDepartmentRequest;
import com.company.erp.system.application.dto.DepartmentResponse;
import com.company.erp.system.application.dto.UpdateDepartmentRequest;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.Department;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.DepartmentRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Danh mục phòng ban theo chi nhánh (BR-SYS-01); phạm vi dữ liệu theo chi nhánh. */
@Service
public class DepartmentService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final String ENDPOINT_CREATE = "POST /api/v1/departments";

    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;

    public DepartmentService(DepartmentRepository departmentRepository, BranchRepository branchRepository,
                             IdempotencyService idempotency, AuditLogService auditLog) {
        this.departmentRepository = departmentRepository;
        this.branchRepository = branchRepository;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public Page<DepartmentResponse> search(CurrentUser actor, Long branchId, String keyword, RecordStatus status,
                                           Pageable pageable) {
        return departmentRepository.findAll(Specs.allOf(
                        Specs.<Department>in("branchId", actor.dataScope().branchIds()),
                        Specs.equalsIfPresent("branchId", branchId),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(SystemDtoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(CurrentUser actor, Long id) {
        return SystemDtoMapper.toResponse(findVisible(actor, id));
    }

    @Transactional
    public DepartmentResponse create(CurrentUser actor, String idempotencyKey, CreateDepartmentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_CREATE, request);
        return idempotency.execute(command, DepartmentResponse.class, () -> {
            Branch branch = requireActiveBranch(actor, request.branchId());
            if (departmentRepository.existsByBranchIdAndCode(request.branchId(), request.code())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE,
                        "Mã phòng ban '" + request.code() + "' đã tồn tại trong chi nhánh.");
            }
            Department department = departmentRepository.saveAndFlush(
                    new Department(request.branchId(), request.code(), request.name()));
            DepartmentResponse response = SystemDtoMapper.toResponse(department);
            auditLog.record(new AuditEntry(actor.id(), AuditActions.CREATE, AuditActions.DEPARTMENT,
                    department.getId(), null, null, null, response, branch.getCompanyId(), branch.getId()));
            return response;
        });
    }

    @Transactional
    public DepartmentResponse update(CurrentUser actor, Long id, UpdateDepartmentRequest request) {
        Department department = findVisible(actor, id);
        department.verifyVersion(request.version());
        DepartmentResponse before = SystemDtoMapper.toResponse(department);
        department.rename(request.name());
        DepartmentResponse after = SystemDtoMapper.toResponse(departmentRepository.saveAndFlush(department));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.UPDATE, AuditActions.DEPARTMENT, id, null, null,
                before, after, null, department.getBranchId()));
        return after;
    }

    @Transactional
    public DepartmentResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        Department department = findVisible(actor, id);
        department.verifyVersion(request.version());
        DepartmentResponse before = SystemDtoMapper.toResponse(department);
        department.changeStatus(request.status());
        DepartmentResponse after = SystemDtoMapper.toResponse(departmentRepository.saveAndFlush(department));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.CHANGE_STATUS, AuditActions.DEPARTMENT, id, null,
                request.reason(), before, after, null, department.getBranchId()));
        return after;
    }

    private Department findVisible(CurrentUser actor, Long id) {
        Department department = departmentRepository.findById(id).orElseThrow(BusinessException::notFound);
        if (!actor.dataScope().canAccessBranch(department.getBranchId())) {
            throw BusinessException.notFound();
        }
        return department;
    }

    private Branch requireActiveBranch(CurrentUser actor, Long branchId) {
        if (!actor.dataScope().canAccessBranch(branchId)) {
            throw BusinessException.notFound();
        }
        Branch branch = branchRepository.findById(branchId).orElseThrow(BusinessException::notFound);
        if (branch.getStatus() != RecordStatus.ACTIVE) {
            throw BusinessException.invalidField("branchId", "Chi nhánh đã ngừng hoạt động.");
        }
        return branch;
    }
}
