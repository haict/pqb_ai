package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.PartnerAddressDto;
import com.company.erp.masterdata.application.dto.PartnerContactDto;
import com.company.erp.masterdata.application.dto.PartnerRequest;
import com.company.erp.masterdata.application.dto.PartnerResponse;
import com.company.erp.masterdata.application.dto.UpdatePartnerRequest;
import com.company.erp.masterdata.domain.CustomerType;
import com.company.erp.masterdata.domain.Partner;
import com.company.erp.masterdata.domain.PartnerAddress;
import com.company.erp.masterdata.domain.PartnerContact;
import com.company.erp.masterdata.domain.PartnerDetails;
import com.company.erp.masterdata.domain.PartnerKind;
import com.company.erp.masterdata.domain.PartnerRole;
import com.company.erp.masterdata.infrastructure.persistence.PartnerGroupRepository;
import com.company.erp.masterdata.infrastructure.persistence.PartnerRepository;
import com.company.erp.masterdata.infrastructure.persistence.PriceListRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import jakarta.persistence.criteria.Join;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đối tác nhiều vai trò (BR-MD-30..35). Người dùng "chỉ dữ liệu của mình" chỉ thấy đối tác mình phụ trách
 * (BR-SYS-04); ngoài phạm vi trả 404.
 */
@Service
public class PartnerService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "updatedAt");

    private final PartnerRepository partnerRepository;
    private final PartnerGroupRepository groupRepository;
    private final PriceListRepository priceListRepository;
    private final ProductReferences productReferences;
    private final OrganizationQueryService organization;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public PartnerService(PartnerRepository partnerRepository, PartnerGroupRepository groupRepository,
                          PriceListRepository priceListRepository, ProductReferences productReferences,
                          OrganizationQueryService organization, MasterDataGuard guard,
                          IdempotencyService idempotency) {
        this.partnerRepository = partnerRepository;
        this.groupRepository = groupRepository;
        this.priceListRepository = priceListRepository;
        this.productReferences = productReferences;
        this.organization = organization;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<PartnerResponse> search(CurrentUser actor, Long companyId, String keyword, PartnerRole role,
                                        RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        Specification<Partner> byRole = role == null ? null : (root, query, cb) -> {
            Join<Partner, PartnerRole> roles = root.join("roles");
            return cb.equal(roles, role);
        };
        Specification<Partner> ownData = actor.dataScope().ownDataOnly()
                ? (root, query, cb) -> cb.equal(root.get("salesUserId"), actor.id())
                : null;
        return partnerRepository.findAll(Specs.allOf(
                        Specs.<Partner>equalsIfPresent("companyId", companyId),
                        Specs.keyword(keyword, "code", "name", "taxCode", "phone", "legacyCode"),
                        Specs.equalsIfPresent("status", status),
                        byRole, ownData),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(partner -> toResponse(partner, false));
    }

    @Transactional(readOnly = true)
    public PartnerResponse get(CurrentUser actor, Long id) {
        return toResponse(findVisible(actor, id), true);
    }

    @Transactional
    public PartnerResponse create(CurrentUser actor, String idempotencyKey, PartnerRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/partners", request);
        return idempotency.execute(command, PartnerResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            guard.requireNewCode(partnerRepository.existsByCompanyIdAndCode(request.companyId(), request.code()),
                    "Đối tác", request.code());
            PartnerDetails details = details(request.legacyCode(), request.name(), request.shortName(),
                    request.partnerKind(), request.taxCode(), request.address(), request.phone(), request.email(),
                    request.countryCode(), request.partnerGroupId(), request.roles(), request.customerType(),
                    request.salesUserId(), request.managingBranchId(), request.paymentTermDays(),
                    request.creditLimit(), request.minRemainingShelfLifeDays(), request.priceListId(),
                    request.supplierPaymentTermDays(), request.currencyCode(), request.distributedBrands(),
                    request.agentDiscountPercent(), request.agentRegion(), request.agentCreditLimit());
            validate(request.companyId(), details);
            Partner partner = new Partner(request.companyId(), request.code(), details);
            partner.replaceContacts(contacts(request.contacts()));
            partner.replaceAddresses(addresses(request.addresses()));
            partner = partnerRepository.saveAndFlush(partner);
            PartnerResponse response = toResponse(partner, true);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.PARTNER, partner.getId(), null, null, response,
                    partner.getCompanyId());
            return response;
        });
    }

    @Transactional
    public PartnerResponse update(CurrentUser actor, Long id, UpdatePartnerRequest request) {
        Partner partner = findVisible(actor, id);
        partner.verifyVersion(request.version());
        PartnerDetails details = details(request.legacyCode(), request.name(), request.shortName(),
                request.partnerKind(), request.taxCode(), request.address(), request.phone(), request.email(),
                request.countryCode(), request.partnerGroupId(), request.roles(), request.customerType(),
                request.salesUserId(), request.managingBranchId(), request.paymentTermDays(), request.creditLimit(),
                request.minRemainingShelfLifeDays(), request.priceListId(), request.supplierPaymentTermDays(),
                request.currencyCode(), request.distributedBrands(), request.agentDiscountPercent(),
                request.agentRegion(), request.agentCreditLimit());
        validate(partner.getCompanyId(), details);
        PartnerResponse before = toResponse(partner, true);
        partner.update(details);
        partner.replaceContacts(contacts(request.contacts()));
        partner.replaceAddresses(addresses(request.addresses()));
        PartnerResponse after = toResponse(partnerRepository.saveAndFlush(partner), true);
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.PARTNER, id, null, before, after,
                partner.getCompanyId());
        return after;
    }

    @Transactional
    public PartnerResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        Partner partner = findVisible(actor, id);
        partner.verifyVersion(request.version());
        PartnerResponse before = toResponse(partner, false);
        partner.changeStatus(request.status());
        PartnerResponse after = toResponse(partnerRepository.saveAndFlush(partner), false);
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.PARTNER, id, request.reason(), before,
                after, partner.getCompanyId());
        return after;
    }

    /** Kiểm tra tham chiếu (dùng chung với nhập Excel). */
    void validate(Long companyId, PartnerDetails details) {
        if (details.partnerGroupId() != null) {
            guard.requireActiveReference(groupRepository.findById(details.partnerGroupId()), companyId,
                    "partnerGroupId", "Nhóm đối tác");
        }
        if (details.priceListId() != null) {
            guard.requireActiveReference(priceListRepository.findById(details.priceListId()), companyId,
                    "priceListId", "Bảng giá");
        }
        productReferences.requireCountry(details.countryCode(), "countryCode");
        if (details.salesUserId() != null) {
            boolean valid = organization.user(details.salesUserId())
                    .filter(user -> user.active() && user.companyId().equals(companyId))
                    .isPresent();
            if (!valid) {
                throw BusinessException.invalidField("salesUserId", "Nhân viên phụ trách không hợp lệ.");
            }
        }
        if (details.managingBranchId() != null) {
            boolean valid = organization.branch(details.managingBranchId())
                    .filter(branch -> branch.active() && branch.companyId().equals(companyId))
                    .isPresent();
            if (!valid) {
                throw BusinessException.invalidField("managingBranchId", "Chi nhánh phụ trách không hợp lệ.");
            }
        }
    }

    static PartnerDetails details(String legacyCode, String name, String shortName,
                                  PartnerKind kind, String taxCode, String address,
                                  String phone, String email, String countryCode, Long partnerGroupId,
                                  Set<PartnerRole> roles, CustomerType customerType,
                                  Long salesUserId, Long managingBranchId, Integer paymentTermDays,
                                  BigDecimal creditLimit, Integer minRemainingShelfLifeDays,
                                  Long priceListId, Integer supplierPaymentTermDays, String currencyCode,
                                  String distributedBrands, BigDecimal agentDiscountPercent,
                                  String agentRegion, BigDecimal agentCreditLimit) {
        return new PartnerDetails(ProductMapper.trim(legacyCode), name.trim(), ProductMapper.trim(shortName), kind,
                ProductMapper.trim(taxCode), ProductMapper.trim(address), ProductMapper.trim(phone),
                ProductMapper.trim(email), countryCode, partnerGroupId, roles, customerType, salesUserId,
                managingBranchId, paymentTermDays, creditLimit, minRemainingShelfLifeDays, priceListId,
                supplierPaymentTermDays, currencyCode, ProductMapper.trim(distributedBrands), agentDiscountPercent,
                ProductMapper.trim(agentRegion), agentCreditLimit);
    }

    private Partner findVisible(CurrentUser actor, Long id) {
        Partner partner = guard.visible(actor, partnerRepository.findById(id));
        if (actor.dataScope().ownDataOnly() && !actor.id().equals(partner.getSalesUserId())) {
            throw BusinessException.notFound();
        }
        return partner;
    }

    private static List<PartnerContact> contacts(List<PartnerContactDto> contacts) {
        return contacts == null ? List.of() : contacts.stream()
                .map(contact -> new PartnerContact(contact.name().trim(), ProductMapper.trim(contact.position()),
                        ProductMapper.trim(contact.phone()), ProductMapper.trim(contact.email()),
                        contact.primaryContact()))
                .toList();
    }

    private static List<PartnerAddress> addresses(List<PartnerAddressDto> addresses) {
        return addresses == null ? List.of() : addresses.stream()
                .map(address -> new PartnerAddress(address.address().trim(),
                        ProductMapper.trim(address.receiverName()), ProductMapper.trim(address.phone()),
                        address.defaultAddress()))
                .toList();
    }

    static PartnerResponse toResponse(Partner partner, boolean detailed) {
        return new PartnerResponse(partner.getId(), partner.getCompanyId(), partner.getCode(),
                partner.getLegacyCode(), partner.getName(), partner.getShortName(), partner.getPartnerKind(),
                partner.getTaxCode(), partner.getAddress(), partner.getPhone(), partner.getEmail(),
                partner.getCountryCode(), partner.getPartnerGroupId(), partner.getRoles(), partner.getCustomerType(),
                partner.getSalesUserId(), partner.getManagingBranchId(), partner.getPaymentTermDays(),
                partner.getCreditLimit(), partner.getMinRemainingShelfLifeDays(), partner.getPriceListId(),
                partner.getSupplierPaymentTermDays(), partner.getCurrencyCode(), partner.getDistributedBrands(),
                partner.getAgentDiscountPercent(), partner.getAgentRegion(), partner.getAgentCreditLimit(),
                detailed ? partner.getContacts().stream()
                        .map(contact -> new PartnerContactDto(contact.getName(), contact.getPosition(),
                                contact.getPhone(), contact.getEmail(), contact.isPrimaryContact()))
                        .toList() : List.of(),
                detailed ? partner.getAddresses().stream()
                        .map(address -> new PartnerAddressDto(address.getAddress(), address.getReceiverName(),
                                address.getPhone(), address.isDefaultAddress()))
                        .toList() : List.of(),
                partner.getStatus(), partner.getVersion(), partner.getUpdatedAt());
    }
}
