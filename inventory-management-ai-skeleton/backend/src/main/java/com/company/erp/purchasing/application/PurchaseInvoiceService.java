package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.InvoiceableLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseInvoiceLineRequest;
import com.company.erp.purchasing.application.dto.PurchaseInvoiceLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseInvoiceRequest;
import com.company.erp.purchasing.application.dto.PurchaseInvoiceResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.InspectionResult;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseInvoice;
import com.company.erp.purchasing.domain.PurchaseInvoiceLine;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import com.company.erp.purchasing.domain.PurchaseReceiptLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseInvoiceRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReceiptRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReceiptRepository.InvoiceableLineRow;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hóa đơn mua (HDM, BR-PUR-09): lập theo dòng phiếu nhận hàng đã nhập kho (nhiều phiếu một hóa đơn, một phiếu nhiều
 * hóa đơn) và dòng dịch vụ (BR-PUR-16), chống trùng (MST, ký hiệu, số), gửi duyệt (duyệt khi có luồng), duyệt, từ
 * chối, hủy; cảnh báo đối chiếu ba chiều (BR-PUR-11). Ghi sổ, đảo ở {@link PurchaseInvoicePostingService}.
 */
@Service
public class PurchaseInvoiceService {

    static final String PATH = "/api/v1/purchase-invoices";
    static final String VIEW = "purchase-invoice:view";
    static final String CREATE = "purchase-invoice:create";
    static final String APPROVE = "purchase-invoice:approve";
    static final String ENTITY = "PURCHASE_INVOICE";
    static final String PRICE_TOLERANCE = "purchasing.price-tolerance-percent";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record InvoiceFilter(Long companyId, Long branchId, PurchaseDocumentStatus status, Long supplierId,
                                LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    /** Dòng nguồn đã tra cho một hóa đơn: dòng phiếu nhận, phiếu nhận, dòng đơn mua. */
    private record Sources(Map<Long, PurchaseReceipt> receiptsByLine, Map<Long, PurchaseReceiptLine> receiptLines,
                           Map<Long, PurchaseOrder> ordersByLine) {
    }

    private final PurchaseInvoiceRepository repository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final ParameterService parameters;
    private final PurchaseDocumentFlow.Spec<PurchaseInvoice, PurchaseInvoiceResponse> spec;

    @SuppressWarnings("java:S107")
    public PurchaseInvoiceService(PurchaseInvoiceRepository repository, PurchaseReceiptRepository receiptRepository,
                                  PurchaseOrderRepository orderRepository, PurchaseDocumentFlow flow,
                                  PurchasingAccess access, ApprovalService approvalService,
                                  ParameterService parameters) {
        this.repository = repository;
        this.receiptRepository = receiptRepository;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.parameters = parameters;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                PurchaseInvoiceResponse.class, null, null, document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInvoiceResponse> search(CurrentUser actor, InvoiceFilter filter, Pageable pageable) {
        Page<PurchaseInvoice> page = flow.search(actor, VIEW, repository,
                new PurchaseDocumentFlow.Filter<PurchaseInvoice>(filter.companyId(), filter.branchId(),
                        filter.status(), filter.dateFrom(), filter.dateTo(), filter.keyword(),
                        filter.supplierId() == null ? null
                                : (root, query, cb) -> cb.equal(root.get("supplierId"), filter.supplierId())),
                pageable);
        Map<Long, PartnerInfo> suppliers = access.partners(page.getContent().stream()
                .map(PurchaseInvoice::getSupplierId).toList());
        return page.map(document -> toResponse(document, suppliers.get(document.getSupplierId()), false));
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Dòng phiếu nhận hàng của nhà cung cấp, chi nhánh đã nhập kho còn chưa nhận hóa đơn. */
    @Transactional(readOnly = true)
    public List<InvoiceableLineResponse> invoiceableLines(CurrentUser actor, Long branchId, Long supplierId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        List<InvoiceableLineRow> rows = receiptRepository.findInvoiceableLines(branch.companyId(), branch.id(),
                supplierId, PurchaseDocumentStatus.POSTED, InspectionResult.RETURN);
        Map<Long, ProductInfo> products = access.products(rows.stream().map(InvoiceableLineRow::getProductId)
                .toList());
        Map<Long, PurchaseOrder> orders = new HashMap<>();
        orderRepository.findAllById(rows.stream().map(InvoiceableLineRow::getPurchaseOrderId).distinct().toList())
                .forEach(order -> orders.put(order.getId(), order));
        return rows.stream().map(row -> {
            ProductInfo product = products.get(row.getProductId());
            PurchaseOrder order = orders.get(row.getPurchaseOrderId());
            PurchaseOrderLine orderLine = order == null ? null : order.line(row.getOrderLineId());
            return new InvoiceableLineResponse(row.getReceiptId(), row.getReceiptNo(), row.getReceiptDate(),
                    row.getPurchaseOrderId(), order == null ? null : order.getDocumentNo(), row.getReceiptLineId(),
                    row.getLineNo(), row.getOrderLineId(), row.getProductId(), product == null ? null : product.code(),
                    product == null ? null : product.name(), row.getUnitId(),
                    row.getRemaining().divide(row.getConversionFactor(), 3, RoundingMode.DOWN), row.getUnitCost(),
                    orderLine == null ? null : orderLine.getTaxRateId());
        }).toList();
    }

    @Transactional
    public PurchaseInvoiceResponse create(CurrentUser actor, String idempotencyKey, PurchaseInvoiceRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseInvoiceResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            PartnerInfo supplier = access.supplier(branch.companyId(), request.supplierId(), "supplierId");
            PurchaseInvoice document = new PurchaseInvoice(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), PurchaseInvoice.TYPE_CODE,
                            request.documentDate()), supplier.id());
            fill(document, supplier, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public PurchaseInvoiceResponse update(CurrentUser actor, Long id, PurchaseInvoiceRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseInvoice document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())
                || !document.getSupplierId().equals(request.supplierId())) {
            throw BusinessException.invalidField("supplierId", "Không đổi chi nhánh, nhà cung cấp sau khi lập.");
        }
        fill(document, access.supplier(document.getCompanyId(), document.getSupplierId(), "supplierId"), request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public PurchaseInvoiceResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public PurchaseInvoiceResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseInvoiceResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseInvoiceResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    PurchaseDocumentFlow.Spec<PurchaseInvoice, PurchaseInvoiceResponse> spec() {
        return spec;
    }

    /** Hóa đơn khác còn hiệu lực trùng (MST, ký hiệu, số): 409 {@code DUPLICATE_DOCUMENT} (BR-PUR-09). */
    void requireUnique(PurchaseInvoice document) {
        repository.findDuplicate(document.getCompanyId(), document.getDuplicateKey(), document.getId())
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "Hóa đơn " + document.invoiceLabel()
                            + " của MST " + document.getSellerTaxCode() + " đã được nhập ở chứng từ " + other + ".");
                });
    }

    private void fill(PurchaseInvoice document, PartnerInfo supplier, PurchaseInvoiceRequest request) {
        Sources sources = sources(document, request);
        List<PurchaseInvoiceLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseInvoiceLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            lines.add(new PurchaseInvoiceLine(i + 1, line.receiptLineId() != null
                    ? goodsLine(document, sources, line, request.invoiceDate(), field)
                    : serviceLine(document, sources, line, request.invoiceDate(), field)));
        }
        String taxCode = PurchaseRequisitionService.trim(request.sellerTaxCode()) != null ? request.sellerTaxCode()
                : supplier.taxCode();
        if (PurchaseRequisitionService.trim(taxCode) == null) {
            throw BusinessException.invalidField("sellerTaxCode", "Nhà cung cấp chưa khai báo MST; nhập MST người bán.");
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        LocalDate dueDate = request.dueDate() != null ? request.dueDate()
                : request.invoiceDate().plusDays(paymentTerm(sources, supplier));
        document.replaceContent(new PurchaseInvoice.Header(request.documentDate(), postingDate, taxCode,
                request.invoiceSeries(), request.invoiceNo(), request.invoiceDate(), dueDate,
                PurchaseRequisitionService.trim(request.description())), lines);
        requireUnique(document);
    }

    /** Dòng hàng hóa: dòng phiếu nhận đã nhập kho cùng chi nhánh, nhà cung cấp; không vượt số chưa nhận hóa đơn. */
    private PurchaseInvoiceLine.Values goodsLine(PurchaseInvoice document, Sources sources,
                                                 PurchaseInvoiceLineRequest line, LocalDate date, String field) {
        PurchaseReceipt receipt = sources.receiptsByLine().get(line.receiptLineId());
        PurchaseReceiptLine receiptLine = sources.receiptLines().get(line.receiptLineId());
        if (receipt == null || !receipt.getBranchId().equals(document.getBranchId())
                || !receipt.getSupplierId().equals(document.getSupplierId()) || !receipt.isStocked()
                || receipt.getStatus() != PurchaseDocumentStatus.POSTED || !receiptLine.getInspectionResult().stocked()) {
            throw BusinessException.invalidField(field + ".receiptLineId", "Dòng phiếu nhận hàng không tồn tại, khác "
                    + "chi nhánh / nhà cung cấp, chưa nhập kho hoặc là hàng trả lại.");
        }
        BigDecimal base = PurchasingAccess.baseQuantity(line.quantity(), receiptLine.getConversionFactor(),
                field + ".quantity");
        if (base.compareTo(receiptLine.uninvoicedQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": phiếu " + receipt.getDocumentNo()
                    + " dòng " + receiptLine.getLineNo() + " còn " + receiptLine.uninvoicedQuantity()
                    .stripTrailingZeros().toPlainString() + " (đơn vị cơ sở) chưa nhận hóa đơn.");
        }
        PurchaseOrderLine orderLine = sources.ordersByLine().get(receiptLine.getOrderLineId())
                .line(receiptLine.getOrderLineId());
        TaxRateInfo tax = tax(document, line.taxRateId() != null ? line.taxRateId() : orderLine.getTaxRateId(), date,
                field);
        return new PurchaseInvoiceLine.Values(receiptLine.getId(), orderLine.getId(), receiptLine.getProductId(),
                receiptLine.getUnitId(), receiptLine.getConversionFactor(), line.quantity(), base, line.unitPrice(),
                tax == null ? null : tax.id(), tax == null ? null : tax.rate(), Boolean.TRUE.equals(line.promotional()),
                PurchaseRequisitionService.trim(line.description()));
    }

    /** Dòng dịch vụ không qua kho (BR-PUR-16); theo dòng đơn mua dịch vụ cùng nhà cung cấp nếu có. */
    private PurchaseInvoiceLine.Values serviceLine(PurchaseInvoice document, Sources sources,
                                                   PurchaseInvoiceLineRequest line, LocalDate date, String field) {
        if (line.productId() == null || line.unitId() == null) {
            throw BusinessException.invalidField(field + ".productId", "Chọn dòng phiếu nhận hàng hoặc dịch vụ.");
        }
        ProductInfo product = access.product(document.getCompanyId(), line.productId(), field + ".productId");
        if (product.stockTracked()) {
            throw BusinessException.invalidField(field + ".receiptLineId", "Hàng " + product.code()
                    + " quản lý tồn kho: hóa đơn theo dòng phiếu nhận hàng.");
        }
        BigDecimal factor = access.conversionFactor(line.productId(), line.unitId(), date, field + ".unitId");
        Long taxRateId = line.taxRateId();
        if (line.orderLineId() != null) {
            PurchaseOrder order = sources.ordersByLine().get(line.orderLineId());
            if (order == null || !order.getSupplierId().equals(document.getSupplierId())
                    || !order.getBranchId().equals(document.getBranchId()) || !order.isOpen()
                    || !order.line(line.orderLineId()).getProductId().equals(line.productId())) {
                throw BusinessException.invalidField(field + ".orderLineId", "Dòng đơn mua không hợp lệ cho dịch vụ này.");
            }
            taxRateId = taxRateId != null ? taxRateId : order.line(line.orderLineId()).getTaxRateId();
        }
        TaxRateInfo tax = tax(document, taxRateId != null ? taxRateId : product.defaultTaxRateId(), date, field);
        return new PurchaseInvoiceLine.Values(null, line.orderLineId(), line.productId(), line.unitId(), factor,
                line.quantity(), PurchasingAccess.baseQuantity(line.quantity(), factor, field + ".quantity"),
                line.unitPrice(), tax == null ? null : tax.id(), tax == null ? null : tax.rate(),
                Boolean.TRUE.equals(line.promotional()), PurchaseRequisitionService.trim(line.description()));
    }

    private TaxRateInfo tax(PurchaseInvoice document, Long taxRateId, LocalDate date, String field) {
        return taxRateId == null ? null
                : access.taxRate(document.getCompanyId(), taxRateId, date, field + ".taxRateId");
    }

    private Sources sources(PurchaseInvoice document, PurchaseInvoiceRequest request) {
        List<Long> receiptLineIds = request.lines().stream().map(PurchaseInvoiceLineRequest::receiptLineId)
                .filter(Objects::nonNull).toList();
        if (receiptLineIds.size() != receiptLineIds.stream().distinct().count()) {
            throw BusinessException.invalidField("lines", "Mỗi dòng phiếu nhận hàng chỉ xuất hiện một lần trên hóa đơn.");
        }
        Map<Long, PurchaseReceipt> receiptsByLine = new HashMap<>();
        Map<Long, PurchaseReceiptLine> receiptLines = new HashMap<>();
        if (!receiptLineIds.isEmpty()) {
            for (PurchaseReceipt receipt : receiptRepository.findByLineIds(receiptLineIds)) {
                if (!receipt.getCompanyId().equals(document.getCompanyId())) {
                    continue;
                }
                receipt.getLines().stream().filter(line -> receiptLineIds.contains(line.getId())).forEach(line -> {
                    receiptsByLine.put(line.getId(), receipt);
                    receiptLines.put(line.getId(), line);
                });
            }
        }
        List<Long> orderLineIds = Stream.concat(receiptLines.values().stream().map(PurchaseReceiptLine::getOrderLineId),
                request.lines().stream().map(PurchaseInvoiceLineRequest::orderLineId).filter(Objects::nonNull))
                .distinct().toList();
        Map<Long, PurchaseOrder> ordersByLine = new HashMap<>();
        if (!orderLineIds.isEmpty()) {
            for (PurchaseOrder order : orderRepository.findByLineIds(orderLineIds)) {
                order.getLines().stream().filter(line -> orderLineIds.contains(line.getId()))
                        .forEach(line -> ordersByLine.put(line.getId(), order));
            }
        }
        return new Sources(receiptsByLine, receiptLines, ordersByLine);
    }

    /** Hạn thanh toán: theo đơn mua liên quan đầu tiên, không có thì theo nhà cung cấp, không có thì 0 ngày. */
    private static long paymentTerm(Sources sources, PartnerInfo supplier) {
        return sources.ordersByLine().values().stream().map(PurchaseOrder::getPaymentTermDays)
                .filter(Objects::nonNull).findFirst()
                .orElse(supplier.supplierPaymentTermDays() == null ? 0 : supplier.supplierPaymentTermDays());
    }

    /**
     * Đối chiếu ba chiều (BR-PUR-11): đơn giá hóa đơn lệch đơn giá đơn mua (sau chiết khấu) quá dung sai. Số lượng
     * không vượt số đã nhập kho chưa nhận hóa đơn được chặn khi lập / ghi sổ.
     */
    private List<String> matchWarnings(PurchaseInvoice document, Map<Long, BigDecimal> provisional) {
        BigDecimal tolerance = parameters.decimalValue(document.getCompanyId(), PRICE_TOLERANCE);
        List<String> warnings = new ArrayList<>();
        for (PurchaseInvoiceLine line : document.getLines()) {
            BigDecimal expected = provisional.get(line.getId());
            if (expected == null || line.isPromotional() || expected.signum() == 0) {
                continue;
            }
            BigDecimal percent = line.getUnitPrice().subtract(expected).abs().multiply(HUNDRED)
                    .divide(expected, 2, RoundingMode.HALF_UP);
            if (percent.compareTo(tolerance) > 0) {
                warnings.add("Dòng " + line.getLineNo() + ": đơn giá hóa đơn " + plain(line.getUnitPrice())
                        + " lệch đơn giá đơn mua " + plain(expected) + " (" + plain(percent) + "%).");
            }
        }
        return warnings;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    PurchaseInvoiceResponse toResponse(PurchaseInvoice document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getSupplierId())).get(document.getSupplierId()),
                detailed);
    }

    private PurchaseInvoiceResponse toResponse(PurchaseInvoice document, PartnerInfo supplier, boolean detailed) {
        List<PurchaseInvoiceLineResponse> lines = List.of();
        List<String> warnings = List.of();
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(PurchaseInvoiceLine::getProductId).toList());
            Map<Long, PurchaseReceipt> receipts = new HashMap<>();
            List<Long> receiptLineIds = document.getLines().stream().map(PurchaseInvoiceLine::getReceiptLineId)
                    .filter(Objects::nonNull).toList();
            if (!receiptLineIds.isEmpty()) {
                receiptRepository.findByLineIds(receiptLineIds).forEach(receipt -> receipt.getLines()
                        .forEach(line -> receipts.put(line.getId(), receipt)));
            }
            Map<Long, PurchaseOrder> orders = new HashMap<>();
            List<Long> orderLineIds = document.getLines().stream().map(PurchaseInvoiceLine::getOrderLineId)
                    .filter(Objects::nonNull).toList();
            if (!orderLineIds.isEmpty()) {
                orderRepository.findByLineIds(orderLineIds).forEach(order -> order.getLines()
                        .forEach(line -> orders.put(line.getId(), order)));
            }
            Map<Long, BigDecimal> provisional = new HashMap<>();
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                PurchaseReceipt receipt = line.getReceiptLineId() == null ? null : receipts.get(line.getReceiptLineId());
                PurchaseOrder order = line.getOrderLineId() == null ? null : orders.get(line.getOrderLineId());
                BigDecimal expected = order == null ? null : order.line(line.getOrderLineId()).provisionalUnitCost();
                if (expected != null) {
                    provisional.put(line.getId(), expected);
                }
                return new PurchaseInvoiceLineResponse(line.getId(), line.getLineNo(), line.getReceiptLineId(),
                        receipt == null ? null : receipt.getDocumentNo(), line.getOrderLineId(), line.getProductId(),
                        product == null ? null : product.code(), product == null ? null : product.name(),
                        line.getUnitId(), line.getConversionFactor(), line.getQuantity(), line.getBaseQuantity(),
                        line.getUnitPrice(), expected, line.getAmount(), line.getTaxRateId(), line.getTaxRate(),
                        line.getTaxAmount(), line.isPromotional(), line.getDescription());
            }).toList();
            warnings = matchWarnings(document, provisional);
        }
        return new PurchaseInvoiceResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(),
                document.getSupplierId(), supplier == null ? null : supplier.code(),
                supplier == null ? null : supplier.name(), document.getSellerTaxCode(), document.getInvoiceSeries(),
                document.getInvoiceNo(), document.getInvoiceDate(), document.getDueDate(), document.getDescription(),
                document.getAmount(), document.getTaxAmount(), document.getTotalAmount(), document.getStatus(),
                document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(),
                document.getReverseDate(), document.getReason(), document.getCreatedBy(), document.getVersion(),
                warnings, lines,
                detailed ? approvalService.history(PurchaseInvoice.TYPE_CODE, document.getId()) : List.of());
    }
}
