package com.company.erp.masterdata.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.common.infrastructure.persistence.Specs;
import com.company.erp.masterdata.application.dto.PriceListItemRequest;
import com.company.erp.masterdata.application.dto.PriceListItemResponse;
import com.company.erp.masterdata.application.dto.PriceListRequest;
import com.company.erp.masterdata.application.dto.PriceListResponse;
import com.company.erp.masterdata.application.dto.UpdatePriceListRequest;
import com.company.erp.masterdata.domain.PriceList;
import com.company.erp.masterdata.domain.PriceListItem;
import com.company.erp.masterdata.domain.PriceTargetType;
import com.company.erp.masterdata.domain.PriceType;
import com.company.erp.masterdata.domain.Product;
import com.company.erp.masterdata.infrastructure.persistence.PartnerGroupRepository;
import com.company.erp.masterdata.infrastructure.persistence.PartnerRepository;
import com.company.erp.masterdata.infrastructure.persistence.PriceListItemRepository;
import com.company.erp.masterdata.infrastructure.persistence.PriceListRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductUnitRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảng giá: giá mua tham khảo, niêm yết, giá sàn, bảng giá bán (BR-MD-42). Giá mua tham khảo chỉ người có quyền
 * {@code purchase-price:view} thấy (BR-SYS-05): không có quyền thì bảng giá loại này coi như không tồn tại (404).
 */
@Service
public class PriceListService {

    static final String PURCHASE_PRICE_PERMISSION = "purchase-price:view";
    private static final Set<String> SORTABLE = Set.of("code", "name", "validFrom", "updatedAt");
    private static final Set<String> ITEM_SORTABLE = Set.of("productId", "minQuantity", "unitPrice");
    private static final String DEFAULT_CURRENCY = "VND";

    private final PriceListRepository priceListRepository;
    private final PriceListItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final ProductUnitRepository productUnitRepository;
    private final PartnerGroupRepository groupRepository;
    private final PartnerRepository partnerRepository;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public PriceListService(PriceListRepository priceListRepository, PriceListItemRepository itemRepository,
                            ProductRepository productRepository, ProductUnitRepository productUnitRepository,
                            PartnerGroupRepository groupRepository, PartnerRepository partnerRepository,
                            MasterDataGuard guard, IdempotencyService idempotency) {
        this.priceListRepository = priceListRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.productUnitRepository = productUnitRepository;
        this.groupRepository = groupRepository;
        this.partnerRepository = partnerRepository;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    @Transactional(readOnly = true)
    public Page<PriceListResponse> search(CurrentUser actor, Long companyId, PriceType priceType, String keyword,
                                          RecordStatus status, Pageable pageable) {
        guard.requireCompany(actor, companyId);
        Specification<PriceList> hidePurchase = actor.hasPermission(PURCHASE_PRICE_PERMISSION) ? null
                : (root, query, cb) -> cb.notEqual(root.get("priceType"), PriceType.PURCHASE_REFERENCE);
        return priceListRepository.findAll(Specs.allOf(
                        Specs.<PriceList>equalsIfPresent("companyId", companyId),
                        Specs.equalsIfPresent("priceType", priceType),
                        Specs.keyword(keyword, "code", "name"),
                        Specs.equalsIfPresent("status", status),
                        hidePurchase),
                PageRequests.checked(pageable, SORTABLE, Sort.by("code")))
                .map(PriceListService::toResponse);
    }

    @Transactional(readOnly = true)
    public PriceListResponse get(CurrentUser actor, Long id) {
        return toResponse(findVisible(actor, id));
    }

    @Transactional
    public PriceListResponse create(CurrentUser actor, String idempotencyKey, PriceListRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/price-lists",
                request);
        return idempotency.execute(command, PriceListResponse.class, () -> {
            guard.requireCompany(actor, request.companyId());
            requirePurchaseVisibility(actor, request.priceType());
            guard.requireNewCode(priceListRepository.existsByCompanyIdAndCode(request.companyId(), request.code()),
                    "Bảng giá", request.code());
            validateTarget(request.companyId(), request.targetType(), request.partnerGroupId(), request.partnerId());
            PriceList priceList = priceListRepository.saveAndFlush(new PriceList(request.companyId(), request.code(),
                    request.name().trim(), request.priceType(), request.targetType(), request.partnerGroupId(),
                    request.partnerId(), currency(request.currencyCode()), request.validFrom(), request.validTo()));
            PriceListResponse response = toResponse(priceList);
            guard.audit(actor, MasterDataAudit.CREATE, MasterDataAudit.PRICE_LIST, priceList.getId(), null, null,
                    response, priceList.getCompanyId());
            return response;
        });
    }

    @Transactional
    public PriceListResponse update(CurrentUser actor, Long id, UpdatePriceListRequest request) {
        PriceList priceList = findVisible(actor, id);
        priceList.verifyVersion(request.version());
        validateTarget(priceList.getCompanyId(), request.targetType(), request.partnerGroupId(), request.partnerId());
        PriceListResponse before = toResponse(priceList);
        priceList.update(request.name().trim(), request.targetType(), request.partnerGroupId(), request.partnerId(),
                currency(request.currencyCode()), request.validFrom(), request.validTo());
        PriceListResponse after = toResponse(priceListRepository.saveAndFlush(priceList));
        guard.audit(actor, MasterDataAudit.UPDATE, MasterDataAudit.PRICE_LIST, id, null, before, after,
                priceList.getCompanyId());
        return after;
    }

    @Transactional
    public PriceListResponse changeStatus(CurrentUser actor, Long id, ChangeStatusRequest request) {
        PriceList priceList = findVisible(actor, id);
        priceList.verifyVersion(request.version());
        PriceListResponse before = toResponse(priceList);
        priceList.changeStatus(request.status());
        PriceListResponse after = toResponse(priceListRepository.saveAndFlush(priceList));
        guard.audit(actor, MasterDataAudit.CHANGE_STATUS, MasterDataAudit.PRICE_LIST, id, request.reason(), before,
                after, priceList.getCompanyId());
        return after;
    }

    @Transactional(readOnly = true)
    public Page<PriceListItemResponse> items(CurrentUser actor, Long priceListId, Pageable pageable) {
        findVisible(actor, priceListId);
        Page<PriceListItem> page = itemRepository.findByPriceListId(priceListId,
                PageRequests.checked(pageable, ITEM_SORTABLE, Sort.by("productId", "unitId", "minQuantity")));
        Map<Long, Product> products = productRepository.findAllById(page.getContent().stream()
                        .map(PriceListItem::getProductId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return page.map(item -> toResponse(item, products.get(item.getProductId())));
    }

    /** Đặt giá theo (hàng, đơn vị, bậc số lượng): đã có thì đổi giá, chưa có thì thêm. */
    @Transactional
    public PriceListItemResponse upsertItem(CurrentUser actor, Long priceListId, PriceListItemRequest request) {
        PriceList priceList = findVisible(actor, priceListId);
        Product product = productRepository.findById(request.productId())
                .filter(found -> found.getCompanyId().equals(priceList.getCompanyId()))
                .orElseThrow(() -> BusinessException.invalidField("productId", "Hàng hóa không thuộc công ty."));
        boolean validUnit = request.unitId().equals(product.getBaseUnitId())
                || !productUnitRepository.findByProductIdOrderByUnitIdAscEffectiveFromDesc(product.getId()).stream()
                .filter(unit -> unit.getUnitId().equals(request.unitId()))
                .toList().isEmpty();
        if (!validUnit) {
            throw BusinessException.invalidField("unitId", "Đơn vị tính chưa được khai báo cho hàng này.");
        }
        BigDecimal minQuantity = request.minQuantity() == null ? BigDecimal.ZERO : request.minQuantity();
        PriceListItem item = itemRepository.findByPriceListIdAndProductIdAndUnitIdAndMinQuantity(priceListId,
                        product.getId(), request.unitId(), minQuantity)
                .orElseGet(() -> new PriceListItem(priceListId, product.getId(), request.unitId(), minQuantity,
                        request.unitPrice()));
        BigDecimal before = item.getId() == null ? null : item.getUnitPrice();
        item.changePrice(request.unitPrice());
        item = itemRepository.saveAndFlush(item);
        PriceListItemResponse response = toResponse(item, product);
        guard.audit(actor, before == null ? MasterDataAudit.CREATE : MasterDataAudit.UPDATE,
                MasterDataAudit.PRICE_LIST_ITEM, item.getId(), null, before, response, priceList.getCompanyId());
        return response;
    }

    @Transactional
    public void deleteItem(CurrentUser actor, Long priceListId, Long itemId) {
        PriceList priceList = findVisible(actor, priceListId);
        PriceListItem item = itemRepository.findById(itemId)
                .filter(found -> found.getPriceListId().equals(priceListId))
                .orElseThrow(BusinessException::notFound);
        itemRepository.delete(item);
        guard.audit(actor, MasterDataAudit.DELETE, MasterDataAudit.PRICE_LIST_ITEM, itemId, null,
                toResponse(item, null), null, priceList.getCompanyId());
    }

    private void validateTarget(Long companyId, PriceTargetType targetType, Long groupId, Long partnerId) {
        if (targetType == PriceTargetType.PARTNER_GROUP && groupId != null) {
            guard.requireActiveReference(groupRepository.findById(groupId), companyId, "partnerGroupId",
                    "Nhóm đối tác");
        }
        if (targetType == PriceTargetType.PARTNER && partnerId != null) {
            guard.requireActiveReference(partnerRepository.findById(partnerId), companyId, "partnerId", "Đối tác");
        }
    }

    private PriceList findVisible(CurrentUser actor, Long id) {
        PriceList priceList = guard.visible(actor, priceListRepository.findById(id));
        if (priceList.getPriceType() == PriceType.PURCHASE_REFERENCE
                && !actor.hasPermission(PURCHASE_PRICE_PERMISSION)) {
            throw BusinessException.notFound();
        }
        return priceList;
    }

    private static void requirePurchaseVisibility(CurrentUser actor, PriceType priceType) {
        if (priceType == PriceType.PURCHASE_REFERENCE && !actor.hasPermission(PURCHASE_PRICE_PERMISSION)) {
            throw BusinessException.invalidField("priceType", "Bạn không có quyền xem giá mua.");
        }
    }

    private static String currency(String currencyCode) {
        return currencyCode == null ? DEFAULT_CURRENCY : currencyCode;
    }

    private static PriceListResponse toResponse(PriceList priceList) {
        return new PriceListResponse(priceList.getId(), priceList.getCompanyId(), priceList.getCode(),
                priceList.getName(), priceList.getPriceType(), priceList.getTargetType(),
                priceList.getPartnerGroupId(), priceList.getPartnerId(), priceList.getCurrencyCode(),
                priceList.getValidFrom(), priceList.getValidTo(), priceList.getStatus(), priceList.getVersion(),
                priceList.getUpdatedAt());
    }

    private static PriceListItemResponse toResponse(PriceListItem item, Product product) {
        return new PriceListItemResponse(item.getId(), item.getPriceListId(), item.getProductId(),
                product == null ? null : product.getCode(), product == null ? null : product.getName(),
                item.getUnitId(), item.getMinQuantity(), item.getUnitPrice());
    }
}
