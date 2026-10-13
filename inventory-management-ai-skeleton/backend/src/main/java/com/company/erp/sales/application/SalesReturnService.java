package com.company.erp.sales.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ReturnableSalesLineResponse;
import com.company.erp.sales.application.dto.SalesLineRequest;
import com.company.erp.sales.application.dto.SalesOrderRequest;
import com.company.erp.sales.application.dto.SalesOrderResponse;
import com.company.erp.sales.application.dto.SalesReturnLineRequest;
import com.company.erp.sales.application.dto.SalesReturnLineResponse;
import com.company.erp.sales.application.dto.SalesReturnRequest;
import com.company.erp.sales.application.dto.SalesReturnResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderItem;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.domain.SalesReturn;
import com.company.erp.sales.domain.SalesReturnLine;
import com.company.erp.sales.domain.SalesReturnType;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.sales.infrastructure.persistence.SalesReturnRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hàng bán bị trả lại (TLB) / giảm giá hàng bán (GGB), BR-SAL-32, 36, D-54: lập theo dòng của một đơn bán đã ghi sổ
 * (trả hàng đúng lô / serial đã xuất, đơn giá sau chiết khấu của dòng), gửi duyệt (duyệt khi có luồng), duyệt, từ
 * chối, hủy; đổi hàng (BR-SAL-33) lập đơn bán mới từ phiếu trả. Ghi sổ, đảo ở {@link SalesReturnPostingService}.
 */
@Service
public class SalesReturnService {

    static final String PATH = "/api/v1/sales-returns";
    static final String VIEW = "sales-return:view";
    static final String CREATE = "sales-return:create";
    static final String APPROVE = "sales-return:approve";
    static final String ENTITY = "SALES_RETURN";

    public record ReturnFilter(Long companyId, Long branchId, SalesReturnType documentType, SalesDocumentStatus status,
                               Long customerId, LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final SalesReturnRepository repository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderService orders;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final ApprovalService approvalService;
    private final StockInService stockIn;
    private final SalesDocumentFlow.Spec<SalesReturn, SalesReturnResponse> spec;

    @SuppressWarnings("java:S107")
    public SalesReturnService(SalesReturnRepository repository, SalesOrderRepository orderRepository,
                              SalesOrderService orders, SalesDocumentFlow flow, SalesAccess access,
                              ApprovalService approvalService, StockInService stockIn) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.orders = orders;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.stockIn = stockIn;
        this.spec = new SalesDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), SalesReturnResponse.class, null, null,
                document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<SalesReturnResponse> search(CurrentUser actor, ReturnFilter filter, Pageable pageable) {
        Page<SalesReturn> page = flow.search(actor, VIEW, repository, new SalesDocumentFlow.Filter<SalesReturn>(
                filter.companyId(), filter.branchId(), filter.status(), filter.dateFrom(), filter.dateTo(),
                filter.keyword(), (root, query, cb) -> cb.and(
                filter.customerId() == null ? cb.conjunction()
                        : cb.equal(root.get("customerId"), filter.customerId()),
                filter.documentType() == null ? cb.conjunction()
                        : cb.equal(root.get("documentType"), filter.documentType()))), pageable);
        Map<Long, PartnerInfo> customers = access.partners(page.getContent().stream()
                .map(SalesReturn::getCustomerId).toList());
        return page.map(document -> toResponse(document, customers.get(document.getCustomerId()), false));
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Dòng còn trả / giảm giá được của đơn bán đã ghi sổ {@code orderId} (cùng chi nhánh, khách), chia theo lô. */
    @Transactional(readOnly = true)
    public List<ReturnableSalesLineResponse> returnableLines(CurrentUser actor, Long branchId, Long customerId,
                                                             Long orderId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        SalesOrder order = postedOrder(branch, customerId, orderId);
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(SalesOrderLine::getProductId).toList());
        List<ReturnableSalesLineResponse> rows = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            if (line.returnableQuantity().signum() <= 0 && line.remainingAmount().signum() <= 0) {
                continue;
            }
            ProductInfo product = products.get(line.getProductId());
            for (LotShare share : lots(line, line.returnableQuantity())) {
                rows.add(new ReturnableSalesLineResponse(order.getId(), order.getDocumentNo(), order.getPostingDate(),
                        order.invoiceLabel(), order.getWarehouseId(), line.getId(), line.isServiceLine(),
                        line.getProductId(), product == null ? null : product.code(),
                        product == null ? null : product.name(), line.getUnitId(), share.lotNo(), share.expiry(),
                        share.quantity().divide(line.getConversionFactor(), 3, RoundingMode.DOWN),
                        line.netUnitPrice(), line.getTaxRate(), line.remainingAmount(), share.serials()));
            }
        }
        return rows;
    }

    @Transactional
    public SalesReturnResponse create(CurrentUser actor, String idempotencyKey, SalesReturnRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, SalesReturnResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            CustomerProfile customer = access.customerOf(branch.companyId(), request.customerId(), "customerId");
            SalesOrder order = postedOrder(branch, customer.id(), request.salesOrderId());
            SalesReturn document = new SalesReturn(branch.companyId(), branch.id(), access.nextNumber(
                    branch.companyId(), branch.id(), request.documentType().name(), request.documentDate()),
                    request.documentType(), customer.id(), order.getId(),
                    request.documentType() == SalesReturnType.TLB ? order.getWarehouseId() : null);
            fill(document, order, request);
            document.assignSalesUser(order.getSalesUserId() != null ? order.getSalesUserId()
                    : customer.salesUserId());
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public SalesReturnResponse update(CurrentUser actor, Long id, SalesReturnRequest request) {
        access.requirePermission(actor, CREATE);
        SalesReturn document = flow.lock(actor, id, spec);
        SalesDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId()) || !document.getCustomerId().equals(request.customerId())
                || document.getDocumentType() != request.documentType()
                || !document.getSalesOrderId().equals(request.salesOrderId())) {
            throw BusinessException.invalidField("customerId", "Không đổi loại, chi nhánh, khách, đơn bán sau khi lập.");
        }
        BranchRef branch = access.usableBranch(actor, document.getBranchId());
        fill(document, postedOrder(branch, document.getCustomerId(), document.getSalesOrderId()), request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public SalesReturnResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public SalesReturnResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public SalesReturnResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public SalesReturnResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    /**
     * Đổi hàng (BR-SAL-33): từ phiếu trả hàng đã ghi sổ, lập đơn bán nháp cho cùng khách, cùng kho, cùng hàng, số lượng
     * và giá, chiết khấu, thuế như đơn gốc (lô chọn lại theo FEFO); đơn mới đi qua duyệt / ghi sổ như đơn thường.
     */
    @Transactional
    public SalesOrderResponse createExchangeOrder(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/create-exchange-order", id,
                SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, CREATE);
                    SalesReturn document = flow.lock(actor, id, spec);
                    if (!document.isGoodsReturn() || document.getStatus() != SalesDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chỉ đổi hàng từ phiếu trả hàng "
                                + "đã ghi sổ.");
                    }
                    SalesOrder source = orderRepository.findById(document.getSalesOrderId())
                            .orElseThrow(BusinessException::notFound);
                    Map<Long, BigDecimal> quantityByLine = new LinkedHashMap<>();
                    document.getLines().forEach(line -> quantityByLine.merge(line.getOrderLineId(),
                            line.getQuantity(), BigDecimal::add));
                    List<SalesLineRequest> lines = new ArrayList<>();
                    quantityByLine.forEach((orderLineId, quantity) -> {
                        SalesOrderLine line = source.line(orderLineId, "lines");
                        lines.add(new SalesLineRequest(line.getProductId(), line.getUnitId(), quantity,
                                line.getUnitPrice(), line.getDiscountPercent(), line.getTaxRateId(),
                                line.isPromotional(), null, null, null, List.of(),
                                "Đổi hàng theo " + document.getDocumentNo()));
                    });
                    SalesOrder order = orders.createDocument(actor, new SalesOrderRequest(document.getBranchId(),
                            document.getCustomerId(), document.getWarehouseId(), document.getPostingDate(), null,
                            source.getContactName(), source.getDeliveryAddress(), source.getReceiverName(),
                            source.getReceiverPhone(), null, source.getPaymentTermDays(), false,
                            "Đổi hàng theo " + document.getDocumentNo(), lines, List.of(), null), document.getId());
                    return orders.toResponse(order, true);
                });
    }

    SalesDocumentFlow.Spec<SalesReturn, SalesReturnResponse> spec() {
        return spec;
    }

    private void fill(SalesReturn document, SalesOrder order, SalesReturnRequest request) {
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(SalesOrderLine::getProductId).toList());
        List<SalesReturnLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            SalesReturnLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            if (!seen.add(line.orderLineId() + "/" + line.lotNo())) {
                throw BusinessException.invalidField(field, "Mỗi dòng đơn (và lô) chỉ một lần.");
            }
            SalesOrderLine orderLine = order.line(line.orderLineId(), field + ".orderLineId");
            lines.add(new SalesReturnLine(i + 1, request.documentType() == SalesReturnType.GGB
                    ? discountLine(orderLine, line, field)
                    : returnLine(orderLine, line, products.get(orderLine.getProductId()), field)));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new SalesReturn.Header(request.documentDate(), postingDate,
                SalesAccess.trim(request.description())), lines);
    }

    /**
     * Dòng trả hàng: đúng lô / serial đã xuất của dòng đơn; không vượt số còn trả được của dòng và của lô (đã xuất − đã
     * trả).
     */
    private SalesReturnLine.Values returnLine(SalesOrderLine orderLine, SalesReturnLineRequest line,
                                              ProductInfo product, String field) {
        if (line.quantity() == null) {
            throw BusinessException.invalidField(field + ".quantity", "Phải nhập số lượng trả.");
        }
        if (orderLine.isServiceLine()) {
            throw BusinessException.invalidField(field + ".orderLineId", "Dòng dịch vụ không trả hàng được; dùng giảm "
                    + "giá hàng bán.");
        }
        BigDecimal base = SalesAccess.baseQuantity(line.quantity(), orderLine.getConversionFactor(),
                field + ".quantity");
        if (base.compareTo(orderLine.returnableQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": chỉ còn trả được "
                    + plain(orderLine.returnableQuantity()) + " (đơn vị cơ sở).");
        }
        String lotNo = SalesAccess.trim(line.lotNo());
        List<SalesOrderItem> lotItems = orderLine.getItems().stream()
                .filter(item -> Objects.equals(item.getLotNo(), lotNo)).toList();
        if (lotItems.isEmpty()) {
            throw BusinessException.invalidField(field + ".lotNo", "Lô " + (lotNo == null ? "(trống)" : lotNo)
                    + " không có trong hàng đã xuất của dòng đơn.");
        }
        BigDecimal lotIssued = lotItems.stream().map(SalesOrderItem::getBaseQuantity).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal lotLeft = lotIssued.subtract(repository.returnedForLot(orderLine.getId(), lotNo));
        if (base.compareTo(lotLeft) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": lô " + lotNo + " chỉ còn trả "
                    + "được " + plain(lotLeft) + " (đơn vị cơ sở).");
        }
        List<String> serials = serials(product, lotItems, line, base, field);
        return new SalesReturnLine.Values(orderLine.getId(), orderLine.getProductId(), orderLine.getUnitId(),
                orderLine.getConversionFactor(), line.quantity(), base, lotNo, lotItems.get(0).getExpiryDate(),
                line.qualityStatus() != null ? line.qualityStatus() : QualityStatus.QUARANTINE,
                orderLine.netUnitPrice(), null, orderLine.getTaxRateId(), orderLine.getTaxRate(),
                SalesAccess.trim(line.description()), serials);
    }

    /** Dòng giảm giá: theo dòng đơn, không vượt giá trị còn lại (BR-SAL-36). */
    private static SalesReturnLine.Values discountLine(SalesOrderLine orderLine, SalesReturnLineRequest line,
                                                       String field) {
        if (line.amount() == null) {
            throw BusinessException.invalidField(field + ".amount", "Phải nhập số tiền giảm giá.");
        }
        BigDecimal amount = line.amount().setScale(0, RoundingMode.HALF_UP);
        if (amount.compareTo(orderLine.remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + orderLine.getLineNo()
                    + " của đơn bán chỉ còn giá trị " + plain(orderLine.remainingAmount()) + ".");
        }
        return new SalesReturnLine.Values(orderLine.getId(), orderLine.getProductId(), orderLine.getUnitId(),
                orderLine.getConversionFactor(), null, null, null, null, null, null, amount,
                orderLine.getTaxRateId(), orderLine.getTaxRate(), SalesAccess.trim(line.description()), List.of());
    }

    /** Hàng quản lý serial: đúng số lượng, thuộc serial đã xuất của lô. */
    private static List<String> serials(ProductInfo product, List<SalesOrderItem> lotItems,
                                        SalesReturnLineRequest line, BigDecimal base, String field) {
        List<String> serials = line.serialNumbers() == null ? List.of()
                : line.serialNumbers().stream().map(String::trim).toList();
        if (product == null || !product.serialTracked()) {
            if (!serials.isEmpty()) {
                throw BusinessException.invalidField(field + ".serialNumbers", "Hàng không quản lý serial.");
            }
            return serials;
        }
        if (new HashSet<>(serials).size() != serials.size()
                || base.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Số serial phải bằng số lượng trả và không "
                    + "trùng.");
        }
        Set<String> issued = new HashSet<>();
        lotItems.forEach(item -> issued.add(item.getSerialNo()));
        if (!issued.containsAll(serials)) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Serial không thuộc hàng đã xuất.");
        }
        return serials;
    }

    private SalesOrder postedOrder(BranchRef branch, Long customerId, Long orderId) {
        return orderRepository.findById(orderId)
                .filter(found -> found.getCompanyId().equals(branch.companyId())
                        && found.getBranchId().equals(branch.id()) && found.getCustomerId().equals(customerId)
                        && found.getStatus() == SalesDocumentStatus.POSTED)
                .orElseThrow(() -> BusinessException.invalidField("salesOrderId", "Đơn bán không tồn tại, khác chi "
                        + "nhánh / khách hoặc chưa ghi sổ."));
    }

    /** Phần còn trả của dòng chia theo lô đã xuất (đã xuất − đã trả của lô), tối đa {@code limit}. */
    private List<LotShare> lots(SalesOrderLine line, BigDecimal limit) {
        if (line.isServiceLine()) {
            return List.of(new LotShare(null, null, BigDecimal.ZERO, List.of()));
        }
        Map<String, List<SalesOrderItem>> byLot = new LinkedHashMap<>();
        line.getItems().forEach(item -> byLot.computeIfAbsent(Objects.toString(item.getLotNo(), ""),
                ignored -> new ArrayList<>()).add(item));
        List<LotShare> shares = new ArrayList<>();
        BigDecimal left = limit;
        for (List<SalesOrderItem> items : byLot.values()) {
            String lotNo = items.get(0).getLotNo();
            BigDecimal issued = items.stream().map(SalesOrderItem::getBaseQuantity).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            BigDecimal available = issued.subtract(repository.returnedForLot(line.getId(), lotNo)).min(left);
            if (available.signum() > 0) {
                shares.add(new LotShare(lotNo, items.get(0).getExpiryDate(), available, items.stream()
                        .map(SalesOrderItem::getSerialNo).filter(Objects::nonNull).toList()));
                left = left.subtract(available);
            }
        }
        if (shares.isEmpty() && line.remainingAmount().signum() > 0) {
            shares.add(new LotShare(null, null, BigDecimal.ZERO, List.of()));
        }
        return shares;
    }

    private record LotShare(String lotNo, LocalDate expiry, BigDecimal quantity, List<String> serials) {
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    SalesReturnResponse toResponse(SalesReturn document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getCustomerId())).get(document.getCustomerId()),
                detailed);
    }

    private SalesReturnResponse toResponse(SalesReturn document, PartnerInfo customer, boolean detailed) {
        List<SalesReturnLineResponse> lines = List.of();
        SalesOrder order = null;
        String inventoryNo = null;
        if (detailed) {
            order = orderRepository.findById(document.getSalesOrderId()).orElse(null);
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(SalesReturnLine::getProductId).toList());
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                return new SalesReturnLineResponse(line.getId(), line.getLineNo(), line.getOrderLineId(),
                        line.getProductId(), product == null ? null : product.code(),
                        product == null ? null : product.name(), line.getUnitId(), line.getQuantity(),
                        line.getBaseQuantity(), line.getLotNo(), line.getExpiryDate(), line.getQualityStatus(),
                        line.getUnitPrice(), line.getAmount(), line.getTaxRate(), line.getTaxAmount(),
                        line.getDescription(), List.copyOf(line.getSerialNumbers()));
            }).toList();
            if (document.getInventoryDocumentId() != null) {
                inventoryNo = stockIn.documentNumbers(List.of(document.getInventoryDocumentId()))
                        .get(document.getInventoryDocumentId());
            }
        }
        return new SalesReturnResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentType(), document.getDocumentDate(),
                document.getPostingDate(), document.getCustomerId(), customer == null ? null : customer.code(),
                customer == null ? null : customer.name(), document.getSalesOrderId(),
                order == null ? null : order.getDocumentNo(), order == null ? null : order.invoiceLabel(),
                document.getWarehouseId(), document.getInventoryDocumentId(), inventoryNo, document.getDescription(),
                document.getAmount(), document.getTaxAmount(), document.getTotalAmount(), document.getStatus(),
                document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(),
                document.getReverseDate(), document.getReason(), document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(document.typeCode(), document.getId()) : List.of());
    }
}
