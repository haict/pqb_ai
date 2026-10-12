package com.company.erp.sales.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.EinvoiceReferenceRequest;
import com.company.erp.sales.application.dto.InvoiceableSalesLineResponse;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.SalesInvoiceLineRequest;
import com.company.erp.sales.application.dto.SalesInvoiceLineResponse;
import com.company.erp.sales.application.dto.SalesInvoiceRequest;
import com.company.erp.sales.application.dto.SalesInvoiceResponse;
import com.company.erp.sales.domain.DeliveryStatus;
import com.company.erp.sales.domain.SalesDelivery;
import com.company.erp.sales.domain.SalesDeliveryLine;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesInvoice;
import com.company.erp.sales.domain.SalesInvoiceLine;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.infrastructure.persistence.SalesDeliveryRepository;
import com.company.erp.sales.infrastructure.persistence.SalesInvoiceRepository;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Hóa đơn bán (HDB, BR-SAL-30, 31): lập từ dòng phiếu giao đã xuất kho của cùng khách (nhiều phiếu một hóa đơn) và
 * dòng dịch vụ của đơn bán; giá, chiết khấu, thuế theo đơn bán; cảnh báo khi lập trước khi khách nhận hàng; ghi số
 * hóa đơn điện tử từ MISA. Ghi sổ, đảo ở {@link SalesInvoicePostingService}.
 */
@Service
public class SalesInvoiceService {

    static final String PATH = "/api/v1/sales-invoices";
    static final String VIEW = "sales-invoice:view";
    static final String CREATE = "sales-invoice:create";
    static final String APPROVE = "sales-invoice:approve";
    static final String ENTITY = "SALES_INVOICE";

    public record InvoiceFilter(Long companyId, Long branchId, SalesDocumentStatus status, Long customerId,
                                LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    /** Nguồn đã tra: phiếu giao theo id dòng giao, đơn bán theo id dòng đơn. */
    record Sources(Map<Long, SalesDelivery> deliveriesByLine, Map<Long, SalesOrder> ordersByLine) {
    }

    private final SalesInvoiceRepository repository;
    private final SalesDeliveryRepository deliveryRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final ApprovalService approvalService;
    private final SalesDocumentFlow.Spec<SalesInvoice, SalesInvoiceResponse> spec;

    public SalesInvoiceService(SalesInvoiceRepository repository, SalesDeliveryRepository deliveryRepository,
                               SalesOrderRepository orderRepository, SalesDocumentFlow flow, SalesAccess access,
                               ApprovalService approvalService) {
        this.repository = repository;
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.spec = new SalesDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), SalesInvoiceResponse.class, null, null,
                document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<SalesInvoiceResponse> search(CurrentUser actor, InvoiceFilter filter, Pageable pageable) {
        Page<SalesInvoice> page = flow.search(actor, VIEW, repository, new SalesDocumentFlow.Filter<SalesInvoice>(
                filter.companyId(), filter.branchId(), filter.status(), filter.dateFrom(), filter.dateTo(),
                filter.keyword(), filter.customerId() == null ? null
                : (root, query, cb) -> cb.equal(root.get("customerId"), filter.customerId())), pageable);
        Map<Long, PartnerInfo> customers = access.partners(page.getContent().stream()
                .map(SalesInvoice::getCustomerId).toList());
        return page.map(document -> toResponse(document, customers.get(document.getCustomerId()), false));
    }

    @Transactional(readOnly = true)
    public SalesInvoiceResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Dòng còn xuất hóa đơn được của khách trong chi nhánh. */
    @Transactional(readOnly = true)
    public List<InvoiceableSalesLineResponse> invoiceableLines(CurrentUser actor, Long branchId, Long customerId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        List<SalesDelivery> deliveries = deliveryRepository.findPostedByCustomer(branch.companyId(), branch.id(),
                customerId);
        List<SalesOrder> orders = orderRepository.findApprovedByCustomer(branch.companyId(), branch.id(), customerId);
        Map<Long, SalesOrder> ordersById = new HashMap<>();
        orders.forEach(order -> ordersById.put(order.getId(), order));
        orderRepository.findAllById(deliveries.stream().map(SalesDelivery::getOrderId).distinct().toList())
                .forEach(order -> ordersById.put(order.getId(), order));
        Map<Long, ProductInfo> products = access.products(ordersById.values().stream()
                .flatMap(order -> order.getLines().stream()).map(SalesOrderLine::getProductId).toList());
        List<InvoiceableSalesLineResponse> rows = new ArrayList<>();
        for (SalesDelivery delivery : deliveries) {
            SalesOrder order = ordersById.get(delivery.getOrderId());
            for (SalesDeliveryLine line : delivery.getLines()) {
                if (line.uninvoicedQuantity().signum() > 0) {
                    rows.add(row(delivery, line.getId(), order, order.line(line.getOrderLineId()),
                            products.get(line.getProductId()), line.uninvoicedQuantity()));
                }
            }
        }
        for (SalesOrder order : orders) {
            for (SalesOrderLine line : order.getLines()) {
                BigDecimal remaining = serviceRemaining(line);
                if (line.isServiceLine() && remaining.signum() > 0) {
                    rows.add(row(null, null, order, line, products.get(line.getProductId()), remaining));
                }
            }
        }
        return rows;
    }

    @Transactional
    public SalesInvoiceResponse create(CurrentUser actor, String idempotencyKey, SalesInvoiceRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, SalesInvoiceResponse.class,
                () -> toResponse(createDocument(actor, request), true));
    }

    /** Lập hóa đơn (dùng chung cho lệnh tạo và bán nhanh); trả chứng từ đã lưu. */
    SalesInvoice createDocument(CurrentUser actor, SalesInvoiceRequest request) {
        access.requirePermission(actor, CREATE);
        BranchRef branch = access.usableBranch(actor, request.branchId());
        CustomerProfile customer = access.customerOf(branch.companyId(), request.customerId(), "customerId");
        SalesInvoice document = new SalesInvoice(branch.companyId(), branch.id(), access.nextNumber(
                branch.companyId(), branch.id(), SalesInvoice.TYPE_CODE, request.documentDate()), customer.id());
        fill(document, customer, request);
        document = repository.saveAndFlush(document);
        flow.act(actor, document, "CREATE", null, spec);
        return document;
    }

    @Transactional
    public SalesInvoiceResponse update(CurrentUser actor, Long id, SalesInvoiceRequest request) {
        access.requirePermission(actor, CREATE);
        SalesInvoice document = flow.lock(actor, id, spec);
        SalesDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())
                || !document.getCustomerId().equals(request.customerId())) {
            throw BusinessException.invalidField("customerId", "Không đổi chi nhánh, khách hàng sau khi lập.");
        }
        fill(document, access.customerOf(document.getCompanyId(), document.getCustomerId(), "customerId"), request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public SalesInvoiceResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public SalesInvoiceResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public SalesInvoiceResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public SalesInvoiceResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    /** Ghi số hóa đơn điện tử đã phát hành trên MISA (BR-SAL-31); trùng ký hiệu + số: 409 DUPLICATE_DOCUMENT. */
    @Transactional
    public SalesInvoiceResponse recordEinvoice(CurrentUser actor, Long id, String key,
                                               EinvoiceReferenceRequest request) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/einvoice-reference", request,
                SalesInvoiceResponse.class, () -> {
                    access.requirePermission(actor, CREATE);
                    SalesInvoice document = flow.lock(actor, id, spec);
                    requireUniqueEinvoice(document, SalesInvoice.einvoiceKey(request.series(), request.number()));
                    document.recordEinvoice(new SalesInvoice.EinvoiceReference(request.series(), request.number(),
                            request.date(), SalesAccess.trim(request.taxAuthorityCode())));
                    return flow.act(actor, document, "EINVOICE_REFERENCE", document.invoiceLabel(), spec);
                });
    }

    SalesDocumentFlow.Spec<SalesInvoice, SalesInvoiceResponse> spec() {
        return spec;
    }

    void requireUniqueEinvoice(SalesInvoice document) {
        requireUniqueEinvoice(document, document.getEinvoiceKey());
    }

    /** Kiểm tra trước khi đổi chứng từ (truy vấn tự flush thay đổi đang chờ, sẽ vướng ràng buộc unique trước). */
    private void requireUniqueEinvoice(SalesInvoice document, String key) {
        if (key == null) {
            return;
        }
        repository.findEinvoiceDuplicate(document.getCompanyId(), key, document.getId())
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "Số hóa đơn điện tử " + key
                            + " đã gắn với chứng từ " + other + ".");
                });
    }

    private void fill(SalesInvoice document, CustomerProfile customer, SalesInvoiceRequest request) {
        Sources sources = sources(document, request);
        List<SalesInvoiceLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            SalesInvoiceLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            if (!seen.add(line.deliveryLineId() + "/" + line.orderLineId())) {
                throw BusinessException.invalidField(field, "Mỗi dòng nguồn chỉ một lần trên hóa đơn.");
            }
            lines.add(line.deliveryLineId() != null ? goodsLine(document, sources, line, i + 1, field)
                    : serviceLine(document, sources, line, i + 1, field));
        }
        LocalDate invoiceDate = request.invoiceDate() != null ? request.invoiceDate() : request.documentDate();
        LocalDate dueDate = request.dueDate() != null ? request.dueDate()
                : invoiceDate.plusDays(paymentTerm(sources, customer));
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new SalesInvoice.Header(request.documentDate(), postingDate, invoiceDate, dueDate,
                Objects.requireNonNullElse(SalesAccess.trim(request.buyerName()), customer.name()),
                Objects.requireNonNullElse(SalesAccess.trim(request.buyerTaxCode()), Objects.toString(
                        customer.taxCode(), "")),
                Objects.requireNonNullElse(SalesAccess.trim(request.buyerAddress()), Objects.toString(
                        customer.address(), "")),
                SalesAccess.trim(request.description())), lines);
        document.assignSalesUser(sources.ordersByLine().values().stream().map(SalesOrder::getSalesUserId)
                .filter(Objects::nonNull).findFirst().orElse(customer.salesUserId()));
    }

    /** Dòng hàng hóa: dòng phiếu giao đã xuất kho cùng chi nhánh, khách; không vượt số chưa xuất hóa đơn. */
    private SalesInvoiceLine goodsLine(SalesInvoice document, Sources sources, SalesInvoiceLineRequest line,
                                       int lineNo, String field) {
        SalesDelivery delivery = sources.deliveriesByLine().get(line.deliveryLineId());
        if (delivery == null || !delivery.getBranchId().equals(document.getBranchId())
                || !delivery.getCustomerId().equals(document.getCustomerId())
                || delivery.getStatus() != SalesDocumentStatus.POSTED) {
            throw BusinessException.invalidField(field + ".deliveryLineId", "Dòng phiếu giao không tồn tại, khác chi "
                    + "nhánh / khách hoặc chưa xuất kho.");
        }
        SalesDeliveryLine deliveryLine = delivery.line(line.deliveryLineId(), field);
        BigDecimal base = SalesAccess.baseQuantity(line.quantity(), deliveryLine.getConversionFactor(),
                field + ".quantity");
        if (base.compareTo(deliveryLine.uninvoicedQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": phiếu " + delivery.getDocumentNo()
                    + " dòng " + deliveryLine.getLineNo() + " còn " + plain(deliveryLine.uninvoicedQuantity())
                    + " (đơn vị cơ sở) chưa xuất hóa đơn.");
        }
        SalesOrderLine orderLine = sources.ordersByLine().get(deliveryLine.getOrderLineId())
                .line(deliveryLine.getOrderLineId());
        return new SalesInvoiceLine(lineNo, deliveryLine.getId(), orderLine.getId(), orderLine.valuesFor(
                line.quantity(), base, SalesAccess.trim(line.description())));
    }

    /** Dòng dịch vụ: dòng dịch vụ của đơn bán đã duyệt cùng chi nhánh, khách; không vượt số còn lại. */
    private SalesInvoiceLine serviceLine(SalesInvoice document, Sources sources, SalesInvoiceLineRequest line,
                                         int lineNo, String field) {
        SalesOrder order = line.orderLineId() == null ? null : sources.ordersByLine().get(line.orderLineId());
        if (order == null || !order.getBranchId().equals(document.getBranchId())
                || !order.getCustomerId().equals(document.getCustomerId())) {
            throw BusinessException.invalidField(field + ".orderLineId", "Chọn dòng phiếu giao hoặc dòng dịch vụ của "
                    + "đơn bán cùng khách.");
        }
        SalesOrderLine orderLine = order.line(line.orderLineId());
        if (!orderLine.isServiceLine()) {
            throw BusinessException.invalidField(field + ".deliveryLineId", "Hàng hóa xuất hóa đơn theo dòng phiếu "
                    + "giao đã xuất kho.");
        }
        BigDecimal base = SalesAccess.baseQuantity(line.quantity(), orderLine.getConversionFactor(),
                field + ".quantity");
        if (base.compareTo(serviceRemaining(orderLine)) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng dịch vụ còn "
                    + plain(serviceRemaining(orderLine)) + " (đơn vị cơ sở) chưa xuất hóa đơn.");
        }
        return new SalesInvoiceLine(lineNo, null, orderLine.getId(), orderLine.valuesFor(line.quantity(), base,
                SalesAccess.trim(line.description())));
    }

    private static BigDecimal serviceRemaining(SalesOrderLine line) {
        return line.getBaseQuantity().subtract(line.getClosedQuantity()).subtract(line.getInvoicedQuantity())
                .max(BigDecimal.ZERO);
    }

    private Sources sources(SalesInvoice document, SalesInvoiceRequest request) {
        List<Long> deliveryLineIds = request.lines().stream().map(SalesInvoiceLineRequest::deliveryLineId)
                .filter(Objects::nonNull).toList();
        Map<Long, SalesDelivery> deliveriesByLine = new HashMap<>();
        if (!deliveryLineIds.isEmpty()) {
            deliveryRepository.findByLineIds(deliveryLineIds).stream()
                    .filter(delivery -> delivery.getCompanyId().equals(document.getCompanyId()))
                    .forEach(delivery -> delivery.getLines().forEach(line -> deliveriesByLine.put(line.getId(),
                            delivery)));
        }
        Set<Long> orderLineIds = new HashSet<>();
        request.lines().stream().map(SalesInvoiceLineRequest::orderLineId).filter(Objects::nonNull)
                .forEach(orderLineIds::add);
        deliveriesByLine.values().forEach(delivery -> delivery.getLines()
                .forEach(line -> orderLineIds.add(line.getOrderLineId())));
        Map<Long, SalesOrder> ordersByLine = new HashMap<>();
        if (!orderLineIds.isEmpty()) {
            orderRepository.findByLineIds(orderLineIds).stream()
                    .filter(order -> order.getCompanyId().equals(document.getCompanyId()))
                    .forEach(order -> order.getLines().forEach(line -> ordersByLine.put(line.getId(), order)));
        }
        return new Sources(deliveriesByLine, ordersByLine);
    }

    /** Hạn thanh toán: theo đơn bán liên quan đầu tiên, không có thì theo khách, không có thì 0 ngày. */
    private static long paymentTerm(Sources sources, CustomerProfile customer) {
        return sources.ordersByLine().values().stream().map(SalesOrder::getPaymentTermDays).filter(Objects::nonNull)
                .findFirst().orElse(customer.paymentTermDays() == null ? 0 : customer.paymentTermDays());
    }

    private static InvoiceableSalesLineResponse row(SalesDelivery delivery, Long deliveryLineId, SalesOrder order,
                                                    SalesOrderLine line, ProductInfo product, BigDecimal remaining) {
        return new InvoiceableSalesLineResponse(delivery == null ? null : delivery.getId(),
                delivery == null ? null : delivery.getDocumentNo(), delivery == null ? null : delivery.getPostingDate(),
                delivery == null ? null : delivery.getDeliveryStatus(), deliveryLineId, order.getId(),
                order.getDocumentNo(), line.getId(), line.getProductId(), product == null ? null : product.code(),
                product == null ? null : product.name(), line.getUnitId(),
                remaining.divide(line.getConversionFactor(), 3, RoundingMode.DOWN), line.getUnitPrice(),
                line.getDiscountPercent(), line.getTaxRate(), line.isServiceLine());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    SalesInvoiceResponse toResponse(SalesInvoice document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getCustomerId())).get(document.getCustomerId()),
                detailed);
    }

    private SalesInvoiceResponse toResponse(SalesInvoice document, PartnerInfo customer, boolean detailed) {
        List<SalesInvoiceLineResponse> lines = List.of();
        List<String> warnings = List.of();
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(SalesInvoiceLine::getProductId).toList());
            Map<Long, SalesDelivery> deliveries = new HashMap<>();
            List<Long> deliveryLineIds = document.getLines().stream().map(SalesInvoiceLine::getDeliveryLineId)
                    .filter(Objects::nonNull).toList();
            if (!deliveryLineIds.isEmpty()) {
                deliveryRepository.findByLineIds(deliveryLineIds).forEach(delivery -> delivery.getLines()
                        .forEach(line -> deliveries.put(line.getId(), delivery)));
            }
            Map<Long, SalesOrder> orders = new HashMap<>();
            orderRepository.findByLineIds(document.getLines().stream().map(SalesInvoiceLine::getOrderLineId)
                    .toList()).forEach(order -> order.getLines().forEach(line -> orders.put(line.getId(), order)));
            List<String> pending = new ArrayList<>();
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                SalesDelivery delivery = line.getDeliveryLineId() == null ? null
                        : deliveries.get(line.getDeliveryLineId());
                SalesOrder order = orders.get(line.getOrderLineId());
                if (delivery != null && delivery.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
                    pending.add("Dòng " + line.getLineNo() + ": phiếu giao " + delivery.getDocumentNo()
                            + " chưa được khách xác nhận nhận hàng (hóa đơn lập trước khi giao).");
                }
                return new SalesInvoiceLineResponse(line.getId(), line.getLineNo(), line.getDeliveryLineId(),
                        delivery == null ? null : delivery.getDocumentNo(), line.getOrderLineId(),
                        order == null ? null : order.getDocumentNo(), line.getProductId(),
                        product == null ? null : product.code(), product == null ? null : product.name(),
                        line.getUnitId(), line.getQuantity(), line.getBaseQuantity(), line.getUnitPrice(),
                        line.getDiscountPercent(), line.getDiscountAmount(), line.getAmount(), line.getTaxRate(),
                        line.getTaxAmount(), line.isPromotional(), line.getReturnedQuantity(),
                        line.getAdjustedAmount(), line.getDescription());
            }).toList();
            warnings = pending;
        }
        return new SalesInvoiceResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(),
                document.getCustomerId(), customer == null ? null : customer.code(),
                customer == null ? null : customer.name(), document.getBuyerName(), document.getBuyerTaxCode(),
                document.getBuyerAddress(), document.getInvoiceDate(), document.getDueDate(),
                document.getEinvoiceSeries(), document.getEinvoiceNo(), document.getEinvoiceDate(),
                document.getTaxAuthorityCode(), document.getSalesUserId(), document.getDescription(),
                document.getAmount(), document.getDiscountAmount(), document.getTaxAmount(),
                document.getTotalAmount(), document.getStatus(), document.getSubmittedAt(), document.getApprovedAt(),
                document.getPostedAt(), document.getReversedAt(), document.getReverseDate(), document.getReason(),
                document.getCreatedBy(), document.getVersion(), warnings, lines,
                detailed ? approvalService.history(SalesInvoice.TYPE_CODE, document.getId()) : List.of());
    }
}
