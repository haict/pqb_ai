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
import com.company.erp.sales.application.dto.SalesDeliveryLineRequest;
import com.company.erp.sales.application.dto.SalesDeliveryRequest;
import com.company.erp.sales.application.dto.SalesDeliveryResponse;
import com.company.erp.sales.application.dto.SalesReturnLineRequest;
import com.company.erp.sales.application.dto.SalesReturnLineResponse;
import com.company.erp.sales.application.dto.SalesReturnRequest;
import com.company.erp.sales.application.dto.SalesReturnResponse;
import com.company.erp.sales.domain.SalesDelivery;
import com.company.erp.sales.domain.SalesDeliveryItem;
import com.company.erp.sales.domain.SalesDeliveryLine;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesInvoice;
import com.company.erp.sales.domain.SalesInvoiceLine;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.domain.SalesReturn;
import com.company.erp.sales.domain.SalesReturnLine;
import com.company.erp.sales.domain.SalesReturnType;
import com.company.erp.sales.infrastructure.persistence.SalesDeliveryRepository;
import com.company.erp.sales.infrastructure.persistence.SalesInvoiceRepository;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.sales.infrastructure.persistence.SalesReturnRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Trả hàng bán (TLB, BR-SAL-32) / giảm giá hàng bán (GGB, BR-SAL-36): lập theo dòng phiếu giao (đúng lô, serial đã
 * xuất, không vượt số đã bán trừ số đã trả) hoặc dòng hóa đơn đã ghi sổ; gửi duyệt (duyệt khi có luồng), duyệt, từ
 * chối, hủy; đổi hàng (BR-SAL-33): lập phiếu giao mới liên kết cho phần đã trả. Ghi sổ, đảo ở
 * {@link SalesReturnPostingService}.
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

    /** Nguồn đã tra: hóa đơn (nếu có), phiếu giao theo id dòng giao, đơn bán theo id dòng đơn. */
    private record Sources(SalesInvoice invoice, Map<Long, SalesDelivery> deliveriesByLine,
                           Map<Long, SalesOrder> ordersByLine) {
    }

    private final SalesReturnRepository repository;
    private final SalesInvoiceRepository invoiceRepository;
    private final SalesDeliveryRepository deliveryRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesDeliveryService deliveries;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final ApprovalService approvalService;
    private final StockInService stockIn;
    private final SalesDocumentFlow.Spec<SalesReturn, SalesReturnResponse> spec;

    @SuppressWarnings("java:S107")
    public SalesReturnService(SalesReturnRepository repository, SalesInvoiceRepository invoiceRepository,
                              SalesDeliveryRepository deliveryRepository, SalesOrderRepository orderRepository,
                              SalesDeliveryService deliveries, SalesDocumentFlow flow, SalesAccess access,
                              ApprovalService approvalService, StockInService stockIn) {
        this.repository = repository;
        this.invoiceRepository = invoiceRepository;
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.deliveries = deliveries;
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

    /**
     * Dòng còn trả / giảm giá được của khách trong chi nhánh: theo hóa đơn {@code invoiceId}, hoặc (trống) theo (dòng
     * phiếu giao, lô) còn chưa xuất hóa đơn.
     */
    @Transactional(readOnly = true)
    public List<ReturnableSalesLineResponse> returnableLines(CurrentUser actor, Long branchId, Long customerId,
                                                             Long invoiceId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        List<ReturnableSalesLineResponse> rows = new ArrayList<>();
        if (invoiceId != null) {
            SalesInvoice invoice = postedInvoice(branch, customerId, invoiceId);
            Map<Long, SalesDelivery> byLine = deliveriesByLine(invoice.getLines().stream()
                    .map(SalesInvoiceLine::getDeliveryLineId).filter(Objects::nonNull).toList());
            Map<Long, ProductInfo> products = access.products(invoice.getLines().stream()
                    .map(SalesInvoiceLine::getProductId).toList());
            for (SalesInvoiceLine line : invoice.getLines()) {
                SalesDelivery delivery = line.isGoods() ? byLine.get(line.getDeliveryLineId()) : null;
                SalesDeliveryLine deliveryLine = delivery == null ? null : delivery.line(line.getDeliveryLineId(), "x");
                BigDecimal returnable = deliveryLine == null ? BigDecimal.ZERO
                        : line.returnableQuantity().min(deliveryLine.returnableQuantity());
                if (returnable.signum() <= 0 && line.remainingAmount().signum() <= 0) {
                    continue;
                }
                for (LotShare share : lots(deliveryLine, returnable)) {
                    rows.add(new ReturnableSalesLineResponse(delivery == null ? null : delivery.getId(),
                            delivery == null ? null : delivery.getDocumentNo(), line.getDeliveryLineId(), line.getId(),
                            line.getOrderLineId(), line.getProductId(), code(products, line.getProductId()),
                            name(products, line.getProductId()), line.getUnitId(), share.lotNo(), share.expiry(),
                            share.quantity().divide(line.getConversionFactor(), 3, RoundingMode.DOWN),
                            line.netUnitPrice(), line.getTaxRate(), line.remainingAmount(), share.serials()));
                }
            }
            return rows;
        }
        List<SalesDelivery> posted = deliveryRepository.findPostedByCustomer(branch.companyId(), branch.id(),
                customerId);
        Map<Long, ProductInfo> products = access.products(posted.stream().flatMap(d -> d.getLines().stream())
                .map(SalesDeliveryLine::getProductId).toList());
        Map<Long, SalesOrder> orders = ordersByLine(posted.stream().flatMap(d -> d.getLines().stream())
                .map(SalesDeliveryLine::getOrderLineId).toList());
        for (SalesDelivery delivery : posted) {
            for (SalesDeliveryLine line : delivery.getLines()) {
                BigDecimal returnable = line.uninvoicedQuantity().min(line.returnableQuantity());
                if (returnable.signum() <= 0) {
                    continue;
                }
                SalesOrderLine orderLine = orders.get(line.getOrderLineId()).line(line.getOrderLineId());
                for (LotShare share : lots(line, returnable)) {
                    rows.add(new ReturnableSalesLineResponse(delivery.getId(), delivery.getDocumentNo(), line.getId(),
                            null, line.getOrderLineId(), line.getProductId(), code(products, line.getProductId()),
                            name(products, line.getProductId()), line.getUnitId(), share.lotNo(), share.expiry(),
                            share.quantity().divide(line.getConversionFactor(), 3, RoundingMode.DOWN),
                            orderLine.netUnitPrice(), null, null, share.serials()));
                }
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
            Sources sources = sources(branch, customer.id(), request);
            Long warehouseId = request.documentType() == SalesReturnType.TLB ? warehouse(sources, request) : null;
            SalesReturn document = new SalesReturn(branch.companyId(), branch.id(), access.nextNumber(
                    branch.companyId(), branch.id(), request.documentType().name(), request.documentDate()),
                    request.documentType(), customer.id(), request.salesInvoiceId(), warehouseId);
            fill(document, sources, request);
            document.assignSalesUser(sources.invoice() != null ? sources.invoice().getSalesUserId()
                    : sources.ordersByLine().values().stream().map(SalesOrder::getSalesUserId)
                    .filter(Objects::nonNull).findFirst().orElse(customer.salesUserId()));
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
                || !Objects.equals(document.getSalesInvoiceId(), request.salesInvoiceId())) {
            throw BusinessException.invalidField("customerId", "Không đổi loại, chi nhánh, khách, hóa đơn sau khi lập.");
        }
        Sources sources = sources(access.usableBranch(actor, document.getBranchId()), document.getCustomerId(),
                request);
        if (document.isGoodsReturn() && !document.getWarehouseId().equals(warehouse(sources, request))) {
            throw BusinessException.invalidField("lines", "Các dòng trả hàng phải cùng kho của chứng từ.");
        }
        fill(document, sources, request);
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
     * Đổi hàng (BR-SAL-33): từ phiếu trả hàng đã ghi sổ, lập phiếu giao nháp cho cùng dòng đơn bán và số lượng đã trả
     * (hàng trả lại mở lại phần cần giao của đơn); chênh lệch giá xử lý bằng hóa đơn của phiếu giao mới và giảm trừ của
     * phiếu trả hàng.
     */
    @Transactional
    public SalesDeliveryResponse createExchangeDelivery(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/create-exchange-delivery", id,
                SalesDeliveryResponse.class, () -> {
                    access.requirePermission(actor, CREATE);
                    SalesReturn document = flow.lock(actor, id, spec);
                    if (!document.isGoodsReturn() || document.getStatus() != SalesDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chỉ đổi hàng từ phiếu trả hàng "
                                + "đã ghi sổ.");
                    }
                    Map<Long, SalesOrder> orders = ordersByLine(document.getLines().stream()
                            .map(SalesReturnLine::getOrderLineId).toList());
                    Set<Long> orderIds = new HashSet<>();
                    orders.values().forEach(order -> orderIds.add(order.getId()));
                    if (orderIds.size() != 1) {
                        throw BusinessException.invalidField("lines", "Phiếu trả hàng gồm hàng của nhiều đơn bán; "
                                + "lập phiếu giao đổi hàng cho từng đơn.");
                    }
                    Map<Long, BigDecimal> baseByOrderLine = new LinkedHashMap<>();
                    document.getLines().forEach(line -> baseByOrderLine.merge(line.getOrderLineId(),
                            line.getBaseQuantity(), BigDecimal::add));
                    List<SalesDeliveryLineRequest> lines = new ArrayList<>();
                    baseByOrderLine.forEach((orderLineId, base) -> {
                        SalesOrderLine orderLine = orders.get(orderLineId).line(orderLineId);
                        lines.add(new SalesDeliveryLineRequest(orderLineId, base.divide(
                                orderLine.getConversionFactor(), 3, RoundingMode.DOWN), null, null, List.of(),
                                "Đổi hàng theo " + document.getDocumentNo()));
                    });
                    SalesDelivery delivery = deliveries.createDocument(actor, new SalesDeliveryRequest(
                            orderIds.iterator().next(), document.getPostingDate(), null, null, null, null, null,
                            "Đổi hàng theo " + document.getDocumentNo(), lines, null), document.getId());
                    return deliveries.toResponse(delivery, true);
                });
    }

    SalesDocumentFlow.Spec<SalesReturn, SalesReturnResponse> spec() {
        return spec;
    }

    private void fill(SalesReturn document, Sources sources, SalesReturnRequest request) {
        Map<Long, ProductInfo> products = access.products(productIds(sources));
        List<SalesReturnLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            SalesReturnLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            if (!seen.add(line.invoiceLineId() + "/" + line.deliveryLineId() + "/" + line.lotNo())) {
                throw BusinessException.invalidField(field, "Mỗi dòng nguồn (và lô) chỉ một lần.");
            }
            lines.add(new SalesReturnLine(i + 1, request.documentType() == SalesReturnType.GGB
                    ? discountLine(sources, line, field) : returnLine(sources, line, products, field)));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new SalesReturn.Header(request.documentDate(), postingDate,
                SalesAccess.trim(request.description())), lines);
    }

    /**
     * Dòng trả hàng: đúng lô / serial đã xuất của dòng giao; không vượt số còn trả được của dòng giao (và dòng hóa đơn
     * nếu có) và của lô (đã xuất − đã trả).
     */
    private SalesReturnLine.Values returnLine(Sources sources, SalesReturnLineRequest line,
                                              Map<Long, ProductInfo> products, String field) {
        if (line.quantity() == null) {
            throw BusinessException.invalidField(field + ".quantity", "Phải nhập số lượng trả.");
        }
        SalesInvoiceLine invoiceLine = null;
        Long deliveryLineId = line.deliveryLineId();
        if (sources.invoice() != null) {
            invoiceLine = invoiceLine(sources, line, field);
            if (!invoiceLine.isGoods()) {
                throw BusinessException.invalidField(field + ".invoiceLineId", "Dòng dịch vụ không trả hàng được; dùng "
                        + "giảm giá hàng bán.");
            }
            deliveryLineId = invoiceLine.getDeliveryLineId();
        }
        SalesDelivery delivery = deliveryLineId == null ? null : sources.deliveriesByLine().get(deliveryLineId);
        if (delivery == null) {
            throw BusinessException.invalidField(field + ".deliveryLineId", "Dòng phiếu giao không tồn tại, khác chi "
                    + "nhánh / khách hoặc chưa xuất kho.");
        }
        SalesDeliveryLine deliveryLine = delivery.line(deliveryLineId, field);
        BigDecimal base = SalesAccess.baseQuantity(line.quantity(), deliveryLine.getConversionFactor(),
                field + ".quantity");
        BigDecimal limit = invoiceLine != null ? invoiceLine.returnableQuantity().min(deliveryLine.returnableQuantity())
                : deliveryLine.uninvoicedQuantity().min(deliveryLine.returnableQuantity());
        if (base.compareTo(limit) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": chỉ còn trả được "
                    + plain(limit) + " (đơn vị cơ sở)" + (invoiceLine == null ? " chưa xuất hóa đơn." : " theo hóa đơn."));
        }
        String lotNo = SalesAccess.trim(line.lotNo());
        List<SalesDeliveryItem> lotItems = deliveryLine.getItems().stream()
                .filter(item -> Objects.equals(item.getLotNo(), lotNo)).toList();
        if (lotItems.isEmpty()) {
            throw BusinessException.invalidField(field + ".lotNo", "Lô " + (lotNo == null ? "(trống)" : lotNo)
                    + " không có trong hàng đã xuất của dòng giao.");
        }
        BigDecimal lotIssued = lotItems.stream().map(SalesDeliveryItem::getBaseQuantity).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal lotLeft = lotIssued.subtract(repository.returnedForLot(deliveryLineId, lotNo));
        if (base.compareTo(lotLeft) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": lô " + lotNo + " chỉ còn trả "
                    + "được " + plain(lotLeft) + " (đơn vị cơ sở).");
        }
        List<String> serials = serials(products.get(deliveryLine.getProductId()), lotItems, line, base, field);
        SalesOrderLine orderLine = sources.ordersByLine().get(deliveryLine.getOrderLineId())
                .line(deliveryLine.getOrderLineId());
        return new SalesReturnLine.Values(deliveryLine.getId(), invoiceLine == null ? null : invoiceLine.getId(),
                orderLine.getId(), deliveryLine.getProductId(), deliveryLine.getUnitId(),
                deliveryLine.getConversionFactor(), line.quantity(), base, lotNo, lotItems.get(0).getExpiryDate(),
                line.qualityStatus() != null ? line.qualityStatus() : QualityStatus.QUARANTINE,
                invoiceLine != null ? invoiceLine.netUnitPrice() : orderLine.netUnitPrice(), null,
                invoiceLine == null ? null : invoiceLine.getTaxRateId(),
                invoiceLine == null ? null : invoiceLine.getTaxRate(), SalesAccess.trim(line.description()), serials);
    }

    /** Dòng giảm giá: theo dòng hóa đơn, không vượt giá trị còn lại (BR-SAL-36). */
    private SalesReturnLine.Values discountLine(Sources sources, SalesReturnLineRequest line, String field) {
        if (line.amount() == null) {
            throw BusinessException.invalidField(field + ".amount", "Phải nhập số tiền giảm giá.");
        }
        SalesInvoiceLine invoiceLine = invoiceLine(sources, line, field);
        BigDecimal amount = line.amount().setScale(0, RoundingMode.HALF_UP);
        if (amount.compareTo(invoiceLine.remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + invoiceLine.getLineNo()
                    + " của hóa đơn chỉ còn giá trị " + plain(invoiceLine.remainingAmount()) + ".");
        }
        return new SalesReturnLine.Values(invoiceLine.getDeliveryLineId(), invoiceLine.getId(),
                invoiceLine.getOrderLineId(), invoiceLine.getProductId(), invoiceLine.getUnitId(),
                invoiceLine.getConversionFactor(), null, null, null, null, null, null, amount,
                invoiceLine.getTaxRateId(), invoiceLine.getTaxRate(), SalesAccess.trim(line.description()), List.of());
    }

    private static SalesInvoiceLine invoiceLine(Sources sources, SalesReturnLineRequest line, String field) {
        if (line.invoiceLineId() == null) {
            throw BusinessException.invalidField(field + ".invoiceLineId", "Chọn dòng hóa đơn.");
        }
        return sources.invoice().getLines().stream().filter(candidate -> candidate.getId().equals(line.invoiceLineId()))
                .findFirst().orElseThrow(() -> BusinessException.invalidField(field + ".invoiceLineId",
                        "Dòng không thuộc hóa đơn " + sources.invoice().getDocumentNo() + "."));
    }

    /** Hàng quản lý serial: đúng số lượng, thuộc serial đã xuất của lô. */
    private static List<String> serials(ProductInfo product, List<SalesDeliveryItem> lotItems,
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

    private Sources sources(BranchRef branch, Long customerId, SalesReturnRequest request) {
        SalesInvoice invoice = null;
        if (request.salesInvoiceId() != null) {
            invoice = postedInvoice(branch, customerId, request.salesInvoiceId());
        } else if (request.documentType() == SalesReturnType.GGB) {
            throw BusinessException.invalidField("salesInvoiceId", "Giảm giá hàng bán phải theo hóa đơn bán.");
        }
        List<Long> deliveryLineIds = invoice != null
                ? invoice.getLines().stream().map(SalesInvoiceLine::getDeliveryLineId).filter(Objects::nonNull).toList()
                : request.lines().stream().map(SalesReturnLineRequest::deliveryLineId).filter(Objects::nonNull).toList();
        Map<Long, SalesDelivery> byLine = deliveriesByLine(deliveryLineIds);
        byLine.values().removeIf(delivery -> !delivery.getCompanyId().equals(branch.companyId())
                || !delivery.getBranchId().equals(branch.id()) || !delivery.getCustomerId().equals(customerId)
                || delivery.getStatus() != SalesDocumentStatus.POSTED);
        List<Long> orderLineIds = new ArrayList<>();
        byLine.values().forEach(delivery -> delivery.getLines().forEach(line -> orderLineIds.add(line.getOrderLineId())));
        if (invoice != null) {
            invoice.getLines().forEach(line -> orderLineIds.add(line.getOrderLineId()));
        }
        return new Sources(invoice, byLine, ordersByLine(orderLineIds));
    }

    private SalesInvoice postedInvoice(BranchRef branch, Long customerId, Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .filter(found -> found.getCompanyId().equals(branch.companyId())
                        && found.getBranchId().equals(branch.id()) && found.getCustomerId().equals(customerId)
                        && found.getStatus() == SalesDocumentStatus.POSTED)
                .orElseThrow(() -> BusinessException.invalidField("salesInvoiceId", "Hóa đơn bán không tồn tại, khác "
                        + "chi nhánh / khách hoặc chưa ghi sổ."));
    }

    private Map<Long, SalesDelivery> deliveriesByLine(List<Long> deliveryLineIds) {
        Map<Long, SalesDelivery> byLine = new HashMap<>();
        if (!deliveryLineIds.isEmpty()) {
            deliveryRepository.findByLineIds(deliveryLineIds).forEach(delivery -> delivery.getLines()
                    .forEach(line -> byLine.put(line.getId(), delivery)));
        }
        return byLine;
    }

    private Map<Long, SalesOrder> ordersByLine(List<Long> orderLineIds) {
        Map<Long, SalesOrder> byLine = new HashMap<>();
        List<Long> ids = orderLineIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) {
            orderRepository.findByLineIds(ids).forEach(order -> order.getLines()
                    .forEach(line -> byLine.put(line.getId(), order)));
        }
        return byLine;
    }

    /** Kho nhập lại: kho của phiếu giao; mọi dòng cùng kho (một phiếu nhập). */
    private static Long warehouse(Sources sources, SalesReturnRequest request) {
        Set<Long> warehouses = new HashSet<>();
        for (SalesReturnLineRequest line : request.lines()) {
            Long deliveryLineId = line.deliveryLineId();
            if (sources.invoice() != null && line.invoiceLineId() != null) {
                deliveryLineId = sources.invoice().getLines().stream()
                        .filter(candidate -> candidate.getId().equals(line.invoiceLineId())).findFirst()
                        .map(SalesInvoiceLine::getDeliveryLineId).orElse(null);
            }
            SalesDelivery delivery = deliveryLineId == null ? null : sources.deliveriesByLine().get(deliveryLineId);
            if (delivery != null) {
                warehouses.add(delivery.getWarehouseId());
            }
        }
        if (warehouses.size() > 1) {
            throw BusinessException.invalidField("lines", "Các dòng trả hàng phải cùng một kho; lập phiếu riêng cho "
                    + "từng kho.");
        }
        return warehouses.stream().findFirst().orElseThrow(() -> BusinessException.invalidField(
                "lines[0].deliveryLineId", "Dòng phiếu giao không hợp lệ."));
    }

    private static List<Long> productIds(Sources sources) {
        List<Long> ids = new ArrayList<>();
        sources.deliveriesByLine().values().forEach(delivery -> delivery.getLines()
                .forEach(line -> ids.add(line.getProductId())));
        if (sources.invoice() != null) {
            sources.invoice().getLines().forEach(line -> ids.add(line.getProductId()));
        }
        return ids;
    }

    /** Phần còn trả của dòng giao chia theo lô đã xuất (đã xuất − đã trả của lô), tối đa {@code limit}. */
    private List<LotShare> lots(SalesDeliveryLine line, BigDecimal limit) {
        if (line == null) {
            return List.of(new LotShare(null, null, BigDecimal.ZERO, List.of()));
        }
        Map<String, List<SalesDeliveryItem>> byLot = new LinkedHashMap<>();
        line.getItems().forEach(item -> byLot.computeIfAbsent(Objects.toString(item.getLotNo(), ""),
                ignored -> new ArrayList<>()).add(item));
        List<LotShare> shares = new ArrayList<>();
        BigDecimal left = limit;
        for (List<SalesDeliveryItem> items : byLot.values()) {
            String lotNo = items.get(0).getLotNo();
            BigDecimal issued = items.stream().map(SalesDeliveryItem::getBaseQuantity).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            BigDecimal available = issued.subtract(repository.returnedForLot(line.getId(), lotNo)).min(left);
            if (available.signum() > 0) {
                shares.add(new LotShare(lotNo, items.get(0).getExpiryDate(), available, items.stream()
                        .map(SalesDeliveryItem::getSerialNo).filter(Objects::nonNull).toList()));
                left = left.subtract(available);
            }
        }
        return shares;
    }

    private record LotShare(String lotNo, LocalDate expiry, BigDecimal quantity, List<String> serials) {
    }

    private static String code(Map<Long, ProductInfo> products, Long id) {
        ProductInfo product = products.get(id);
        return product == null ? null : product.code();
    }

    private static String name(Map<Long, ProductInfo> products, Long id) {
        ProductInfo product = products.get(id);
        return product == null ? null : product.name();
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
        String invoiceNo = null;
        String inventoryNo = null;
        if (detailed) {
            invoiceNo = document.getSalesInvoiceId() == null ? null
                    : invoiceRepository.findById(document.getSalesInvoiceId()).map(SalesInvoice::getDocumentNo)
                    .orElse(null);
            Map<Long, SalesDelivery> byLine = deliveriesByLine(document.getLines().stream()
                    .map(SalesReturnLine::getDeliveryLineId).filter(Objects::nonNull).toList());
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(SalesReturnLine::getProductId).toList());
            lines = document.getLines().stream().map(line -> {
                SalesDelivery delivery = line.getDeliveryLineId() == null ? null : byLine.get(line.getDeliveryLineId());
                return new SalesReturnLineResponse(line.getId(), line.getLineNo(), line.getDeliveryLineId(),
                        delivery == null ? null : delivery.getDocumentNo(), line.getInvoiceLineId(),
                        line.getOrderLineId(), line.getProductId(), code(products, line.getProductId()),
                        name(products, line.getProductId()), line.getUnitId(), line.getQuantity(),
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
                customer == null ? null : customer.name(), document.getSalesInvoiceId(), invoiceNo,
                document.getWarehouseId(), document.getInventoryDocumentId(), inventoryNo, document.getDescription(),
                document.getAmount(), document.getTaxAmount(), document.getTotalAmount(), document.getStatus(),
                document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(),
                document.getReverseDate(), document.getReason(), document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(document.typeCode(), document.getId()) : List.of());
    }
}
