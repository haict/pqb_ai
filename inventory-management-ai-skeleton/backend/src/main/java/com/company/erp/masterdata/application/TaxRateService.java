package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.TaxRateRequest;
import com.company.erp.masterdata.application.dto.TaxRateResponse;
import com.company.erp.masterdata.application.dto.UpdateTaxRateRequest;
import com.company.erp.masterdata.domain.TaxRate;
import com.company.erp.masterdata.infrastructure.persistence.TaxRateRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thuế suất GTGT có thời hạn (BR-MD-42, BR-GEN-42). */
@Service
public class TaxRateService {

    private static final Set<String> SORTABLE = Set.of("code", "rate", "validFrom");

    private final TaxRateRepository repository;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public TaxRateService(TaxRateRepository repository, MasterDataGuard guard, IdempotencyService idempotency) {
        this.repository = repository;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<TaxRateResponse> search(CurrentUser actor, Long companyId, RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        return repository.findAll(Specs.allOf(
                        Specs.<TaxRate>equalsIfPresent("companyId", companyId),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(TaxRateService::toResponse);
    }

    @Transactional
    public TaxRateResponse create(CurrentUser actor, String idempotencyKey, TaxRateRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/tax-rates", request);
        return idempotency.execute(command, TaxRateResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            guard.requireNewCode(repository.existsByCompanyIdAndCode(request.companyId(), request.code()),
                    "Thuế suất", request.code());
            TaxRate taxRate = repository.saveAndFlush(new TaxRate(request.companyId(), request.code(),
                    request.name().trim(), request.rate(), request.validFrom(), request.validTo()));
            TaxRateResponse response = toResponse(taxRate);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.TAX_RATE, taxRate.getId(), null, null, response,
                    taxRate.getCompanyId());
            return response;
        });
    }

    @Transactional
    public TaxRateResponse update(CurrentUser actor, Long id, UpdateTaxRateRequest request) {
        TaxRate taxRate = guard.visible(actor, repository.findById(id));
        taxRate.verifyVersion(request.version());
        TaxRateResponse before = toResponse(taxRate);
        taxRate.update(request.name().trim(), request.rate(), request.validFrom(), request.validTo());
        TaxRateResponse after = toResponse(repository.saveAndFlush(taxRate));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.TAX_RATE, id, null, before, after,
                taxRate.getCompanyId());
        return after;
    }

    @Transactional
    public TaxRateResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        TaxRate taxRate = guard.visible(actor, repository.findById(id));
        taxRate.verifyVersion(request.version());
        TaxRateResponse before = toResponse(taxRate);
        taxRate.changeStatus(request.status());
        TaxRateResponse after = toResponse(repository.saveAndFlush(taxRate));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.TAX_RATE, id, request.reason(), before,
                after, taxRate.getCompanyId());
        return after;
    }

    private static TaxRateResponse toResponse(TaxRate taxRate) {
        return new TaxRateResponse(taxRate.getId(), taxRate.getCompanyId(), taxRate.getCode(), taxRate.getName(),
                taxRate.getRate(), taxRate.getValidFrom(), taxRate.getValidTo(), taxRate.getStatus(),
                taxRate.getVersion(), taxRate.getUpdatedAt());
    }
}
