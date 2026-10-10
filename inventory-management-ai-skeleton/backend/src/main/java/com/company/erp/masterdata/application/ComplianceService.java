package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.ComplianceDocumentRequest;
import com.company.erp.masterdata.application.dto.ComplianceDocumentResponse;
import com.company.erp.masterdata.application.dto.ComplianceDocumentTypeRequest;
import com.company.erp.masterdata.application.dto.ComplianceDocumentTypeResponse;
import com.company.erp.masterdata.application.dto.UpdateComplianceDocumentRequest;
import com.company.erp.masterdata.application.dto.UpdateComplianceDocumentTypeRequest;
import com.company.erp.masterdata.domain.ComplianceDocument;
import com.company.erp.masterdata.domain.ComplianceDocumentType;
import com.company.erp.masterdata.domain.ComplianceOwnerType;
import com.company.erp.masterdata.infrastructure.persistence.ComplianceDocumentRepository;
import com.company.erp.masterdata.infrastructure.persistence.ComplianceDocumentTypeRepository;
import com.company.erp.masterdata.infrastructure.persistence.PartnerRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loại hồ sơ, giấy phép và hồ sơ cụ thể có hạn (BR-MD-44, BR-INV-05, D-28). Cảnh báo sắp hết hạn bằng bộ lọc
 * {@code expiringWithinDays}.
 */
@Service
public class ComplianceService {

    private static final Set<String> TYPE_SORTABLE = Set.of("code", "name");
    private static final Set<String> DOCUMENT_SORTABLE = Set.of("expiryDate", "documentNo", "updatedAt");

    private final ComplianceDocumentTypeRepository typeRepository;
    private final ComplianceDocumentRepository documentRepository;
    private final ProductRepository productRepository;
    private final PartnerRepository partnerRepository;
    private final ObjectProvider<TrackedItemDirectory> trackedItems;
    private final BusinessCalendar calendar;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public ComplianceService(ComplianceDocumentTypeRepository typeRepository,
                             ComplianceDocumentRepository documentRepository, ProductRepository productRepository,
                             PartnerRepository partnerRepository, ObjectProvider<TrackedItemDirectory> trackedItems,
                             BusinessCalendar calendar, MasterDataGuard guard, IdempotencyService idempotency) {
        this.typeRepository = typeRepository;
        this.documentRepository = documentRepository;
        this.productRepository = productRepository;
        this.partnerRepository = partnerRepository;
        this.trackedItems = trackedItems;
        this.calendar = calendar;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<ComplianceDocumentTypeResponse> searchTypes(CurrentUser actor, Long companyId, RecordStatus status,
                                                            Pageable pageable) {
        guard.requireCompany(actor, companyId);
        return typeRepository.findAll(Specs.allOf(
                        Specs.<ComplianceDocumentType>equalsIfPresent("companyId", companyId),
                        Specs.equalsIfPresent("status", status)),
                PageRequests.checked(pageable, TYPE_SORTABLE, Sort.by("code")))
                .map(ComplianceService::toResponse);
    }

    @Transactional
    public ComplianceDocumentTypeResponse createType(CurrentUser actor, String idempotencyKey,
                                                     ComplianceDocumentTypeRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/compliance-document-types", request);
        return idempotency.execute(command, ComplianceDocumentTypeResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            guard.requireNewCode(typeRepository.existsByCompanyIdAndCode(request.companyId(), request.code()),
                    "Loại hồ sơ", request.code());
            ComplianceDocumentType type = typeRepository.saveAndFlush(new ComplianceDocumentType(request.companyId(),
                    request.code(), request.name().trim(), request.appliesTo(), request.hasExpiry(),
                    request.requiredProductTypes()));
            ComplianceDocumentTypeResponse response = toResponse(type);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.COMPLIANCE_TYPE, type.getId(), null, null,
                    response, type.getCompanyId());
            return response;
        });
    }

    @Transactional
    public ComplianceDocumentTypeResponse updateType(CurrentUser actor, Long id,
                                                     UpdateComplianceDocumentTypeRequest request) {
        ComplianceDocumentType type = guard.visible(actor, typeRepository.findById(id));
        type.verifyVersion(request.version());
        if (type.getAppliesTo() != request.appliesTo() && documentRepository.existsByDocumentTypeId(id)) {
            throw BusinessException.invalidField("appliesTo", "Đã có hồ sơ thuộc loại này, không đổi đối tượng áp dụng.");
        }
        ComplianceDocumentTypeResponse before = toResponse(type);
        type.update(request.name().trim(), request.appliesTo(), request.hasExpiry(), request.requiredProductTypes());
        ComplianceDocumentTypeResponse after = toResponse(typeRepository.saveAndFlush(type));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.COMPLIANCE_TYPE, id, null, before, after,
                type.getCompanyId());
        return after;
    }

    @Transactional
    public ComplianceDocumentTypeResponse changeTypeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        ComplianceDocumentType type = guard.visible(actor, typeRepository.findById(id));
        type.verifyVersion(request.version());
        ComplianceDocumentTypeResponse before = toResponse(type);
        type.changeStatus(request.status());
        ComplianceDocumentTypeResponse after = toResponse(typeRepository.saveAndFlush(type));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.COMPLIANCE_TYPE, id, request.reason(),
                before, after, type.getCompanyId());
        return after;
    }

    /**
     * Hồ sơ theo đối tượng, loại; {@code expiringWithinDays}: chỉ hồ sơ đang hiệu lực hết hạn trong số ngày tới (kể cả
     * đã hết hạn) để cảnh báo.
     */
    @Transactional(readOnly = true)
    public Page<ComplianceDocumentResponse> searchDocuments(CurrentUser actor, Long companyId,
                                                            ComplianceOwnerType ownerType, Long ownerId,
                                                            Long documentTypeId, Integer expiringWithinDays,
                                                            RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        LocalDate today = calendar.today();
        Specification<ComplianceDocument> expiring = expiringWithinDays == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("expiryDate"),
                        today.plusDays(expiringWithinDays));
        return documentRepository.findAll(Specs.allOf(
                        Specs.<ComplianceDocument>equalsIfPresent("companyId", companyId),
                        Specs.equalsIfPresent("ownerType", ownerType),
                        Specs.equalsIfPresent("ownerId", ownerId),
                        Specs.equalsIfPresent("documentTypeId", documentTypeId),
                        Specs.equalsIfPresent("status", expiringWithinDays == null ? status : RecordStatus.ACTIVE),
                        expiring),
                PageRequests.checked(pageable, DOCUMENT_SORTABLE, Sort.by("expiryDate")))
                .map(document -> toResponse(document, today));
    }

    @Transactional
    public ComplianceDocumentResponse createDocument(CurrentUser actor, String idempotencyKey,
                                                     ComplianceDocumentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/compliance-documents", request);
        return idempotency.execute(command, ComplianceDocumentResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            ComplianceDocumentType type = typeRepository.findById(request.documentTypeId())
                    .filter(found -> found.getCompanyId().equals(request.companyId()) && found.isActive())
                    .orElseThrow(() -> BusinessException.invalidField("documentTypeId", "Loại hồ sơ không hợp lệ."));
            requireOwner(request.companyId(), request.ownerType(), request.ownerId());
            ComplianceDocument document = documentRepository.saveAndFlush(new ComplianceDocument(request.companyId(),
                    type, request.ownerType(), request.ownerId(), request.documentNo().trim(), request.issuedDate(),
                    request.expiryDate(), ProductMapper.trim(request.issuer()), ProductMapper.trim(request.note())));
            ComplianceDocumentResponse response = toResponse(document, calendar.today());
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.COMPLIANCE_DOCUMENT, document.getId(), null,
                    null, response, document.getCompanyId());
            return response;
        });
    }

    @Transactional
    public ComplianceDocumentResponse updateDocument(CurrentUser actor, Long id,
                                                     UpdateComplianceDocumentRequest request) {
        ComplianceDocument document = findDocument(actor, id);
        document.verifyVersion(request.version());
        ComplianceDocumentType type = typeRepository.findById(document.getDocumentTypeId())
                .orElseThrow(() -> new IllegalStateException("Thiếu loại hồ sơ"));
        ComplianceDocumentResponse before = toResponse(document, calendar.today());
        document.update(type, request.documentNo().trim(), request.issuedDate(), request.expiryDate(),
                ProductMapper.trim(request.issuer()), ProductMapper.trim(request.note()));
        ComplianceDocumentResponse after = toResponse(documentRepository.saveAndFlush(document), calendar.today());
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.COMPLIANCE_DOCUMENT, id, null, before, after,
                document.getCompanyId());
        return after;
    }

    @Transactional
    public ComplianceDocumentResponse changeDocumentStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        ComplianceDocument document = findDocument(actor, id);
        document.verifyVersion(request.version());
        ComplianceDocumentResponse before = toResponse(document, calendar.today());
        document.changeStatus(request.status());
        ComplianceDocumentResponse after = toResponse(documentRepository.saveAndFlush(document), calendar.today());
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.COMPLIANCE_DOCUMENT, id, request.reason(),
                before, after, document.getCompanyId());
        return after;
    }

    /** Hàng, đối tác, công ty kiểm tra tại đây; lô, serial hỏi module kho qua {@link TrackedItemDirectory}. */
    private void requireOwner(Long companyId, ComplianceOwnerType ownerType, Long ownerId) {
        boolean valid = switch (ownerType) {
            case PRODUCT -> productRepository.findById(ownerId)
                    .filter(product -> product.getCompanyId().equals(companyId)).isPresent();
            case PARTNER -> partnerRepository.findById(ownerId)
                    .filter(partner -> partner.getCompanyId().equals(companyId)).isPresent();
            case COMPANY -> ownerId.equals(companyId);
            case LOT -> trackedItems.orderedStream().anyMatch(items -> items.lotExists(companyId, ownerId));
            case SERIAL -> trackedItems.orderedStream().anyMatch(items -> items.serialExists(companyId, ownerId));
        };
        if (!valid) {
            throw BusinessException.invalidField("ownerId", "Đối tượng gắn hồ sơ không hợp lệ.");
        }
    }

    private ComplianceDocument findDocument(CurrentUser actor, Long id) {
        ComplianceDocument document = documentRepository.findById(id).orElseThrow(BusinessException::notFound);
        guard.requireCompany(actor, document.getCompanyId());
        return document;
    }

    private static ComplianceDocumentTypeResponse toResponse(ComplianceDocumentType type) {
        return new ComplianceDocumentTypeResponse(type.getId(), type.getCompanyId(), type.getCode(), type.getName(),
                type.getAppliesTo(), type.isHasExpiry(), type.requiredProductTypeSet(), type.getStatus(),
                type.getVersion());
    }

    private static ComplianceDocumentResponse toResponse(ComplianceDocument document, LocalDate today) {
        Long daysToExpiry = document.getExpiryDate() == null ? null
                : ChronoUnit.DAYS.between(today, document.getExpiryDate());
        return new ComplianceDocumentResponse(document.getId(), document.getCompanyId(), document.getDocumentTypeId(),
                document.getOwnerType(), document.getOwnerId(), document.getDocumentNo(), document.getIssuedDate(),
                document.getExpiryDate(), daysToExpiry, document.getIssuer(), document.getNote(), document.getStatus(),
                document.getVersion());
    }
}
