package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.LocationRequest;
import com.company.erp.masterdata.application.dto.LocationResponse;
import com.company.erp.masterdata.application.dto.UpdateLocationRequest;
import com.company.erp.masterdata.application.dto.UpdateWarehouseRequest;
import com.company.erp.masterdata.application.dto.WarehouseRequest;
import com.company.erp.masterdata.application.dto.WarehouseResponse;
import com.company.erp.masterdata.domain.Warehouse;
import com.company.erp.masterdata.domain.WarehouseLocation;
import com.company.erp.masterdata.domain.WarehouseType;
import com.company.erp.masterdata.infrastructure.persistence.WarehouseLocationRepository;
import com.company.erp.masterdata.infrastructure.persistence.WarehouseRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kho và vị trí (BR-MD-40, BR-MD-41). Kho thấy được khi chi nhánh của kho thuộc phạm vi hoặc kho được gán riêng
 * (BR-SYS-04); ngoài phạm vi trả 404. Mỗi kho có một vị trí mặc định tạo cùng kho.
 */
@Service
public class WarehouseService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "updatedAt");

    private final WarehouseRepository warehouseRepository;
    private final WarehouseLocationRepository locationRepository;
    private final OrganizationQueryService organization;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public WarehouseService(WarehouseRepository warehouseRepository, WarehouseLocationRepository locationRepository,
                            OrganizationQueryService organization, MasterDataGuard guard,
                            IdempotencyService idempotency) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.organization = organization;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> search(CurrentUser actor, Long companyId, Long branchId, WarehouseType type,
                                          String keyword, RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        Specification<Warehouse> inScope = (root, query, cb) -> {
            var byBranch = actor.dataScope().branchIds().isEmpty() ? cb.disjunction()
                    : root.get("branchId").in(actor.dataScope().branchIds());
            var byWarehouse = actor.dataScope().warehouseIds().isEmpty() ? cb.disjunction()
                    : root.get("id").in(actor.dataScope().warehouseIds());
            return cb.or(byBranch, byWarehouse);
        };
        return warehouseRepository.findAll(Specs.allOf(
                        inScope,
                        Specs.equalsIfPresent("companyId", companyId),
                        Specs.equalsIfPresent("branchId", branchId),
                        Specs.equalsIfPresent("warehouseType", type),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(WarehouseService::toResponse);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(CurrentUser actor, Long id) {
        return toResponse(findVisible(actor, id));
    }

    @Transactional
    public WarehouseResponse create(CurrentUser actor, String idempotencyKey, WarehouseRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/warehouses",
                request);
        return idempotency.execute(command, WarehouseResponse.class, () -> {
            OrganizationQueryService.BranchRef branch = organization.branch(request.branchId())
                    .filter(found -> actor.dataScope().canAccessBranch(found.id()))
                    .orElseThrow(BusinessException::notFound);
            if (!branch.active()) {
                throw BusinessException.invalidField("branchId", "Chi nhánh đã ngừng hoạt động.");
            }
            guard.requireNewCode(warehouseRepository.existsByCompanyIdAndCode(branch.companyId(), request.code()),
                    "Kho", request.code());
            Warehouse warehouse = warehouseRepository.saveAndFlush(new Warehouse(branch.companyId(), branch.id(),
                    request.code(), request.name().trim(), request.warehouseType(), request.virtualKind(),
                    ProductMapper.trim(request.address()), ProductMapper.trim(request.storageCondition())));
            locationRepository.saveAndFlush(WarehouseLocation.defaultFor(warehouse.getId()));
            WarehouseResponse response = toResponse(warehouse);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.WAREHOUSE, warehouse.getId(), null, null,
                    response, warehouse.getCompanyId());
            return response;
        });
    }

    @Transactional
    public WarehouseResponse update(CurrentUser actor, Long id, UpdateWarehouseRequest request) {
        Warehouse warehouse = findVisible(actor, id);
        warehouse.verifyVersion(request.version());
        WarehouseResponse before = toResponse(warehouse);
        warehouse.update(request.name().trim(), ProductMapper.trim(request.address()),
                ProductMapper.trim(request.storageCondition()));
        WarehouseResponse after = toResponse(warehouseRepository.saveAndFlush(warehouse));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.WAREHOUSE, id, null, before, after,
                warehouse.getCompanyId());
        return after;
    }

    @Transactional
    public WarehouseResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        Warehouse warehouse = findVisible(actor, id);
        warehouse.verifyVersion(request.version());
        WarehouseResponse before = toResponse(warehouse);
        warehouse.changeStatus(request.status());
        WarehouseResponse after = toResponse(warehouseRepository.saveAndFlush(warehouse));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.WAREHOUSE, id, request.reason(), before,
                after, warehouse.getCompanyId());
        return after;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> locations(CurrentUser actor, Long warehouseId) {
        findVisible(actor, warehouseId);
        return locationRepository.findByWarehouseIdOrderByCode(warehouseId).stream()
                .map(WarehouseService::toResponse).toList();
    }

    @Transactional
    public LocationResponse addLocation(CurrentUser actor, Long warehouseId, String idempotencyKey,
                                        LocationRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/warehouses/" + warehouseId + "/locations", request);
        return idempotency.execute(command, LocationResponse.class, () -> {
            Warehouse warehouse = findVisible(actor, warehouseId);
            guard.requireNewCode(locationRepository.existsByWarehouseIdAndCode(warehouseId, request.code()),
                    "Vị trí", request.code());
            WarehouseLocation location = locationRepository.saveAndFlush(new WarehouseLocation(warehouseId,
                    request.code(), request.name().trim(), request.zoneType(),
                    ProductMapper.trim(request.storageCondition()), false));
            LocationResponse response = toResponse(location);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.LOCATION, location.getId(), null, null,
                    response, warehouse.getCompanyId());
            return response;
        });
    }

    @Transactional
    public LocationResponse updateLocation(CurrentUser actor, Long locationId, UpdateLocationRequest request) {
        WarehouseLocation location = locationRepository.findById(locationId).orElseThrow(BusinessException::notFound);
        Warehouse warehouse = findVisible(actor, location.getWarehouseId());
        location.verifyVersion(request.version());
        LocationResponse before = toResponse(location);
        location.update(request.name().trim(), request.zoneType(), ProductMapper.trim(request.storageCondition()));
        LocationResponse after = toResponse(locationRepository.saveAndFlush(location));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.LOCATION, locationId, null, before, after,
                warehouse.getCompanyId());
        return after;
    }

    @Transactional
    public LocationResponse changeLocationStatus(CurrentUser actor, Long locationId, ChangeStatusRequest request) {
        WarehouseLocation location = locationRepository.findById(locationId).orElseThrow(BusinessException::notFound);
        Warehouse warehouse = findVisible(actor, location.getWarehouseId());
        location.verifyVersion(request.version());
        if (location.isDefaultLocation() && request.status() != RecordStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.DATA_IN_USE, "Không ngừng vị trí mặc định của kho.");
        }
        LocationResponse before = toResponse(location);
        location.changeStatus(request.status());
        LocationResponse after = toResponse(locationRepository.saveAndFlush(location));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.LOCATION, locationId, request.reason(),
                before, after, warehouse.getCompanyId());
        return after;
    }

    private Warehouse findVisible(CurrentUser actor, Long id) {
        Warehouse warehouse = warehouseRepository.findById(id).orElseThrow(BusinessException::notFound);
        if (!actor.dataScope().canAccessWarehouse(warehouse.getId(), warehouse.getBranchId())) {
            throw BusinessException.notFound();
        }
        return warehouse;
    }

    static WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getCompanyId(), warehouse.getBranchId(),
                warehouse.getCode(), warehouse.getName(), warehouse.getWarehouseType(), warehouse.getVirtualKind(),
                warehouse.getAddress(), warehouse.getStorageCondition(), warehouse.getStatus(), warehouse.getVersion(),
                warehouse.getUpdatedAt());
    }

    static LocationResponse toResponse(WarehouseLocation location) {
        return new LocationResponse(location.getId(), location.getWarehouseId(), location.getCode(),
                location.getName(), location.getZoneType(), location.getStorageCondition(),
                location.isDefaultLocation(), location.getStatus(), location.getVersion());
    }
}
