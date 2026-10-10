package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.CatalogItemRequest;
import com.company.erp.masterdata.application.dto.CatalogItemResponse;
import com.company.erp.masterdata.application.dto.UpdateCatalogItemRequest;
import com.company.erp.masterdata.domain.Brand;
import com.company.erp.masterdata.domain.CodeNameEntity;
import com.company.erp.masterdata.domain.Manufacturer;
import com.company.erp.masterdata.domain.PartnerGroup;
import com.company.erp.masterdata.domain.Unit;
import com.company.erp.masterdata.infrastructure.persistence.BrandRepository;
import com.company.erp.masterdata.infrastructure.persistence.CodeNameRepository;
import com.company.erp.masterdata.infrastructure.persistence.CountryRepository;
import com.company.erp.masterdata.infrastructure.persistence.ManufacturerRepository;
import com.company.erp.masterdata.infrastructure.persistence.PartnerGroupRepository;
import com.company.erp.masterdata.infrastructure.persistence.UnitRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh mục dạng mã - tên theo công ty: đơn vị tính, nhãn hiệu, nhà sản xuất, nhóm đối tác (BR-MD-03, BR-MD-20,
 * BR-MD-31). Mã duy nhất trong công ty, không đổi; ngừng hoạt động thay vì xóa (BR-MD-12).
 */
@Service
public class SimpleCatalogService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "updatedAt");

    /** Cách tạo / sửa / đọc thuộc tính riêng của từng danh mục. */
    private record Handler<E extends CodeNameEntity>(
            CodeNameRepository<E> repository,
            Creator<E> creator,
            Updater<E> updater,
            Function<E, String> countryCode,
            boolean usesCountry) {
    }

    @FunctionalInterface
    private interface Creator<E> {
        E create(Long companyId, String code, String name, String countryCode);
    }

    @FunctionalInterface
    private interface Updater<E> {
        void update(E entity, String name, String countryCode);
    }

    private final Map<SimpleCatalog, Handler<?>> handlers = new EnumMap<>(SimpleCatalog.class);
    private final CountryRepository countryRepository;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public SimpleCatalogService(UnitRepository unitRepository, BrandRepository brandRepository,
                                ManufacturerRepository manufacturerRepository,
                                PartnerGroupRepository partnerGroupRepository, CountryRepository countryRepository,
                                MasterDataGuard guard, IdempotencyService idempotency) {
        this.countryRepository = countryRepository;
        this.guard = guard;
        this.idempotency = idempotency;
        handlers.put(SimpleCatalog.UNITS, new Handler<>(unitRepository,
                (companyId, code, name, country) -> new Unit(companyId, code, name),
                (entity, name, country) -> entity.rename(name), entity -> null, false));
        handlers.put(SimpleCatalog.BRANDS, new Handler<>(brandRepository,
                (companyId, code, name, country) -> new Brand(companyId, code, name),
                (entity, name, country) -> entity.rename(name), entity -> null, false));
        handlers.put(SimpleCatalog.MANUFACTURERS, new Handler<>(manufacturerRepository,
                Manufacturer::new,
                (entity, name, country) -> {
                    entity.rename(name);
                    entity.changeCountry(country);
                },
                Manufacturer::getCountryCode, true));
        handlers.put(SimpleCatalog.PARTNER_GROUPS, new Handler<>(partnerGroupRepository,
                (companyId, code, name, country) -> new PartnerGroup(companyId, code, name),
                (entity, name, country) -> entity.rename(name), entity -> null, false));
    }

    @Transactional(readOnly = true)
    public Page<CatalogItemResponse> search(CurrentUser actor, SimpleCatalog catalog, Long companyId, String keyword,
                                            RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        return search(handler(catalog), companyId, keyword, status, pageable);
    }

    @Transactional
    public CatalogItemResponse create(CurrentUser actor, SimpleCatalog catalog, String idempotencyKey,
                                      CatalogItemRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/" + catalog.path(), request);
        return idempotency.execute(command, CatalogItemResponse.class,
                () -> create(actor, catalog, handler(catalog), request));
    }

    @Transactional
    public CatalogItemResponse update(CurrentUser actor, SimpleCatalog catalog, Long id,
                                      UpdateCatalogItemRequest request) {
        return update(actor, catalog, handler(catalog), id, request);
    }

    @Transactional
    public CatalogItemResponse changeStatus(CurrentUser actor, SimpleCatalog catalog, Long id,
                                            ChangeStatusRequest request) {
        return changeStatus(actor, catalog, handler(catalog), id, request);
    }

    private <E extends CodeNameEntity> Page<CatalogItemResponse> search(Handler<E> handler, Long companyId,
                                                                        String keyword, RecordStatus status,
                                                                        Pageable pageable) {
        return handler.repository().findAll(Specs.allOf(
                        Specs.<E>equalsIfPresent("companyId", companyId),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(entity -> toResponse(handler, entity));
    }

    private <E extends CodeNameEntity> CatalogItemResponse create(CurrentUser actor, SimpleCatalog catalog,
                                                                  Handler<E> handler, CatalogItemRequest request) {
        guard.requireCompany(actor, request.companyId());
        guard.requireNewCode(handler.repository().existsByCompanyIdAndCode(request.companyId(), request.code()),
                catalog.label(), request.code());
        requireCountry(handler, request.countryCode());
        E entity = handler.repository().saveAndFlush(handler.creator().create(request.companyId(), request.code(),
                request.name().trim(), request.countryCode()));
        CatalogItemResponse response = toResponse(handler, entity);
        guard.audit(actor, MasterDataAudit.CREATE, catalog.auditType(), entity.getId(), null, null, response,
                entity.getCompanyId());
        return response;
    }

    private <E extends CodeNameEntity> CatalogItemResponse update(CurrentUser actor, SimpleCatalog catalog,
                                                                  Handler<E> handler, Long id,
                                                                  UpdateCatalogItemRequest request) {
        E entity = guard.visible(actor, handler.repository().findById(id));
        entity.verifyVersion(request.version());
        requireCountry(handler, request.countryCode());
        CatalogItemResponse before = toResponse(handler, entity);
        handler.updater().update(entity, request.name().trim(), request.countryCode());
        CatalogItemResponse after = toResponse(handler, handler.repository().saveAndFlush(entity));
        guard.audit(actor, MasterDataAudit.UPDATE, catalog.auditType(), id, null, before, after,
                entity.getCompanyId());
        return after;
    }

    private <E extends CodeNameEntity> CatalogItemResponse changeStatus(CurrentUser actor, SimpleCatalog catalog,
                                                                        Handler<E> handler, Long id,
                                                                        ChangeStatusRequest request) {
        E entity = guard.visible(actor, handler.repository().findById(id));
        entity.verifyVersion(request.version());
        CatalogItemResponse before = toResponse(handler, entity);
        entity.changeStatus(request.status());
        CatalogItemResponse after = toResponse(handler, handler.repository().saveAndFlush(entity));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, catalog.auditType(), id, request.reason(), before, after,
                entity.getCompanyId());
        return after;
    }

    private void requireCountry(Handler<?> handler, String countryCode) {
        if (countryCode == null) {
            return;
        }
        if (!handler.usesCountry()) {
            throw BusinessException.invalidField("countryCode", "Danh mục này không có quốc gia.");
        }
        boolean valid = countryRepository.findById(countryCode)
                .filter(country -> country.getStatus() == RecordStatus.ACTIVE)
                .isPresent();
        if (!valid) {
            throw BusinessException.invalidField("countryCode", "Quốc gia không tồn tại hoặc đã ngừng hoạt động.");
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends CodeNameEntity> Handler<E> handler(SimpleCatalog catalog) {
        return (Handler<E>) handlers.get(catalog);
    }

    private static <E extends CodeNameEntity> CatalogItemResponse toResponse(Handler<E> handler, E entity) {
        return new CatalogItemResponse(entity.getId(), entity.getCompanyId(), entity.getCode(), entity.getName(),
                handler.countryCode().apply(entity), entity.getStatus(), entity.getVersion(), entity.getUpdatedAt());
    }
}
