package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.CountryRequest;
import com.company.erp.masterdata.application.dto.CountryResponse;
import com.company.erp.masterdata.application.dto.UpdateCountryRequest;
import com.company.erp.masterdata.domain.Country;
import com.company.erp.masterdata.infrastructure.persistence.CountryRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quốc gia / xuất xứ (BR-MD-03), dùng chung mọi công ty. */
@Service
public class CountryService {

    private static final Set<String> SORTABLE = Set.of("code", "name");

    private final CountryRepository repository;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public CountryService(CountryRepository repository, MasterDataGuard guard, IdempotencyService idempotency) {
        this.repository = repository;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<CountryResponse> search(String keyword, RecordStatus status, Pageable pageable) {
        return repository.findAll(Specs.allOf(
                        Specs.<Country>keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("name")))
                .map(CountryService::toResponse);
    }

    @Transactional
    public CountryResponse create(CurrentUser actor, String idempotencyKey, CountryRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/countries", request);
        return idempotency.execute(command, CountryResponse.class, () -> {
            guard.requireNewCode(repository.existsById(request.code()), "Quốc gia", request.code());
            Country country = repository.saveAndFlush(new Country(request.code(), request.name().trim()));
            CountryResponse response = toResponse(country);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.COUNTRY, null, null, null, response, null);
            return response;
        });
    }

    @Transactional
    public CountryResponse update(CurrentUser actor, String code, UpdateCountryRequest request) {
        Country country = repository.findById(code).orElseThrow(BusinessException::notFound);
        country.verifyVersion(request.version());
        CountryResponse before = toResponse(country);
        country.rename(request.name().trim());
        CountryResponse after = toResponse(repository.saveAndFlush(country));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.COUNTRY, null, null, before, after, null);
        return after;
    }

    @Transactional
    public CountryResponse changeStatus(CurrentUser actor, String code, ChangeStatusRequest request) {
        Country country = repository.findById(code).orElseThrow(BusinessException::notFound);
        country.verifyVersion(request.version());
        CountryResponse before = toResponse(country);
        country.changeStatus(request.status());
        CountryResponse after = toResponse(repository.saveAndFlush(country));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.COUNTRY, null, request.reason(), before,
                after, null);
        return after;
    }

    private static CountryResponse toResponse(Country country) {
        return new CountryResponse(country.getCode(), country.getName(), country.getStatus(), country.getVersion());
    }
}
