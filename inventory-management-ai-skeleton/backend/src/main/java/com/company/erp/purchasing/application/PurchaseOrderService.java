package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.PurchaseOrderLineRequest;
import com.company.erp.purchasing.application.dto.PurchaseOrderLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseOrderRequest;
import com.company.erp.purchasing.application.dto.PurchaseOrderResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.domain.PurchaseRequisition;
import com.company.erp.purchasing.domain.PurchaseRequisitionLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseRequisitionRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đơn mua hàng (DMH, BR-PUR-04): lập, sửa, gửi duyệt (từ ngưỡng {@code purchasing.order-approval-threshold} trở lên
 * phải duyệt), duyệt, từ chối, hủy, đóng phần còn lại (BR-PUR-05). Duyệt / hủy / đóng cập nhật hàng đang về và số đã
 * đặt của đề nghị mua trong cùng transaction.
 */
@Service
public class PurchaseOrderService {

    static final String PATH = "/api/v1/purchase-orders";
    static final String VIEW = "purchase-order:view";
    static final String CREATE = "purchase-order:create";
    static final String APPROVE = "purchase-order:approve";
    static final String CLOSE = "purchase-order:close";
    static final String ENTITY = "PURCHASE_ORDER";
    static final String THRESHOLD = "purchasing.order-approval-threshold";
    static final String DOMESTIC_CURRENCY = "VND";

    /** {@code openOnly}: chỉ đơn đã duyệt còn mở (để nhận hàng). */
    public record OrderFilter(Long companyId, Long branchId, PurchaseDocumentStatus status, Long supplierId,
                              Long warehouseId, boolean openOnly, LocalDate dateFrom, LocalDate dateTo,
                              String keyword) {
    }

    private final PurchaseOrderRepository repository;
    private final PurchaseRequisitionRepository requisitionRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final PurchaseOrderEffects effects;
    private final ApprovalService approvalService;
    private final ParameterService parameters;
    private final Clock clock;
    private final PurchaseDocumentFlow.Spec<PurchaseOrder, PurchaseOrderResponse> spec;

    @SuppressWarnings("java:S107")
    public PurchaseOrderService(PurchaseOrderRepository repository,
                                PurchaseRequisitionRepository requisitionRepository, PurchaseDocumentFlow flow,
                                PurchasingAccess access, PurchaseOrderEffects effects,
                                ApprovalService approvalService, ParameterService parameters, Clock clock) {
        this.repository = repository;
        this.requisitionRepository = requisitionRepository;
        this.flow = flow;
        this.access = access;
        this.effects = effects;
        this.approvalService = approvalService;
        this.parameters = parameters;
        this.clock = clock;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                PurchaseOrderResponse.class, this::thresholdReached, PurchaseOrder::maxDiscountPercent,
                effects::approved, effects::cancelled);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> search(CurrentUser actor, OrderFilter filter, Pageable pageable) {
        Page<PurchaseOrder> page = flow.search(actor, VIEW, repository,
                new PurchaseDocumentFlow.Filter<PurchaseOrder>(filter.companyId(), filter.branchId(), filter.status(),
                        filter.dateFrom(), filter.dateTo(), filter.keyword(), (root, query, cb) -> cb.and(
                        filter.supplierId() == null ? cb.conjunction()
                                : cb.equal(root.get("supplierId"), filter.supplierId()),
                        filter.warehouseId() == null ? cb.conjunction()
                                : cb.equal(root.get("warehouseId"), filter.warehouseId()),
                        filter.openOnly() ? root.get("status").in(PurchaseOrder.OPEN) : cb.conjunction())),
                pageable);
        Map<Long, PartnerInfo> suppliers = access.partners(page.getContent().stream()
                .map(PurchaseOrder::getSupplierId).toList());
        Map<Long, WarehouseInfo> warehouses = access.warehouses(page.getContent().stream()
                .map(PurchaseOrder::getWarehouseId).toList());
        return page.map(document -> toResponse(document, suppliers.get(document.getSupplierId()),
                warehouses.get(document.getWarehouseId()), false));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public PurchaseOrderResponse create(CurrentUser actor, String idempotencyKey, PurchaseOrderRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseOrderResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            PurchaseOrder document = new PurchaseOrder(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), PurchaseOrder.TYPE_CODE,
                            request.documentDate()));
            fill(document, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public PurchaseOrderResponse update(CurrentUser actor, Long id, PurchaseOrderRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseOrder document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())) {
            throw BusinessException.invalidField("branchId", "Không đổi chi nhánh sau khi lập.");
        }
        fill(document, request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public PurchaseOrderResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public PurchaseOrderResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseOrderResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseOrderResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    /** Đóng phần chưa nhập kho (BR-PUR-05): trừ hàng đang về; số đã đặt của đề nghị mua giữ nguyên. */
    @Transactional
    public PurchaseOrderResponse close(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/close", request,
                PurchaseOrderResponse.class, () -> {
                    access.requirePermission(actor, CLOSE);
                    PurchaseOrder document = flow.lock(actor, id, spec);
                    if (!document.isOpen()) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn mua "
                                + document.getDocumentNo() + " đang ở trạng thái " + document.getStatus()
                                + ", không đóng được.");
                    }
                    effects.closing(document);
                    document.close(actor.id(), Instant.now(clock), request.reason().trim());
                    return flow.act(actor, document, "CLOSE", request.reason(), spec);
                });
    }

    private boolean thresholdReached(PurchaseOrder document) {
        return document.getTotalAmount()
                .compareTo(parameters.decimalValue(document.getCompanyId(), THRESHOLD)) >= 0;
    }

    private void fill(PurchaseOrder document, PurchaseOrderRequest request) {
        Long companyId = document.getCompanyId();
        PartnerInfo supplier = access.supplier(companyId, request.supplierId(), "supplierId");
        access.receivingWarehouse(document.getBranchId(), request.warehouseId(), "warehouseId");
        String currency = request.currencyCode() == null ? DOMESTIC_CURRENCY : request.currencyCode();
        if (!DOMESTIC_CURRENCY.equals(currency)) {
            throw BusinessException.invalidField("currencyCode", "Giai đoạn này chỉ hỗ trợ đơn mua bằng VND (D-24).");
        }
        Integer paymentTerm = request.paymentTermDays() != null ? request.paymentTermDays()
                : supplier.supplierPaymentTermDays();
        Map<Long, PurchaseRequisitionLine> requisitionLines = requisitionLines(document, request);
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            lines.add(line(document, request, request.lines().get(i), i, requisitionLines));
        }
        document.replaceContent(new PurchaseOrder.Header(request.documentDate(), supplier.id(), request.warehouseId(),
                request.expectedDate(), paymentTerm, currency, BigDecimal.ONE,
                PurchaseRequisitionService.trim(request.description())), lines);
    }

    private PurchaseOrderLine line(PurchaseOrder document, PurchaseOrderRequest request, PurchaseOrderLineRequest line,
                                   int index, Map<Long, PurchaseRequisitionLine> requisitionLines) {
        String field = "lines[" + index + "]";
        Long companyId = document.getCompanyId();
        ProductInfo product = access.product(companyId, line.productId(), field + ".productId");
        BigDecimal factor = access.conversionFactor(line.productId(), line.unitId(), request.documentDate(),
                field + ".unitId");
        BigDecimal base = PurchasingAccess.baseQuantity(line.quantity(), factor, field + ".quantity");
        Long taxRateId = line.taxRateId() != null ? line.taxRateId() : product.defaultTaxRateId();
        TaxRateInfo tax = taxRateId == null ? null
                : access.taxRate(companyId, taxRateId, request.documentDate(), field + ".taxRateId");
        if (line.requisitionLineId() != null) {
            PurchaseRequisitionLine source = requisitionLines.get(line.requisitionLineId());
            if (!source.getProductId().equals(line.productId())) {
                throw BusinessException.invalidField(field + ".requisitionLineId",
                        "Dòng đề nghị mua khác mặt hàng với dòng đơn mua.");
            }
            if (base.compareTo(source.remainingQuantity()) > 0) {
                throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": đề nghị mua còn "
                        + source.remainingQuantity().stripTrailingZeros().toPlainString() + " (đơn vị cơ sở).");
            }
        }
        return new PurchaseOrderLine(index + 1, new PurchaseOrderLine.Values(line.productId(), line.unitId(), factor,
                line.quantity(), base, line.unitPrice(), line.discountPercent(), tax == null ? null : tax.id(),
                tax == null ? null : tax.rate(), line.expectedDate(), line.requisitionLineId(), !product.stockTracked(),
                PurchaseRequisitionService.trim(line.note())));
    }

    /** Dòng đề nghị được tham chiếu: cùng công ty, chi nhánh, đề nghị đã duyệt còn mở; mỗi dòng chỉ một lần. */
    private Map<Long, PurchaseRequisitionLine> requisitionLines(PurchaseOrder document, PurchaseOrderRequest request) {
        List<Long> ids = request.lines().stream().map(PurchaseOrderLineRequest::requisitionLineId)
                .filter(Objects::nonNull).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw BusinessException.invalidField("lines", "Mỗi dòng đề nghị mua chỉ được đặt ở một dòng đơn mua.");
        }
        Map<Long, PurchaseRequisitionLine> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (PurchaseRequisition requisition : requisitionRepository.findByLineIds(ids)) {
            if (!requisition.getCompanyId().equals(document.getCompanyId())
                    || !requisition.getBranchId().equals(document.getBranchId()) || !requisition.isOpen()) {
                throw BusinessException.invalidField("lines", "Đề nghị mua " + requisition.getDocumentNo()
                        + " khác chi nhánh hoặc không còn mở.");
            }
            requisition.getLines().stream().filter(line -> ids.contains(line.getId()))
                    .forEach(line -> result.put(line.getId(), line));
        }
        Set<Long> missing = new HashSet<>(ids);
        missing.removeAll(result.keySet());
        if (!missing.isEmpty()) {
            throw BusinessException.invalidField("lines", "Dòng đề nghị mua không tồn tại.");
        }
        return result;
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getSupplierId())).get(document.getSupplierId()),
                access.warehouses(List.of(document.getWarehouseId())).get(document.getWarehouseId()), detailed);
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder document, PartnerInfo supplier, WarehouseInfo warehouse,
                                             boolean detailed) {
        List<PurchaseOrderLineResponse> lines = List.of();
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(PurchaseOrderLine::getProductId).toList());
            lines = document.getLines().stream().map(line -> toResponse(line, products.get(line.getProductId())))
                    .toList();
        }
        return new PurchaseOrderResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getSupplierId(),
                supplier == null ? null : supplier.code(), supplier == null ? null : supplier.name(),
                document.getWarehouseId(), warehouse == null ? null : warehouse.code(),
                warehouse == null ? null : warehouse.name(), document.getExpectedDate(),
                document.getPaymentTermDays(), document.getCurrencyCode(), document.getExchangeRate(),
                document.getDescription(), document.getAmount(), document.getDiscountAmount(),
                document.getTaxAmount(), document.getTotalAmount(), document.getStatus(), document.getSubmittedAt(),
                document.getApprovedAt(), document.getClosedAt(), document.getReason(), document.getCreatedBy(),
                document.getVersion(), lines,
                detailed ? approvalService.history(PurchaseOrder.TYPE_CODE, document.getId()) : List.of());
    }

    private static PurchaseOrderLineResponse toResponse(PurchaseOrderLine line, ProductInfo product) {
        return new PurchaseOrderLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                product == null ? null : product.code(), product == null ? null : product.name(), line.getUnitId(),
                product == null ? null : product.baseUnitId(), line.getConversionFactor(), line.getQuantity(),
                line.getBaseQuantity(), line.getUnitPrice(), line.getDiscountPercent(), line.getDiscountAmount(),
                line.getAmount(), line.getTaxRateId(), line.getTaxRate(), line.getTaxAmount(),
                line.getExpectedDate(), line.getRequisitionLineId(), line.getReceivedQuantity(),
                line.getStockedQuantity(), line.getRejectedQuantity(), line.getInvoicedQuantity(),
                line.getClosedQuantity(), line.outstandingQuantity(), line.getNote());
    }
}
