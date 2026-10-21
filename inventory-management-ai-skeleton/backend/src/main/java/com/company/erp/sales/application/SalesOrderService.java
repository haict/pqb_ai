package com.company.erp.sales.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.InventoryReservationService;
import com.company.erp.inventory.application.InventoryReservationService.ReservationLine;
import com.company.erp.inventory.application.InventoryReservationService.ReservationView;
import com.company.erp.inventory.application.StockAvailabilityService;
import com.company.erp.inventory.application.StockAvailabilityService.Availability;
import com.company.erp.inventory.application.StockOutService;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductDocumentInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.sales.application.dto.PaymentTermRequest;
import com.company.erp.sales.application.dto.PriceSuggestionResponse;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.SalesLineRequest;
import com.company.erp.sales.application.dto.SalesLineResponse;
import com.company.erp.sales.application.dto.SalesOrderRequest;
import com.company.erp.sales.application.dto.SalesOrderResponse;
import com.company.erp.sales.domain.SalesDocument;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.domain.SalesPricedLine.PricedValues;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.OrganizationQueryService.UserRef;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đơn bán (DBH, BR-SAL-10..14, D-54): lập theo giá ưu tiên, kiểm tra tồn khả dụng (cảnh báo, BR-SAL-12), ghi nhận lý
 * do cần thẩm quyền (giá dưới sàn, chiết khấu vượt ngưỡng, vượt hạn mức tín dụng / nợ quá hạn; BR-SAL-03, 14), giữ
 * hàng ngay trên đơn nháp khi lưu hoặc theo lệnh (BR-SAL-13, D-37, D-61), nhả khi hủy. Không qua duyệt (D-61): ghi sổ,
 * đảo, giao hàng, hóa đơn điện tử ở {@link SalesOrderPostingService}. Thứ tự khóa: đơn bán → tổng (hàng, kho) theo thứ tự → giữ chỗ (như ghi sổ kho);
 * READ COMMITTED khi giữ / nhả hàng.
 */
@Service
public class SalesOrderService {

    static final String PATH = "/api/v1/sales-orders";
    static final String VIEW = "sales-order:view";
    static final String CREATE = "sales-order:create";
    static final String OVERRIDE_PRICE = "sales-order:override-price";
    static final String OVERRIDE_CREDIT = "sales-order:override-credit";
    static final String ENTITY = "SALES_ORDER";
    static final String RESERVATION_DAYS = "sales.reservation-days";
    private static final Set<SalesDocumentStatus> NOT_POSTED = EnumSet.of(SalesDocumentStatus.DRAFT,
            SalesDocumentStatus.SUBMITTED, SalesDocumentStatus.REJECTED, SalesDocumentStatus.APPROVED);

    public record OrderFilter(Long companyId, Long branchId, SalesDocumentStatus status, Long customerId,
                              LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final SalesOrderRepository repository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final SalesPricing pricing;
    private final ApprovalService approvalService;
    private final InventoryReservationService reservations;
    private final StockAvailabilityService stockAvailability;
    private final StockOutService stockOut;
    private final SalesOrderFacts facts;
    private final SalesReceiptSourceService receiptSources;
    private final ParameterService parameters;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final SalesDocumentFlow.Spec<SalesOrder, SalesOrderResponse> spec;

    @SuppressWarnings("java:S107")
    public SalesOrderService(SalesOrderRepository repository, SalesDocumentFlow flow, SalesAccess access,
                             SalesPricing pricing, ApprovalService approvalService,
                             InventoryReservationService reservations, StockAvailabilityService stockAvailability,
                             StockOutService stockOut, SalesOrderFacts facts,
                             SalesReceiptSourceService receiptSources, ParameterService parameters,
                             BusinessCalendar calendar, Clock clock) {
        this.repository = repository;
        this.flow = flow;
        this.access = access;
        this.pricing = pricing;
        this.approvalService = approvalService;
        this.reservations = reservations;
        this.stockAvailability = stockAvailability;
        this.stockOut = stockOut;
        this.facts = facts;
        this.receiptSources = receiptSources;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
        this.spec = new SalesDocumentFlow.Spec<>(PATH, VIEW, CREATE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), SalesOrderResponse.class,
                this::releaseReservations);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> search(CurrentUser actor, OrderFilter filter, Pageable pageable) {
        Page<SalesOrder> page = flow.search(actor, VIEW, repository, new SalesDocumentFlow.Filter<SalesOrder>(
                filter.companyId(), filter.branchId(), filter.status(), filter.dateFrom(), filter.dateTo(),
                filter.keyword(), (root, query, cb) -> filter.customerId() == null ? cb.conjunction()
                : cb.equal(root.get("customerId"), filter.customerId())), pageable);
        Map<Long, PartnerInfo> customers = access.partners(page.getContent().stream()
                .map(SalesOrder::getCustomerId).toList());
        Map<Long, UserRef> users = access.users(page.getContent().stream().map(SalesOrder::getSalesUserId).toList());
        Map<Long, WarehouseInfo> warehouses = access.warehouses(page.getContent().stream()
                .map(SalesOrder::getWarehouseId).toList());
        Map<Long, String> inventoryNumbers = inventoryNumbers(page.getContent());
        return page.map(document -> toResponse(document, customers.get(document.getCustomerId()),
                users.get(document.getSalesUserId()), warehouses.get(document.getWarehouseId()),
                inventoryNumbers.get(document.getInventoryDocumentId()), false));
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Giá đề xuất cho một dòng (BR-SAL-02). */
    @Transactional(readOnly = true)
    public PriceSuggestionResponse price(CurrentUser actor, Long customerId, Long productId, Long unitId,
                                         BigDecimal quantity, LocalDate date) {
        access.requirePermission(actor, VIEW);
        return pricing.suggest(actor, customerId, productId, unitId, quantity, date == null ? calendar.today() : date);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse create(CurrentUser actor, String idempotencyKey, SalesOrderRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, SalesOrderResponse.class,
                () -> toResponse(createDocument(actor, request, null), true));
    }

    /** Lập đơn (dùng chung cho lệnh tạo và đổi hàng {@code sourceReturnId}); trả chứng từ đã lưu. */
    SalesOrder createDocument(CurrentUser actor, SalesOrderRequest request, Long sourceReturnId) {
        access.requirePermission(actor, CREATE);
        BranchRef branch = access.usableBranch(actor, request.branchId());
        CustomerProfile customer = access.customer(actor, branch.companyId(), request.customerId(), "customerId");
        SalesOrder document = new SalesOrder(branch.companyId(), branch.id(), access.nextNumber(branch.companyId(),
                branch.id(), SalesOrder.TYPE_CODE, request.documentDate()), customer.id(), sourceReturnId);
        document.assignSalesUser(SalesAccess.salesUser(actor, customer));
        fill(document, customer, request);
        document = repository.saveAndFlush(document);
        reserveIfRequested(document);
        flow.act(actor, document, "CREATE", null, spec);
        return document;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        // Dòng có thể đổi: nhả phần đã giữ theo dòng cũ, giữ lại theo nội dung mới nếu đơn chọn giữ hàng.
        releaseReservations(document);
        repository.saveAndFlush(document);
        reserveIfRequested(document);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    /** Giữ hàng cho dòng hàng hóa của đơn chưa ghi sổ (giữ lại từ đầu); thiếu tồn khả dụng: 422 {@code INSUFFICIENT_STOCK}. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse reserve(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/reserve", id, SalesOrderResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    SalesOrder document = flow.lock(actor, id, spec);
                    document.requirePostable();
                    reservations.releaseAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId());
                    reserveGoods(document);
                    return flow.act(actor, document, "RESERVE", null, spec);
                });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse release(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/release", id, SalesOrderResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    SalesOrder document = flow.lock(actor, id, spec);
                    releaseReservations(document);
                    return flow.act(actor, document, "RELEASE", null, spec);
                });
    }

    SalesDocumentFlow.Spec<SalesOrder, SalesOrderResponse> spec() {
        return spec;
    }

    /**
     * D-61 thay bước duyệt: giá dưới sàn / chiết khấu vượt ngưỡng (BR-SAL-03) cần quyền
     * {@code sales-order:override-price}, vượt hạn mức tín dụng / nợ quá hạn (BR-SAL-14, D-22) cần quyền
     * {@code sales-order:override-credit} để ghi sổ; thiếu quyền: 422 {@code PRICE_BELOW_FLOOR} /
     * {@code CREDIT_LIMIT_EXCEEDED}. Tín dụng tính lại tại thời điểm ghi sổ. Trả ghi chú audit khi dùng quyền vượt.
     */
    String requirePostingAuthority(CurrentUser actor, SalesOrder document) {
        List<String> priceReasons = pricing.priceReasons(document.getCompanyId(), document.getLines());
        List<String> creditReasons = pricing.creditReasons(document.getCompanyId(),
                access.customerProfile(document.getCustomerId()), document.getTotalAmount(), calendar.today());
        List<String> reasons = new ArrayList<>(priceReasons);
        reasons.addAll(creditReasons);
        document.recordApprovalReasons(reasons);
        if (!priceReasons.isEmpty() && !actor.hasPermission(OVERRIDE_PRICE)) {
            throw new BusinessException(ErrorCode.PRICE_BELOW_FLOOR, "Đơn bán " + document.getDocumentNo()
                    + " cần người có quyền ghi sổ giá dưới sàn / chiết khấu vượt ngưỡng. "
                    + String.join(" ", priceReasons));
        }
        if (!creditReasons.isEmpty() && !actor.hasPermission(OVERRIDE_CREDIT)) {
            throw new BusinessException(ErrorCode.CREDIT_LIMIT_EXCEEDED, "Đơn bán " + document.getDocumentNo()
                    + " cần người có quyền ghi sổ vượt hạn mức tín dụng. " + String.join(" ", creditReasons));
        }
        return reasons.isEmpty() ? null : "Ghi sổ vượt thẩm quyền: " + String.join(" ", reasons);
    }

    private void reserveIfRequested(SalesOrder document) {
        if (document.isReserveStock()) {
            reserveGoods(document);
        }
    }

    private void releaseReservations(SalesOrder document) {
        reservations.releaseAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId());
        document.markReserved(null);
    }

    /** Giữ hàng cho dòng hàng hóa tại kho của đơn, hết hạn sau {@code sales.reservation-days}. */
    private void reserveGoods(SalesOrder document) {
        List<ReservationLine> lines = document.goodsLines().stream()
                .map(line -> new ReservationLine(line.getId(), line.getProductId(), document.getWarehouseId(),
                        line.getBaseQuantity()))
                .toList();
        if (lines.isEmpty()) {
            return;
        }
        Instant until = Instant.now(clock).plus(Duration.ofDays(parameters.integerValue(document.getCompanyId(),
                RESERVATION_DAYS)));
        reservations.reserveAll(document.getCompanyId(), SalesOrder.TYPE_CODE, document.getId(), lines, until);
        document.markReserved(until);
    }

    private Map<Long, BigDecimal> reservedByLine(SalesOrder document) {
        Map<Long, BigDecimal> reserved = new HashMap<>();
        if (document.getId() != null && SalesDocument.POSTABLE.contains(document.getStatus())) {
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
            String field = "lines[" + i + "]";
            PricedValues values = pricing.line(document.getCompanyId(), customer, line, request.documentDate(), field);
            ProductInfo product = products.get(line.productId());
            boolean service = product != null && !product.stockTracked();
            String lotNo = service ? null : SalesAccess.trim(line.lotNo());
            List<String> serials = service ? List.of() : serials(product, line, values.baseQuantity(), field);
            lines.add(new SalesOrderLine(i + 1, new SalesOrderLine.Values(values, service, line.expectedDate(),
                    lotNo, service ? null : SalesAccess.trim(line.lotOverrideReason()), serials,
                    service ? null : receiptSources.resolve(document.getCompanyId(), warehouse.id(), line, lotNo,
                            serials, field))));
        }
        List<SalesOrder.PaymentTermValues> terms = request.paymentTerms() == null ? List.of()
                : request.paymentTerms().stream().map(SalesOrderService::term).toList();
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new SalesOrder.Header(request.documentDate(), postingDate,
                SalesAccess.trim(request.contactName()), SalesAccess.trim(request.deliveryAddress()),
                SalesAccess.trim(request.receiverName()), SalesAccess.trim(request.receiverPhone()),
                request.expectedDate(),
                request.paymentTermDays() != null ? request.paymentTermDays() : customer.paymentTermDays(),
                Boolean.TRUE.equals(request.reserveStock()), SalesAccess.trim(request.description())),
                warehouse.id(), lines, terms);
        // Lý do cần thẩm quyền ghi nhận ngay khi lưu nháp để người lập thấy trước; tín dụng tính lại khi ghi sổ.
        List<String> reasons = new ArrayList<>(pricing.priceReasons(document.getCompanyId(), document.getLines()));
        reasons.addAll(pricing.creditReasons(document.getCompanyId(), customer, document.getTotalAmount(),
                calendar.today()));
        document.recordApprovalReasons(reasons);
    }

    /** Serial chỉ định: chỉ hàng quản lý serial, không trùng, đủ số lượng theo đơn vị cơ sở (BR-INV-02). */
    private static List<String> serials(ProductInfo product, SalesLineRequest line, BigDecimal base, String field) {
        List<String> serials = line.serialNumbers() == null ? List.of()
                : line.serialNumbers().stream().map(String::trim).toList();
        if (serials.isEmpty()) {
            return serials;
        }
        if (product == null || !product.serialTracked()) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Hàng không quản lý serial.");
        }
        if (new HashSet<>(serials).size() != serials.size()
                || base.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Số serial phải bằng số lượng theo đơn vị "
                    + "cơ sở (" + base.stripTrailingZeros().toPlainString() + ") và không trùng.");
        }
        return serials;
    }

    private static SalesOrder.PaymentTermValues term(PaymentTermRequest request) {
        return new SalesOrder.PaymentTermValues(request.percent(), request.dueBasis(), request.dueDays(),
                SalesAccess.trim(request.description()));
    }

    private Map<Long, String> inventoryNumbers(List<SalesOrder> documents) {
        List<Long> ids = documents.stream().map(SalesOrder::getInventoryDocumentId).filter(Objects::nonNull).toList();
        return ids.isEmpty() ? new HashMap<>() : new HashMap<>(stockOut.documentNumbers(ids));
    }

    /** Khóa chống trùng số hóa đơn điện tử: đơn khác đã dùng thì 409 {@code DUPLICATE_DOCUMENT} (BR-SAL-31). */
    void requireUniqueEinvoice(SalesOrder document, String key) {
        if (key == null) {
            return;
        }
        repository.findEinvoiceDuplicate(document.getCompanyId(), key, document.getId()).ifPresent(other -> {
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "Số hóa đơn điện tử đã dùng ở đơn bán "
                    + other + ".");
        });
    }

    SalesOrderResponse toResponse(SalesOrder document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getCustomerId())).get(document.getCustomerId()),
                document.getSalesUserId() == null ? null
                        : access.users(List.of(document.getSalesUserId())).get(document.getSalesUserId()),
                access.warehouses(List.of(document.getWarehouseId())).get(document.getWarehouseId()),
                inventoryNumbers(List.of(document)).get(document.getInventoryDocumentId()), detailed);
    }

    @SuppressWarnings("java:S107")
    private SalesOrderResponse toResponse(SalesOrder document, PartnerInfo customer, UserRef salesUser,
                                          WarehouseInfo warehouse, String inventoryNo, boolean detailed) {
        List<SalesLineResponse> lines = List.of();
        List<String> warnings = List.of();
        if (detailed) {
            List<Long> productIds = document.getLines().stream().map(SalesOrderLine::getProductId).toList();
            Map<Long, ProductInfo> products = access.products(productIds);
            Map<Long, ProductDocumentInfo> documents = access.productDocuments(productIds);
            boolean open = NOT_POSTED.contains(document.getStatus());
            Map<Long, Availability> stock = open ? stockAvailability.availability(document.getWarehouseId(),
                    document.goodsLines().stream().map(SalesOrderLine::getProductId).distinct().toList()) : Map.of();
            Map<Long, BigDecimal> reserved = reservedByLine(document);
            lines = document.getLines().stream().map(line -> SalesLineMapper.toResponse(line,
                    products.get(line.getProductId()), documents.get(line.getProductId()),
                    stock.containsKey(line.getProductId()) ? stock.get(line.getProductId()).available() : null,
                    reserved.getOrDefault(line.getId(), BigDecimal.ZERO))).toList();
            warnings = open ? availabilityWarnings(document, products, stock, reserved) : List.of();
        }
        return new SalesOrderResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(),
                document.getCustomerId(), customer == null ? null : customer.code(),
                customer == null ? null : customer.name(), customer == null ? null : customer.taxCode(),
                document.getContactName(), document.getDeliveryAddress(), document.getReceiverName(),
                document.getReceiverPhone(), document.getWarehouseId(), warehouse == null ? null : warehouse.code(),
                warehouse == null ? null : warehouse.name(), document.getExpectedDate(), document.getPaymentTermDays(),
                document.getDueDate(), document.isReserveStock(), document.getReservedUntil(),
                document.getSalesUserId(), salesUser == null ? null : salesUser.fullName(),
                document.getSourceReturnId(), document.getCurrencyCode(), document.getDescription(),
                document.getAmount(), document.getDiscountAmount(), document.getTaxAmount(),
                document.getTotalAmount(), document.getApprovalReasons(), document.getEinvoiceSeries(),
                document.getEinvoiceNo(), document.getEinvoiceDate(), document.getTaxAuthorityCode(),
                document.getInventoryDocumentId(), inventoryNo, document.getDeliveryStatus(),
                document.getShipperName(), document.getShippedAt(), document.getReceivedDate(),
                document.getReceivedBy(), document.getSignedDocumentRef(), document.getDeliveryNote(),
                document.getStatus(), document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(),
                document.getReversedAt(), document.getReverseDate(), document.getReason(), document.getCreatedBy(),
                document.getVersion(), warnings, lines,
                detailed ? facts.paymentSchedule(document, calendar.today()) : List.of(),
                detailed ? approvalService.history(SalesOrder.TYPE_CODE, document.getId()) : List.of());
    }

    /** Cảnh báo tồn khả dụng (BR-SAL-12): dòng hàng hóa vượt khả dụng + đang giữ cho đơn (đơn chưa ghi sổ). */
    private static List<String> availabilityWarnings(SalesOrder document, Map<Long, ProductInfo> products,
                                                     Map<Long, Availability> stock, Map<Long, BigDecimal> reserved) {
        List<String> warnings = new ArrayList<>();
        for (SalesOrderLine line : document.goodsLines()) {
            Availability availability = stock.get(line.getProductId());
            BigDecimal covered = (availability == null ? BigDecimal.ZERO : availability.available())
                    .add(reserved.getOrDefault(line.getId(), BigDecimal.ZERO));
            if (line.getBaseQuantity().compareTo(covered) > 0) {
                ProductInfo product = products.get(line.getProductId());
                warnings.add("Dòng " + line.getLineNo() + " (" + (product == null ? "" : product.code())
                        + "): cần " + plain(line.getBaseQuantity()) + ", khả dụng " + plain(covered)
                        + " (đơn vị cơ sở).");
            }
        }
        return warnings;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
