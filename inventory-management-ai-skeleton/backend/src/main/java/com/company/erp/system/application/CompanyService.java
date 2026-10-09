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
import com.company.erp.system.application.dto.CompanyResponse;
import com.company.erp.system.application.dto.CreateCompanyRequest;
import com.company.erp.system.application.dto.UpdateCompanyRequest;
import com.company.erp.system.domain.Company;
import com.company.erp.system.domain.DataScopeType;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.CompanyRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Danh mục công ty (BR-SYS-01). Chỉ thấy công ty trong phạm vi dữ liệu; sửa cần phạm vi toàn công ty. */
@Service
public class CompanyService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final String ENDPOINT_CREATE = "POST /api/v1/companies";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotency;
    private final AuditLogService auditLog;

    public CompanyService(CompanyRepository companyRepository, UserRepository userRepository,
                          IdempotencyService idempotency, AuditLogService auditLog) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.idempotency = idempotency;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public Page<CompanyResponse> search(CurrentUser actor, String keyword, RecordStatus status, Pageable pageable) {
        return companyRepository.findAll(Specs.allOf(
                        Specs.<Company>in("id", actor.dataScope().companyIds()),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(SystemDtoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(CurrentUser actor, Long id) {
        return SystemDtoMapper.toResponse(findVisible(actor, id));
    }

    /** Người tạo được gán phạm vi toàn công ty mới để quản lý tiếp. */
    @Transactional
    public CompanyResponse create(CurrentUser actor, String idempotencyKey, CreateCompanyRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, ENDPOINT_CREATE, request);
        return idempotency.execute(command, CompanyResponse.class, () -> {
            if (companyRepository.existsByCode(request.code())) {
                throw new BusinessException(ErrorCode.DUPLICATE_CODE, "Mã công ty '" + request.code() + "' đã tồn tại.");
            }
            Company company = companyRepository.saveAndFlush(new Company(request.code(), request.name(),
                    request.taxCode(), request.address(), request.phone(), request.email()));
            User creator = userRepository.findById(actor.id()).orElseThrow(BusinessException::notFound);
            creator.addDataScope(new User.ScopeAssignment(DataScopeType.COMPANY, company.getId()));
            CompanyResponse response = SystemDtoMapper.toResponse(company);
            auditLog.record(new AuditEntry(actor.id(), AuditActions.CREATE, AuditActions.COMPANY, company.getId(),
                    null, null, null, response, company.getId(), null));
            return response;
        });
    }

    @Transactional
    public CompanyResponse update(CurrentUser actor, Long id, UpdateCompanyRequest request) {
        Company company = findManageable(actor, id);
        company.verifyVersion(request.version());
        CompanyResponse before = SystemDtoMapper.toResponse(company);
        company.update(request.name(), request.taxCode(), request.address(), request.phone(), request.email());
        CompanyResponse after = SystemDtoMapper.toResponse(companyRepository.saveAndFlush(company));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.UPDATE, AuditActions.COMPANY, id, null, null,
                before, after, id, null));
        return after;
    }

    @Transactional
    public CompanyResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        Company company = findManageable(actor, id);
        company.verifyVersion(request.version());
        CompanyResponse before = SystemDtoMapper.toResponse(company);
        company.changeStatus(request.status());
        CompanyResponse after = SystemDtoMapper.toResponse(companyRepository.saveAndFlush(company));
        auditLog.record(new AuditEntry(actor.id(), AuditActions.CHANGE_STATUS, AuditActions.COMPANY, id, null,
                request.reason(), before, after, id, null));
        return after;
    }

    private Company findVisible(CurrentUser actor, Long id) {
        if (!actor.dataScope().canAccessCompany(id)) {
            throw BusinessException.notFound();
        }
        return companyRepository.findById(id).orElseThrow(BusinessException::notFound);
    }

    private Company findManageable(CurrentUser actor, Long id) {
        Company company = findVisible(actor, id);
        if (!actor.dataScope().hasFullCompanyAccess(id)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cần phạm vi dữ liệu toàn công ty để sửa công ty.");
        }
        return company;
    }
}
