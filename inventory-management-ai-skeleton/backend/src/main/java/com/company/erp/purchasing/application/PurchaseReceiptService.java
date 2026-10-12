package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.PurchaseReceiptLineRequest;
import com.company.erp.purchasing.application.dto.PurchaseReceiptLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseReceiptRequest;
import com.company.erp.purchasing.application.dto.PurchaseReceiptResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.InspectionResult;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import com.company.erp.purchasing.domain.PurchaseReceiptLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReceiptRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiếu nhận hàng (PNH, BR-PUR-07): lập theo đơn mua đã duyệt, sửa, gửi duyệt (chỉ phải duyệt khi nhận vượt số đặt
 * quá dung sai {@code purchasing.receipt-over-tolerance-percent}), duyệt, từ chối, hủy. Ghi sổ, nhập kho, đảo ở
 * {@link PurchaseReceiptPostingService}.
 */
@Service
public class PurchaseReceiptService {

    static final String PATH = "/api/v1/purchase-receipts";
    static final String VIEW = "purchase-receipt:view";
    static final String CREATE = "purchase-receipt:create";
    static final String APPROVE = "purchase-receipt:approve";
    static final String ENTITY = "PURCHASE_RECEIPT";
    static final String TOLERANCE = "purchasing.receipt-over-tolerance-percent";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record ReceiptFilter(Long companyId, Long branchId, PurchaseDocumentStatus status, Long purchaseOrderId,
                                LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final PurchaseReceiptRepository repository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final ParameterService parameters;
    private final StockInService stockIn;
    private final PurchaseDocumentFlow.Spec<PurchaseReceipt, PurchaseReceiptResponse> spec;

    @SuppressWarnings("java:S107")
    public PurchaseReceiptService(PurchaseReceiptRepository repository, PurchaseOrderRepository orderRepository,
                                  PurchaseDocumentFlow flow, PurchasingAccess access, ApprovalService approvalService,
                                  ParameterService parameters, StockInService stockIn) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.parameters = parameters;
        this.stockIn = stockIn;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                PurchaseReceiptResponse.class, this::markOverTolerance, null, document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<PurchaseReceiptResponse> search(CurrentUser actor, ReceiptFilter filter, Pageable pageable) {
        Page<PurchaseReceipt> page = flow.search(actor, VIEW, repository,
                new PurchaseDocumentFlow.Filter<PurchaseReceipt>(filter.companyId(), filter.branchId(),
                        filter.status(), filter.dateFrom(), filter.dateTo(), filter.keyword(),
                        filter.purchaseOrderId() == null ? null
                                : (root, query, cb) -> cb.equal(root.get("purchaseOrderId"), filter.purchaseOrderId())),
                pageable);
        Map<Long, PurchaseOrder> orders = new HashMap<>();
        orderRepository.findAllById(page.getContent().stream().map(PurchaseReceipt::getPurchaseOrderId).toList())
                .forEach(order -> orders.put(order.getId(), order));
        Map<Long, PartnerInfo> suppliers = access.partners(page.getContent().stream()
                .map(PurchaseReceipt::getSupplierId).toList());
        Map<Long, WarehouseInfo> warehouses = access.warehouses(page.getContent().stream()
                .map(PurchaseReceipt::getWarehouseId).toList());
        Map<Long, String> inventoryNos = inventoryNumbers(page.getContent());
        return page.map(document -> toResponse(document, orders.get(document.getPurchaseOrderId()),
                suppliers.get(document.getSupplierId()), warehouses.get(document.getWarehouseId()),
                inventoryNos.get(document.getInventoryDocumentId()), false));
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public PurchaseReceiptResponse create(CurrentUser actor, String idempotencyKey, PurchaseReceiptRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseReceiptResponse.class,
                () -> flow.act(actor, createDocument(actor, request), "CREATE", null, spec));
    }

    /** Lập phiếu (không qua Idempotency-Key; dùng chung cho lệnh gộp nhận hàng và nhập kho). */
    PurchaseReceipt createDocument(CurrentUser actor, PurchaseReceiptRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseOrder order = receivableOrder(actor, request.purchaseOrderId());
        access.usableBranch(actor, order.getBranchId());
        PurchaseReceipt document = new PurchaseReceipt(order.getCompanyId(), order.getBranchId(),
                access.nextNumber(order.getCompanyId(), order.getBranchId(), PurchaseReceipt.TYPE_CODE,
                        request.documentDate()), order.getId(), order.getSupplierId(), order.getWarehouseId());
        fill(document, order, request);
        return repository.saveAndFlush(document);
    }

    @Transactional
    public PurchaseReceiptResponse update(CurrentUser actor, Long id, PurchaseReceiptRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseReceipt document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getPurchaseOrderId().equals(request.purchaseOrderId())) {
            throw BusinessException.invalidField("purchaseOrderId", "Không đổi đơn mua sau khi lập.");
        }
        fill(document, receivableOrder(actor, document.getPurchaseOrderId()), request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public PurchaseReceiptResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public PurchaseReceiptResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseReceiptResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseReceiptResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    PurchaseDocumentFlow.Spec<PurchaseReceipt, PurchaseReceiptResponse> spec() {
        return spec;
    }

    /**
     * Có dòng đơn mua mà tổng đã nhận (phiếu đã ghi sổ) cộng phiếu này vượt số đặt × (1 + dung sai) (BR-PUR-07).
     * Dùng cả khi ghi sổ để phát hiện phiếu khác đã ghi sổ trong lúc chờ.
     */
    boolean overTolerance(PurchaseReceipt document, PurchaseOrder order) {
        BigDecimal factor = BigDecimal.ONE.add(parameters.decimalValue(document.getCompanyId(), TOLERANCE)
                .divide(HUNDRED));
        Map<Long, BigDecimal> received = new HashMap<>();
        document.getLines().forEach(line -> received.merge(line.getOrderLineId(), line.getBaseQuantity(),
                BigDecimal::add));
        return received.entrySet().stream().anyMatch(entry -> {
            PurchaseOrderLine line = order.line(entry.getKey());
            return line.getReceivedQuantity().add(entry.getValue())
                    .compareTo(line.getBaseQuantity().multiply(factor)) > 0;
        });
    }

    private boolean markOverTolerance(PurchaseReceipt document) {
        boolean over = overTolerance(document, orderRepository.findById(document.getPurchaseOrderId())
                .orElseThrow(BusinessException::notFound));
        document.markOverTolerance(over);
        return over;
    }

    private PurchaseOrder receivableOrder(CurrentUser actor, Long orderId) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .filter(found -> access.canAccess(actor, found.getCompanyId(), found.getBranchId()))
                .orElseThrow(() -> BusinessException.invalidField("purchaseOrderId", "Đơn mua không tồn tại."));
        if (!order.isOpen()) {
            throw BusinessException.invalidField("purchaseOrderId", "Đơn mua " + order.getDocumentNo()
                    + " chưa duyệt hoặc đã hoàn thành / đóng.");
        }
        return order;
    }

    private void fill(PurchaseReceipt document, PurchaseOrder order, PurchaseReceiptRequest request) {
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(PurchaseOrderLine::getProductId).toList());
        List<PurchaseReceiptLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseReceiptLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            PurchaseOrderLine orderLine = order.line(line.orderLineId());
            ProductInfo product = products.get(orderLine.getProductId());
            BigDecimal base = PurchasingAccess.baseQuantity(line.quantity(), orderLine.getConversionFactor(),
                    field + ".quantity");
            boolean stocked = line.inspectionResult().stocked();
            if (line.inspectionResult() != InspectionResult.ACCEPTED && PurchaseRequisitionService
                    .trim(line.rejectReason()) == null) {
                throw BusinessException.invalidField(field + ".rejectReason", "Hàng lỗi phải ghi lý do.");
            }
            List<String> serials = stocked ? serials(product, line, base, field) : List.of();
            if (stocked) {
                requireTracking(product, line, field);
            }
            lines.add(new PurchaseReceiptLine(i + 1, new PurchaseReceiptLine.Values(orderLine.getId(),
                    orderLine.getProductId(), orderLine.getUnitId(), orderLine.getConversionFactor(), line.quantity(),
                    base, line.inspectionResult(), PurchaseRequisitionService.trim(line.rejectReason()),
                    stocked ? line.locationId() : null, stocked ? PurchaseRequisitionService.trim(line.lotNo()) : null,
                    stocked ? line.manufactureDate() : null, stocked ? line.expiryDate() : null,
                    orderLine.provisionalUnitCost(), PurchaseRequisitionService.trim(line.note()), serials)));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new PurchaseReceipt.Header(request.documentDate(), postingDate,
                PurchaseRequisitionService.trim(request.deliveryNoteNo()),
                PurchaseRequisitionService.trim(request.description())), lines);
    }

    /** Kiểm tra sớm lô / hạn dùng theo cấu hình hàng (BR-INV-01); kho kiểm tra lại khi nhập. */
    private static void requireTracking(ProductInfo product, PurchaseReceiptLineRequest line, String field) {
        if (!product.stockTracked()) {
            throw BusinessException.invalidField(field + ".orderLineId", "Hàng " + product.code()
                    + " là dịch vụ, không nhận hàng qua kho.");
        }
        if (product.lotTracked() && PurchaseRequisitionService.trim(line.lotNo()) == null) {
            throw BusinessException.invalidField(field + ".lotNo", "Hàng " + product.code() + " phải nhập số lô.");
        }
        if (product.expiryTracked() && line.expiryDate() == null) {
            throw BusinessException.invalidField(field + ".expiryDate", "Hàng " + product.code()
                    + " phải nhập hạn dùng.");
        }
    }

    /** Hàng quản lý serial: mỗi serial một đơn vị cơ sở (BR-INV-02). */
    private static List<String> serials(ProductInfo product, PurchaseReceiptLineRequest line, BigDecimal base,
                                        String field) {
        List<String> serials = line.serialNumbers() == null ? List.of()
                : line.serialNumbers().stream().map(String::trim).distinct().toList();
        if (product.serialTracked() && base.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Số serial không trùng (" + serials.size()
                    + ") phải bằng số lượng theo đơn vị cơ sở (" + base.stripTrailingZeros().toPlainString() + ").");
        }
        return product.serialTracked() ? serials : List.of();
    }

    private Map<Long, String> inventoryNumbers(List<PurchaseReceipt> documents) {
        List<Long> ids = documents.stream().map(PurchaseReceipt::getInventoryDocumentId).filter(Objects::nonNull)
                .toList();
        return ids.isEmpty() ? new HashMap<>() : new HashMap<>(stockIn.documentNumbers(ids));
    }

    PurchaseReceiptResponse toResponse(PurchaseReceipt document, boolean detailed) {
        PurchaseOrder order = orderRepository.findById(document.getPurchaseOrderId()).orElse(null);
        return toResponse(document, order,
                access.partners(List.of(document.getSupplierId())).get(document.getSupplierId()),
                access.warehouses(List.of(document.getWarehouseId())).get(document.getWarehouseId()),
                inventoryNumbers(List.of(document)).get(document.getInventoryDocumentId()), detailed);
    }

    @SuppressWarnings("java:S107")
    private PurchaseReceiptResponse toResponse(PurchaseReceipt document, PurchaseOrder order, PartnerInfo supplier,
                                               WarehouseInfo warehouse, String inventoryNo, boolean detailed) {
        List<PurchaseReceiptLineResponse> lines = List.of();
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(PurchaseReceiptLine::getProductId).toList());
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                return new PurchaseReceiptLineResponse(line.getId(), line.getLineNo(), line.getOrderLineId(),
                        line.getProductId(), product == null ? null : product.code(),
                        product == null ? null : product.name(), line.getUnitId(), line.getConversionFactor(),
                        line.getQuantity(), line.getBaseQuantity(), line.getInspectionResult(),
                        line.getRejectReason(), line.getLocationId(), line.getLotNo(), line.getManufactureDate(),
                        line.getExpiryDate(), line.getUnitCost(), line.getNote(), line.getSerialNumbers());
            }).toList();
        }
        return new PurchaseReceiptResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(),
                document.getPurchaseOrderId(), order == null ? null : order.getDocumentNo(), document.getSupplierId(),
                supplier == null ? null : supplier.code(), supplier == null ? null : supplier.name(),
                document.getWarehouseId(), warehouse == null ? null : warehouse.code(),
                warehouse == null ? null : warehouse.name(), document.getDeliveryNoteNo(), document.getDescription(),
                document.isOverTolerance(), document.getStatus(), document.getInventoryDocumentId(), inventoryNo,
                document.getStockedAt(), document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(),
                document.getReversedAt(), document.getReverseDate(), document.getReason(), document.getCreatedBy(),
                document.getVersion(), lines,
                detailed ? approvalService.history(PurchaseReceipt.TYPE_CODE, document.getId()) : List.of());
    }
}
