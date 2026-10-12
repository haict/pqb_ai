package com.company.erp.sales.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductDocumentInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.DeliveryItemResponse;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.SalesDeliveryLineRequest;
import com.company.erp.sales.application.dto.SalesDeliveryLineResponse;
import com.company.erp.sales.application.dto.SalesDeliveryRequest;
import com.company.erp.sales.application.dto.SalesDeliveryResponse;
import com.company.erp.sales.domain.DeliveryStatus;
import com.company.erp.sales.domain.SalesDelivery;
import com.company.erp.sales.domain.SalesDeliveryLine;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.infrastructure.persistence.SalesDeliveryRepository;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiếu giao hàng theo đơn bán (GH, BR-SAL-20..23): lập theo dòng đơn bán còn phải xuất, gửi duyệt (duyệt khi có
 * luồng), duyệt, từ chối, hủy. Xuất kho, giao, khách nhận, thất bại, đảo ở {@link SalesDeliveryPostingService}.
 */
@Service
public class SalesDeliveryService {

    static final String PATH = "/api/v1/sales-deliveries";
    static final String VIEW = "sales-delivery:view";
    static final String CREATE = "sales-delivery:create";
    static final String APPROVE = "sales-delivery:approve";
    static final String ENTITY = "SALES_DELIVERY";

    public record DeliveryFilter(Long companyId, Long branchId, SalesDocumentStatus status,
                                 DeliveryStatus deliveryStatus, Long orderId, Long customerId, LocalDate dateFrom,
                                 LocalDate dateTo, String keyword) {
    }

    private final SalesDeliveryRepository repository;
    private final SalesOrderRepository orderRepository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final ApprovalService approvalService;
    private final StockInService stockIn;
    private final SalesDocumentFlow.Spec<SalesDelivery, SalesDeliveryResponse> spec;

    public SalesDeliveryService(SalesDeliveryRepository repository, SalesOrderRepository orderRepository,
                                SalesDocumentFlow flow, SalesAccess access, ApprovalService approvalService,
                                StockInService stockIn) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.stockIn = stockIn;
        this.spec = new SalesDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), SalesDeliveryResponse.class, null, null,
                document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<SalesDeliveryResponse> search(CurrentUser actor, DeliveryFilter filter, Pageable pageable) {
        Page<SalesDelivery> page = flow.search(actor, VIEW, repository, new SalesDocumentFlow.Filter<SalesDelivery>(
                filter.companyId(), filter.branchId(), filter.status(), filter.dateFrom(), filter.dateTo(),
                filter.keyword(), (root, query, cb) -> cb.and(
                filter.deliveryStatus() == null ? cb.conjunction()
                        : cb.equal(root.get("deliveryStatus"), filter.deliveryStatus()),
                filter.orderId() == null ? cb.conjunction() : cb.equal(root.get("orderId"), filter.orderId()),
                filter.customerId() == null ? cb.conjunction()
                        : cb.equal(root.get("customerId"), filter.customerId()))), pageable);
        Map<Long, PartnerInfo> customers = access.partners(page.getContent().stream()
                .map(SalesDelivery::getCustomerId).toList());
        return page.map(document -> toResponse(document, customers.get(document.getCustomerId()), false));
    }

    @Transactional(readOnly = true)
    public SalesDeliveryResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public SalesDeliveryResponse create(CurrentUser actor, String idempotencyKey, SalesDeliveryRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, SalesDeliveryResponse.class,
                () -> toResponse(createDocument(actor, request, null), true));
    }

    /** Lập phiếu giao (dùng chung cho lệnh tạo, đổi hàng, bán nhanh); trả chứng từ đã lưu. */
    SalesDelivery createDocument(CurrentUser actor, SalesDeliveryRequest request, Long sourceReturnId) {
        access.requirePermission(actor, CREATE);
        SalesOrder order = openOrder(actor, request.orderId());
        SalesDelivery document = new SalesDelivery(order.getCompanyId(), order.getBranchId(), access.nextNumber(
                order.getCompanyId(), order.getBranchId(), SalesDelivery.TYPE_CODE, request.documentDate()),
                order.getId(), order.getCustomerId(), order.getWarehouseId(), sourceReturnId);
        document.assignSalesUser(order.getSalesUserId());
        fill(document, order, request);
        document = repository.saveAndFlush(document);
        flow.act(actor, document, "CREATE", null, spec);
        return document;
    }

    @Transactional
    public SalesDeliveryResponse update(CurrentUser actor, Long id, SalesDeliveryRequest request) {
        access.requirePermission(actor, CREATE);
        SalesDelivery document = flow.lock(actor, id, spec);
        SalesDocumentFlow.requireVersion(document, request.version());
        if (!document.getOrderId().equals(request.orderId())) {
            throw BusinessException.invalidField("orderId", "Không đổi đơn bán sau khi lập.");
        }
        fill(document, openOrder(actor, request.orderId()), request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public SalesDeliveryResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public SalesDeliveryResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public SalesDeliveryResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public SalesDeliveryResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    SalesDocumentFlow.Spec<SalesDelivery, SalesDeliveryResponse> spec() {
        return spec;
    }

    private SalesOrder openOrder(CurrentUser actor, Long orderId) {
        SalesOrder order = orderRepository.findById(orderId)
                .filter(found -> access.canAccess(actor, found.getCompanyId(), found.getBranchId()))
                .orElseThrow(() -> BusinessException.invalidField("orderId", "Đơn bán không tồn tại hoặc ngoài phạm vi."));
        if (!order.isOpen()) {
            throw BusinessException.invalidField("orderId", "Đơn bán " + order.getDocumentNo()
                    + " chưa duyệt hoặc đã hoàn thành / đóng.");
        }
        return order;
    }

    /** Dòng giao: dòng hàng hóa của đơn, không vượt số còn phải xuất (kiểm tra lại khi xuất kho sau khi khóa đơn). */
    private void fill(SalesDelivery document, SalesOrder order, SalesDeliveryRequest request) {
        List<SalesDeliveryLine> lines = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            SalesDeliveryLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            SalesOrderLine orderLine = order.getLines().stream()
                    .filter(candidate -> candidate.getId().equals(line.orderLineId())).findFirst()
                    .orElseThrow(() -> BusinessException.invalidField(field + ".orderLineId",
                            "Dòng không thuộc đơn " + order.getDocumentNo() + "."));
            if (orderLine.isServiceLine()) {
                throw BusinessException.invalidField(field + ".orderLineId", "Dòng dịch vụ không giao hàng qua kho.");
            }
            if (!seen.add(line.orderLineId())) {
                throw BusinessException.invalidField(field + ".orderLineId", "Mỗi dòng đơn chỉ một lần trên phiếu giao.");
            }
            BigDecimal base = SalesAccess.baseQuantity(line.quantity(), orderLine.getConversionFactor(),
                    field + ".quantity");
            if (base.compareTo(orderLine.outstandingQuantity()) > 0) {
                throw BusinessException.invalidField(field + ".quantity", "Dòng " + orderLine.getLineNo()
                        + " của đơn chỉ còn " + orderLine.outstandingQuantity().stripTrailingZeros().toPlainString()
                        + " (đơn vị cơ sở) chưa xuất.");
            }
            lines.add(new SalesDeliveryLine(i + 1, new SalesDeliveryLine.Values(orderLine.getId(),
                    orderLine.getProductId(), orderLine.getUnitId(), orderLine.getConversionFactor(), line.quantity(),
                    base, SalesAccess.trim(line.lotNo()), SalesAccess.trim(line.lotOverrideReason()),
                    SalesAccess.trim(line.note()), line.serialNumbers() == null ? List.of()
                    : line.serialNumbers().stream().map(String::trim).toList())));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new SalesDelivery.Header(request.documentDate(), postingDate,
                firstNonNull(SalesAccess.trim(request.deliveryAddress()), order.getDeliveryAddress()),
                firstNonNull(SalesAccess.trim(request.receiverName()), order.getReceiverName()),
                firstNonNull(SalesAccess.trim(request.receiverPhone()), order.getReceiverPhone()),
                SalesAccess.trim(request.shipperName()), SalesAccess.trim(request.description())), lines);
    }

    private static String firstNonNull(String value, String fallback) {
        return value != null ? value : fallback;
    }

    SalesDeliveryResponse toResponse(SalesDelivery document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getCustomerId())).get(document.getCustomerId()),
                detailed);
    }

    private SalesDeliveryResponse toResponse(SalesDelivery document, PartnerInfo customer, boolean detailed) {
        List<SalesDeliveryLineResponse> lines = List.of();
        String orderNo = null;
        String inventoryNo = null;
        WarehouseInfo warehouse = null;
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(SalesDeliveryLine::getProductId).toList());
            Map<Long, ProductDocumentInfo> documents = access.productDocuments(products.keySet());
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                ProductDocumentInfo info = documents.get(line.getProductId());
                return new SalesDeliveryLineResponse(line.getId(), line.getLineNo(), line.getOrderLineId(),
                        line.getProductId(), product == null ? null : product.code(),
                        product == null ? null : product.name(), line.getUnitId(), line.getConversionFactor(),
                        line.getQuantity(), line.getBaseQuantity(), line.getLotNo(), line.getLotOverrideReason(),
                        line.getInvoicedQuantity(), line.getReturnedQuantity(), line.uninvoicedQuantity(),
                        info == null ? null : info.registrationNo(), info == null ? null : info.originCountryCode(),
                        line.getNote(), List.copyOf(line.getSerialNumbers()), line.getItems().stream()
                        .map(item -> new DeliveryItemResponse(item.getLotNo(), item.getExpiryDate(), item.getSerialNo(),
                                item.getQualityStatus(), item.getBaseQuantity())).toList());
            }).toList();
            orderNo = orderRepository.findById(document.getOrderId()).map(SalesOrder::getDocumentNo).orElse(null);
            if (document.getInventoryDocumentId() != null) {
                inventoryNo = stockIn.documentNumbers(List.of(document.getInventoryDocumentId()))
                        .get(document.getInventoryDocumentId());
            }
            warehouse = access.warehouses(List.of(document.getWarehouseId())).get(document.getWarehouseId());
        }
        return new SalesDeliveryResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(), document.getOrderId(),
                orderNo, document.getCustomerId(), customer == null ? null : customer.code(),
                customer == null ? null : customer.name(), document.getWarehouseId(),
                warehouse == null ? null : warehouse.code(), document.getDeliveryAddress(), document.getReceiverName(),
                document.getReceiverPhone(), document.getShipperName(), document.getSourceReturnId(),
                document.getDescription(), document.getDeliveryStatus(), document.getInventoryDocumentId(),
                inventoryNo, document.getShippedAt(), document.getReceivedDate(), document.getReceivedBy(),
                document.getSignedDocumentRef(), document.getDeliveryNote(), document.getStatus(),
                document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(),
                document.getReverseDate(), document.getReason(), document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(SalesDelivery.TYPE_CODE, document.getId()) : List.of());
    }
}
