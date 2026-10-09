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
import com.company.erp.system.application.dto.BranchResponse;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import com.company.erp.system.application.dto.CreateBranchRequest;
import com.company.erp.system.application.dto.UpdateBranchRequest;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.Company;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh mục chi nhánh (BR-SYS-01). Chi nhánh ngoài phạm vi trả 404 kể cả khi gọi API trực tiếp theo ID
 * (BR-SYS-06, AT-10). Tạo chi nhánh cần phạm vi toàn công ty.
 */
@Service
public class BranchService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final String ENDPOINT_CREATE = "POST /api/v1/branches";

    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;

    public BranchService(BranchRepository branchRepository, CompanyRepository companyRepository,
                         IdempotencyService idempotency, AuditLogService auditLog) {
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public Page<BranchResponse> search(CurrentUser actor, Long companyId, String keyword, RecordStatus status,
                                       Pageable pageable) {
        return branchRepository.findAll(Specs.allOf(
                        Specs.<Branch>in("id", actor.dataScope().branchIds()),
                        Specs.equalsIfPresent("companyId", companyId),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(SystemDtoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BranchResponse get(CurrentUser actor, Long id) {
        return SystemDtoMapper.toResponse(findVisible(actor, id));
    }

    @Transactional
    public BranchResponse create(CurrentUser actor, String idempotencyKey, CreateBranchRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_CREATE, request);
        return idempotency.execute(command, BranchResponse.class, () -> {
            requireActiveCompanyWithFullAccess(actor, request.companyId());
            if (branchRepository.existsByCompanyIdAndCode(request.companyId(), request.code())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE,
                        "Mã chi nhánh '" + request.code() + "' đã tồn tại trong công ty.");
            }
            Branch branch = branchRepository.saveAndFlush(new Branch(request.companyId(), request.code(),
                    request.name(), request.address(), request.phone()));
            BranchResponse response = SystemDtoMapper.toResponse(branch);
            auditLog.record(new AuditEntry(actor.id(), AuditActions.CREATE, AuditActions.BRANCH, branch.getId(),
                    null, null, null, response, branch.getCompanyId(), branch.getId()));
            return response;
        });
    }

    @Transactional
    public BranchResponse update(CurrentUser actor, Long id, UpdateBranchRequest request) {
        Branch branch = findVisible(actor, id);
        branch.verifyVersion(request.version());
        BranchResponse before = SystemDtoMapper.toResponse(branch);
        branch.update(request.name(), request.address(), request.phone());
        BranchResponse after = SystemDtoMapper.toResponse(branchRepository.saveAndFlush(branch));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.UPDATE, AuditActions.BRANCH, id, null, null,
                before, after, branch.getCompanyId(), id));
        return after;
    }

    @Transactional
    public BranchResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        Branch branch = findVisible(actor, id);
        branch.verifyVersion(request.version());
        BranchResponse before = SystemDtoMapper.toResponse(branch);
        branch.changeStatus(request.status());
        BranchResponse after = SystemDtoMapper.toResponse(branchRepository.saveAndFlush(branch));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.CHANGE_STATUS, AuditActions.BRANCH, id, null,
                request.reason(), before, after, branch.getCompanyId(), id));
        return after;
    }

    private Branch findVisible(CurrentUser actor, Long id) {
        if (!actor.dataScope().canAccessBranch(id)) {
            throw BusinessException.notFound();
        }
        return branchRepository.findById(id).orElseThrow(BusinessException::notFound);
    }

    private void requireActiveCompanyWithFullAccess(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        if (!actor.dataScope().hasFullCompanyAccess(companyId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cần phạm vi dữ liệu toàn công ty để tạo chi nhánh.");
        }
        Company company = companyRepository.findById(companyId).orElseThrow(BusinessException::notFound);
        if (company.getStatus() != RecordStatus.ACTIVE) {
            throw BusinessException.invalidField("companyId", "Công ty đã ngừng hoạt động.");
        }
    }
}
