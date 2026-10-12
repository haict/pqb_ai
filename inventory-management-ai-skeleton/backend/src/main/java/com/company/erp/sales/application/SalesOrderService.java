package com.company.erp.sales.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.inventory.application.InventoryReservationService;
import com.company.erp.inventory.application.InventoryReservationService.ReservationLine;
import com.company.erp.inventory.application.InventoryReservationService.ReservationView;
import com.company.erp.inventory.application.StockAvailabilityService;
import com.company.erp.inventory.application.StockAvailabilityService.Availability;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.purchasing.application.PurchaseRequisitionService;
import com.company.erp.purchasing.application.PurchaseRequisitionService.SourceRequisitionLine;
import com.company.erp.purchasing.application.PurchaseRequisitionService.SourceRequisitionResult;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.PaymentTermRequest;
import com.company.erp.sales.application.dto.PurchaseRequestResult;
import com.company.erp.sales.application.dto.QuoteToOrderRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.SalesLineRequest;
import com.company.erp.sales.application.dto.SalesLineResponse;
import com.company.erp.sales.application.dto.SalesOrderRequest;
import com.company.erp.sales.application.dto.SalesOrderResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.domain.SalesPricedLine.PricedValues;
import com.company.erp.sales.domain.SalesQuote;
import com.company.erp.sales.domain.SalesQuoteLine;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.sales.infrastructure.persistence.SalesQuoteRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.OrganizationQueryService.UserRef;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đơn bán (DBH, BR-SAL-10..15): lập theo giá ưu tiên, kiểm tra tồn khả dụng (cảnh báo, BR-SAL-12), gửi duyệt (giá dưới
 * sàn, chiết khấu vượt ngưỡng, vượt hạn mức tín dụng / nợ quá hạn phải duyệt; BR-SAL-03, 14), giữ hàng khi duyệt hoặc
 * theo lệnh (BR-SAL-13, D-37), nhả khi hủy / đóng, đề nghị mua phần thiếu (BR-SAL-15), lập từ báo giá được chấp nhận.
 * Thứ tự khóa: đơn bán → tổng (hàng, kho) theo thứ tự → giữ chỗ (như ghi sổ kho); READ COMMITTED khi giữ / nhả hàng.
 */
@Service
public class SalesOrderService {

    static final String PATH = "/api/v1/sales-orders";
    static final String VIEW = "sales-order:view";
    static final String CREATE = "sales-order:create";
    static final String APPROVE = "sales-order:approve";
    static final String CLOSE = "sales-order:close";
    static final String ENTITY = "SALES_ORDER";
    static final String RESERVATION_DAYS = "sales.reservation-days";

    public record OrderFilter(Long companyId, Long branchId, SalesDocumentStatus status, Long customerId,
                              boolean openOnly, LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final SalesOrderRepository repository;
    private final SalesQuoteRepository quoteRepository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final SalesPricing pricing;
    private final ApprovalService approvalService;
    private final InventoryReservationService reservations;
    private final StockAvailabilityService stockAvailability;
    private final PurchaseRequisitionService requisitions;
    private final SalesOrderFacts facts;
    private final ParameterService parameters;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final SalesDocumentFlow.Spec<SalesOrder, SalesOrderResponse> spec;

    @SuppressWarnings("java:S107")
    public SalesOrderService(SalesOrderRepository repository, SalesQuoteRepository quoteRepository,
                             SalesDocumentFlow flow, SalesAccess access, SalesPricing pricing,
                             ApprovalService approvalService, InventoryReservationService reservations,
                             StockAvailabilityService stockAvailability, PurchaseRequisitionService requisitions,
                             SalesOrderFacts facts, ParameterService parameters, BusinessCalendar calendar,
                             Clock clock) {
        this.repository = repository;
        this.quoteRepository = quoteRepository;
        this.flow = flow;
        this.access = access;
        this.pricing = pricing;
        this.approvalService = approvalService;
        this.reservations = reservations;
        this.stockAvailability = stockAvailability;
        this.requisitions = requisitions;
        this.facts = facts;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
        this.spec = new SalesDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), SalesOrderResponse.class,
                this::approvalRequired, null, this::reserveOnApproval,
                document -> reservations.releaseAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId()));
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> search(CurrentUser actor, OrderFilter filter, Pageable pageable) {
        Page<SalesOrder> page = flow.search(actor, VIEW, repository, new SalesDocumentFlow.Filter<SalesOrder>(
                filter.companyId(), filter.branchId(), filter.status(), filter.dateFrom(), filter.dateTo(),
                filter.keyword(), (root, query, cb) -> cb.and(
                filter.customerId() == null ? cb.conjunction()
                        : cb.equal(root.get("customerId"), filter.customerId()),
                filter.openOnly() ? root.get("status").in(SalesOrder.OPEN) : cb.conjunction())), pageable);
        Map<Long, PartnerInfo> customers = access.partners(page.getContent().stream()
                .map(SalesOrder::getCustomerId).toList());
        Map<Long, UserRef> users = access.users(page.getContent().stream().map(SalesOrder::getSalesUserId).toList());
        Map<Long, WarehouseInfo> warehouses = access.warehouses(page.getContent().stream()
                .map(SalesOrder::getWarehouseId).toList());
        return page.map(document -> toResponse(document, customers.get(document.getCustomerId()),
                users.get(document.getSalesUserId()), warehouses.get(document.getWarehouseId()), false));
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public SalesOrderResponse create(CurrentUser actor, String idempotencyKey, SalesOrderRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, SalesOrderResponse.class,
                () -> toResponse(createDocument(actor, request), true));
    }

    /** Lập đơn (dùng chung cho lệnh tạo và bán nhanh); trả chứng từ đã lưu. */
    SalesOrder createDocument(CurrentUser actor, SalesOrderRequest request) {
        access.requirePermission(actor, CREATE);
        BranchRef branch = access.usableBranch(actor, request.branchId());
        CustomerProfile customer = access.customer(actor, branch.companyId(), request.customerId(), "customerId");
        SalesOrder document = new SalesOrder(branch.companyId(), branch.id(), access.nextNumber(branch.companyId(),
                branch.id(), SalesOrder.TYPE_CODE, request.documentDate()), customer.id(), null);
        document.assignSalesUser(SalesQuoteService.salesUser(actor, customer));
        fill(document, customer, request);
        document = repository.saveAndFlush(document);
        flow.act(actor, document, "CREATE", null, spec);
        return document;
    }

    @Transactional
    public SalesOrderResponse update(CurrentUser actor, Long id, SalesOrderRequest request) {
        access.requirePermission(actor, CREATE);
        SalesOrder document = flow.lock(actor, id, spec);
        SalesDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())
                || !document.getCustomerId().equals(request.customerId())) {
            throw BusinessException.invalidField("customerId", "Không đổi chi nhánh, khách hàng sau khi lập.");
        }
        fill(document, access.customer(actor, document.getCompanyId(), document.getCustomerId(), "customerId"),
                request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    /** Lập đơn bán từ báo giá khách đã chấp nhận (BR-SAL-05): giữ nguyên giá, chiết khấu đã báo. */
    @Transactional
    public SalesOrderResponse createFromQuote(CurrentUser actor, Long quoteId, String key, QuoteToOrderRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesQuoteService.PATH + "/" + quoteId + "/create-order", request,
                SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, CREATE);
                    SalesQuote quote = quoteRepository.findByIdForUpdate(quoteId)
                            .filter(found -> access.canAccess(actor, found.getCompanyId(), found.getBranchId())
                                    && access.ownsData(actor, found.getSalesUserId(), found.getCreatedBy()))
                            .orElseThrow(BusinessException::notFound);
                    CustomerProfile customer = access.customer(actor, quote.getCompanyId(), quote.getCustomerId(),
                            "customerId");
                    LocalDate today = calendar.today();
                    SalesOrder order = new SalesOrder(quote.getCompanyId(), quote.getBranchId(), access.nextNumber(
                            quote.getCompanyId(), quote.getBranchId(), SalesOrder.TYPE_CODE, today), customer.id(),
                            quote.getId());
                    order.assignSalesUser(quote.getSalesUserId());
                    WarehouseInfo warehouse = access.issuingWarehouse(quote.getBranchId(), request.warehouseId(),
                            "warehouseId");
                    List<SalesOrderLine> lines = new ArrayList<>();
                    Map<Long, ProductInfo> products = access.products(quote.getLines().stream()
                            .map(SalesQuoteLine::getProductId).toList());
                    for (SalesQuoteLine line : quote.getLines()) {
                        ProductInfo product = products.get(line.getProductId());
                        lines.add(new SalesOrderLine(lines.size() + 1, new SalesOrderLine.Values(line.values(),
                                line.getId(), product != null && !product.stockTracked(), request.expectedDate())));
                    }
                    order.replaceContent(new SalesOrder.Header(today, quote.getContactName(),
                            SalesAccess.trim(request.deliveryAddress()), null, null, request.expectedDate(),
                            customer.paymentTermDays(), Boolean.TRUE.equals(request.reserveStock()),
                            "Theo báo giá " + quote.getDocumentNo()), warehouse.id(), lines, List.of());
                    order = repository.saveAndFlush(order);
                    quote.linkOrder(order.getId());
                    quoteRepository.save(quote);
                    return flow.act(actor, order, "CREATE_FROM_QUOTE", quote.getDocumentNo(), spec);
                });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public SalesOrderResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    /** Đóng phần còn lại có lý do; nhả hàng đang giữ (BR-SAL-13). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse close(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/close", request, SalesOrderResponse.class,
                () -> {
                    access.requirePermission(actor, CLOSE);
                    SalesOrder document = flow.lock(actor, id, spec);
                    reservations.releaseAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId());
                    document.close(actor.id(), Instant.now(clock), request.reason().trim());
                    document.markReserved(null);
                    return flow.act(actor, document, "CLOSE", request.reason(), spec);
                });
    }

    /** Giữ hàng cho phần còn phải xuất (giữ lại từ đầu); thiếu tồn khả dụng: 422 {@code INSUFFICIENT_STOCK}. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse reserve(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/reserve", id, SalesOrderResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    SalesOrder document = flow.lock(actor, id, spec);
                    document.requireOpen();
                    reservations.releaseAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId());
                    reserveOutstanding(document);
                    return flow.act(actor, document, "RESERVE", null, spec);
                });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse release(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/release", id, SalesOrderResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    SalesOrder document = flow.lock(actor, id, spec);
                    reservations.releaseAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId());
                    document.markReserved(null);
                    return flow.act(actor, document, "RELEASE", null, spec);
                });
    }

    /**
     * Đề nghị mua phần thiếu (BR-SAL-15): còn phải xuất − (khả dụng + đang giữ cho đơn) theo đơn vị cơ sở; gửi duyệt
     * theo luồng DNM.
     */
    @Transactional
    public PurchaseRequestResult createPurchaseRequest(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/create-purchase-request", id,
                PurchaseRequestResult.class, () -> {
                    access.requirePermission(actor, CREATE);
                    SalesOrder document = flow.lock(actor, id, spec);
                    document.requireOpen();
                    Map<Long, BigDecimal> shortage = shortages(document);
                    if (shortage.isEmpty()) {
                        throw BusinessException.invalidField("lines", "Đơn bán không thiếu hàng tại kho xuất.");
                    }
                    Map<Long, ProductInfo> products = access.products(shortage.keySet());
                    LocalDate today = calendar.today();
                    SourceRequisitionResult result = requisitions.createFromSalesOrder(actor, document.getBranchId(),
                            today, document.getId(), document.getDocumentNo(), shortage.entrySet().stream()
                                    .map(entry -> new SourceRequisitionLine(entry.getKey(),
                                            products.get(entry.getKey()).baseUnitId(), entry.getValue(),
                                            document.getExpectedDate(), "Đơn bán " + document.getDocumentNo()))
                                    .toList());
                    access.audit(actor, "CREATE_PURCHASE_REQUEST", ENTITY, document.getId(), document.getDocumentNo(),
                            null, null, result.documentNo(), document.getCompanyId(), document.getBranchId());
                    return new PurchaseRequestResult(result.id(), result.documentNo(), shortage.size());
                });
    }

    SalesDocumentFlow.Spec<SalesOrder, SalesOrderResponse> spec() {
        return spec;
    }

    /** Lý do phải duyệt lúc gửi duyệt: giá (BR-SAL-03) và tín dụng (BR-SAL-14); có lý do thì bắt buộc luồng duyệt. */
    private boolean approvalRequired(SalesOrder document) {
        List<String> reasons = new ArrayList<>(pricing.priceReasons(document.getCompanyId(), document.getLines()));
        CustomerProfile customer = access.customerProfile(document.getCustomerId());
        reasons.addAll(pricing.creditReasons(document.getCompanyId(), customer, document.getTotalAmount(),
                calendar.today()));
        document.recordApprovalReasons(reasons);
        return !reasons.isEmpty();
    }

    private void reserveOnApproval(SalesOrder document) {
        if (document.isReserveStock()) {
            reserveOutstanding(document);
        }
    }

    /** Giữ hàng cho dòng hàng hóa còn phải xuất tại kho của đơn, hết hạn sau {@code sales.reservation-days}. */
    private void reserveOutstanding(SalesOrder document) {
        List<ReservationLine> lines = document.openGoodsLines().stream()
                .map(line -> new ReservationLine(line.getId(), line.getProductId(), document.getWarehouseId(),
                        line.outstandingQuantity()))
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        Instant until = Instant.now(clock).plus(Duration.ofDays(parameters.integerValue(document.getCompanyId(),
                RESERVATION_DAYS)));
        reservations.reserveAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId(), lines, until);
        document.markReserved(until);
    }

    /** Phần thiếu theo hàng (đơn vị cơ sở). */
    private Map<Long, BigDecimal> shortages(SalesOrder document) {
        List<SalesOrderLine> open = document.openGoodsLines();
        Map<Long, Availability> stock = stockAvailability.availability(document.getWarehouseId(), open.stream()
                .map(SalesOrderLine::getProductId).distinct().toList());
        Map<Long, BigDecimal> reserved = reservedByLine(document);
        Map<Long, BigDecimal> need = new HashMap<>();
        for (SalesOrderLine line : open) {
            need.merge(line.getProductId(), line.outstandingQuantity().subtract(reserved.getOrDefault(line.getId(),
                    BigDecimal.ZERO)), BigDecimal::add);
        }
        Map<Long, BigDecimal> shortage = new HashMap<>();
        need.forEach((productId, quantity) -> {
            BigDecimal missing = quantity.subtract(stock.get(productId).available());
            if (missing.signum() > 0) {
                shortage.put(productId, missing.setScale(3, RoundingMode.UP));
            }
        });
        return shortage;
    }

    private Map<Long, BigDecimal> reservedByLine(SalesOrder document) {
        Map<Long, BigDecimal> reserved = new HashMap<>();
        if (document.getId() != null) {
            for (ReservationView view : reservations.bySource(document.getCompanyId(), SalesOrder.TYPE_CODE,
                    document.getId())) {
                if (view.sourceLineId() != null) {
                    reserved.merge(view.sourceLineId(), view.remainingQuantity(), BigDecimal::add);
                }
            }
        }
        return reserved;
    }

    private void fill(SalesOrder document, CustomerProfile customer, SalesOrderRequest request) {
        WarehouseInfo warehouse = access.issuingWarehouse(document.getBranchId(), request.warehouseId(),
                "warehouseId");
        List<SalesOrderLine> lines = new ArrayList<>();
        Map<Long, ProductInfo> products = access.products(request.lines().stream().map(SalesLineRequest::productId)
                .toList());
        for (int i = 0; i < request.lines().size(); i++) {
            SalesLineRequest line = request.lines().get(i);
            PricedValues values = pricing.line(document.getCompanyId(), customer, line, request.documentDate(),
                    "lines[" + i + "]");
            ProductInfo product = products.get(line.productId());
            lines.add(new SalesOrderLine(i + 1, new SalesOrderLine.Values(values, null,
                    product != null && !product.stockTracked(), line.expectedDate())));
        }
        List<SalesOrder.PaymentTermValues> terms = request.paymentTerms() == null ? List.of()
                : request.paymentTerms().stream().map(SalesOrderService::term).toList();
        document.replaceContent(new SalesOrder.Header(request.documentDate(), SalesAccess.trim(request.contactName()),
                SalesAccess.trim(request.deliveryAddress()), SalesAccess.trim(request.receiverName()),
                SalesAccess.trim(request.receiverPhone()), request.expectedDate(),
                request.paymentTermDays() != null ? request.paymentTermDays() : customer.paymentTermDays(),
                Boolean.TRUE.equals(request.reserveStock()), SalesAccess.trim(request.description())),
                warehouse.id(), lines, terms);
    }

    private static SalesOrder.PaymentTermValues term(PaymentTermRequest request) {
        return new SalesOrder.PaymentTermValues(request.percent(), request.dueBasis(), request.dueDays(),
                SalesAccess.trim(request.description()));
    }

    SalesOrderResponse toResponse(SalesOrder document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getCustomerId())).get(document.getCustomerId()),
                document.getSalesUserId() == null ? null
                        : access.users(List.of(document.getSalesUserId())).get(document.getSalesUserId()),
                access.warehouses(List.of(document.getWarehouseId())).get(document.getWarehouseId()), detailed);
    }

    private SalesOrderResponse toResponse(SalesOrder document, PartnerInfo customer, UserRef salesUser,
                                          WarehouseInfo warehouse, boolean detailed) {
        List<SalesLineResponse> lines = List.of();
        List<String> warnings = List.of();
        String quoteNo = null;
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(SalesOrderLine::getProductId).toList());
            Map<Long, Availability> stock = stockAvailability.availability(document.getWarehouseId(),
                    document.getLines().stream().filter(line -> !line.isServiceLine())
                            .map(SalesOrderLine::getProductId).distinct().toList());
            Map<Long, BigDecimal> reserved = reservedByLine(document);
            lines = document.getLines().stream().map(line -> SalesLineMapper.toResponse(line,
                    products.get(line.getProductId()), stock.containsKey(line.getProductId())
                            ? stock.get(line.getProductId()).available() : null,
                    reserved.getOrDefault(line.getId(), BigDecimal.ZERO))).toList();
            warnings = availabilityWarnings(document, products, stock, reserved);
            quoteNo = document.getQuoteId() == null ? null
                    : quoteRepository.findById(document.getQuoteId()).map(SalesQuote::getDocumentNo).orElse(null);
        }
        return new SalesOrderResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getCustomerId(),
                customer == null ? null : customer.code(), customer == null ? null : customer.name(),
                document.getQuoteId(), quoteNo, document.getContactName(), document.getDeliveryAddress(),
                document.getReceiverName(), document.getReceiverPhone(), document.getWarehouseId(),
                warehouse == null ? null : warehouse.code(), warehouse == null ? null : warehouse.name(),
                document.getExpectedDate(), document.getPaymentTermDays(), document.isReserveStock(),
                document.getReservedUntil(), document.getSalesUserId(), salesUser == null ? null : salesUser.fullName(),
                document.getCurrencyCode(), document.getDescription(), document.getAmount(),
                document.getDiscountAmount(), document.getTaxAmount(), document.getTotalAmount(),
                document.getApprovalReasons(), document.getStatus(), document.getSubmittedAt(),
                document.getApprovedAt(), document.getClosedAt(), document.getReason(), document.getCreatedBy(),
                document.getVersion(), warnings, lines,
                detailed ? facts.paymentSchedule(document, calendar.today()) : List.of(),
                detailed ? approvalService.history(SalesOrder.TYPE_CODE, document.getId()) : List.of());
    }

    /** Cảnh báo tồn khả dụng (BR-SAL-12): dòng còn phải xuất vượt khả dụng + đang giữ cho đơn. */
    private static List<String> availabilityWarnings(SalesOrder document, Map<Long, ProductInfo> products,
                                                     Map<Long, Availability> stock, Map<Long, BigDecimal> reserved) {
        if (!document.isOpen() && document.getStatus() != SalesDocumentStatus.DRAFT
                && document.getStatus() != SalesDocumentStatus.SUBMITTED
                && document.getStatus() != SalesDocumentStatus.REJECTED) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        for (SalesOrderLine line : document.openGoodsLines()) {
            Availability availability = stock.get(line.getProductId());
            BigDecimal covered = (availability == null ? BigDecimal.ZERO : availability.available())
                    .add(reserved.getOrDefault(line.getId(), BigDecimal.ZERO));
            if (line.outstandingQuantity().compareTo(covered) > 0) {
                ProductInfo product = products.get(line.getProductId());
                warnings.add("Dòng " + line.getLineNo() + " (" + (product == null ? "" : product.code())
                        + "): cần " + plain(line.outstandingQuantity()) + ", khả dụng "
                        + plain(Objects.requireNonNullElse(covered, BigDecimal.ZERO)) + " (đơn vị cơ sở).");
            }
        }
        return warnings;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
