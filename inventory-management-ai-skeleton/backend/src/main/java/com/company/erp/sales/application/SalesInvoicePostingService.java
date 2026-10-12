package com.company.erp.sales.application;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.finance.application.DebtPostingService;
import com.company.erp.finance.application.DebtPostingService.DebtReversalCommand;
import com.company.erp.finance.application.DebtPostingService.InvoiceDebtCommand;
import com.company.erp.finance.application.DebtPostingService.InvoiceSide;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesInvoiceResponse;
import com.company.erp.sales.domain.SalesDelivery;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesInvoice;
import com.company.erp.sales.domain.SalesInvoiceLine;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.infrastructure.persistence.SalesDeliveryRepository;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ / đảo hóa đơn bán (BR-SAL-30, D-35): số đã xuất hóa đơn của dòng phiếu giao và dòng đơn bán, công nợ phải thu
 * (một khoản cho cả hóa đơn, nhân viên kinh doanh), bút toán doanh thu và thuế GTGT đầu ra theo quy tắc hạch toán
 * HDB; giá vốn ghi ở Giai đoạn 8. Thứ tự khóa: hóa đơn → phiếu giao (id tăng dần) → đơn bán (id tăng dần) → khoản
 * công nợ → bút toán; cùng chiều phiếu giao → đơn với ghi sổ phiếu giao. READ COMMITTED.
 */
@Service
public class SalesInvoicePostingService {

    static final String POST = "sales-invoice:post";
    static final String REVERSE = "sales-invoice:reverse";
    static final String SALES = "SALES";
    static final String SALES_VAT = "SALES_VAT";
    static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final Logger log = LoggerFactory.getLogger(SalesInvoicePostingService.class);

    private final SalesInvoiceService invoices;
    private final SalesDeliveryRepository deliveryRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public SalesInvoicePostingService(SalesInvoiceService invoices, SalesDeliveryRepository deliveryRepository,
                                      SalesOrderRepository orderRepository, SalesDocumentFlow flow,
                                      SalesAccess access, DebtPostingService debtPosting,
                                      JournalPostingService journalPosting, BusinessCalendar calendar, Clock clock) {
        this.invoices = invoices;
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesInvoiceResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + SalesInvoiceService.PATH + "/" + id + "/post", id,
                SalesInvoiceResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    SalesInvoice invoice = flow.lock(actor, id, invoices.spec());
                    postLocked(actor, invoice);
                    return flow.act(actor, invoice, "POST", null, invoices.spec());
                });
    }

    /** Đảo: hóa đơn đã thu / bù trừ hoặc đã có trả hàng / giảm giá thì 409 {@code HAS_DEPENDENT_DOCUMENTS}. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesInvoiceResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesInvoiceService.PATH + "/" + id + "/reverse", request,
                SalesInvoiceResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    SalesInvoice invoice = flow.lock(actor, id, invoices.spec());
                    if (invoice.getStatus() != SalesDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Hóa đơn "
                                + invoice.getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
                    }
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    invoice.markReversed(actor.id(), Instant.now(clock), date, reason);
                    applySources(invoice, BigDecimal.ONE.negate());
                    if (invoice.getTotalAmount().signum() > 0) {
                        debtPosting.reverse(new DebtReversalCommand(invoice.getCompanyId(), invoice.getBranchId(),
                                SalesInvoice.TYPE_CODE, invoice.getId(), date, invoice.getDocumentNo(), actor.id()));
                        journalPosting.reverse(new ReversalRequest(invoice.getCompanyId(), SalesInvoice.TYPE_CODE,
                                invoice.getId(), date, "Đảo " + invoice.getDocumentNo() + ": " + reason, actor.id()));
                    }
                    return flow.act(actor, invoice, "REVERSE", reason, invoices.spec());
                });
    }

    /** Ghi sổ hóa đơn đã khóa (dùng cả cho bán nhanh). */
    void postLocked(CurrentUser actor, SalesInvoice invoice) {
        if (invoice.getStatus() != SalesDocumentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Hóa đơn " + invoice.getDocumentNo()
                    + " ở trạng thái " + invoice.getStatus() + ", chỉ ghi sổ hóa đơn đã duyệt.");
        }
        invoices.requireUniqueEinvoice(invoice);
        applySources(invoice, BigDecimal.ONE);
        if (invoice.getTotalAmount().signum() > 0) {
            debtPosting.recordInvoice(new InvoiceDebtCommand(invoice.getCompanyId(), invoice.getBranchId(),
                    InvoiceSide.RECEIVABLE, invoice.getCustomerId(), SalesInvoice.TYPE_CODE, invoice.getId(),
                    invoice.getDocumentNo(), invoice.invoiceLabel(), invoice.getInvoiceDate(), invoice.getDueDate(),
                    invoice.getTotalAmount(), invoice.getPostingDate(), invoice.getSalesUserId(),
                    description(invoice), actor.id()));
            journalPosting.post(new PostingRequest(invoice.getCompanyId(), invoice.getBranchId(),
                    invoice.getPostingDate(), SalesInvoice.TYPE_CODE, invoice.getId(), invoice.getDocumentNo(),
                    description(invoice), actor.id(), journalLines(invoice)));
        }
        invoice.markPosted(actor.id(), Instant.now(clock));
        log.info("Ghi sổ hóa đơn bán {}", invoice.getDocumentNo());
    }

    /** Khóa phiếu giao rồi đơn bán theo id tăng dần; cộng ({@code sign} = 1) / trừ số đã xuất hóa đơn. */
    private void applySources(SalesInvoice invoice, BigDecimal sign) {
        List<Long> deliveryLineIds = invoice.getLines().stream().map(SalesInvoiceLine::getDeliveryLineId)
                .filter(Objects::nonNull).toList();
        if (!deliveryLineIds.isEmpty()) {
            for (Long deliveryId : deliveryRepository.findIdsByLineIds(deliveryLineIds)) {
                SalesDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                        .orElseThrow(BusinessException::notFound);
                for (SalesInvoiceLine line : invoice.getLines()) {
                    if (line.isGoods() && delivery.getLines().stream()
                            .anyMatch(candidate -> candidate.getId().equals(line.getDeliveryLineId()))) {
                        delivery.recordInvoice(line.getDeliveryLineId(), line.getBaseQuantity().multiply(sign),
                                "lines[" + (line.getLineNo() - 1) + "]");
                    }
                }
                deliveryRepository.save(delivery);
            }
        }
        for (Long orderId : orderRepository.findIdsByLineIds(invoice.getLines().stream()
                .map(SalesInvoiceLine::getOrderLineId).toList())) {
            SalesOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(BusinessException::notFound);
            invoice.getLines().stream()
                    .filter(line -> order.getLines().stream()
                            .anyMatch(candidate -> candidate.getId().equals(line.getOrderLineId())))
                    .forEach(line -> order.recordInvoice(line.getOrderLineId(), line.getBaseQuantity().multiply(sign)));
            orderRepository.save(order);
        }
    }

    /** Dòng hạch toán: doanh thu từng dòng theo loại hàng (hàng hóa / dịch vụ), thuế GTGT đầu ra gộp một dòng. */
    private List<PostingLine> journalLines(SalesInvoice invoice) {
        Map<Long, ProductInfo> products = access.products(invoice.getLines().stream()
                .map(SalesInvoiceLine::getProductId).toList());
        List<PostingLine> lines = new ArrayList<>();
        for (SalesInvoiceLine line : invoice.getLines()) {
            if (line.getAmount().signum() == 0) {
                continue;
            }
            ProductInfo product = products.get(line.getProductId());
            lines.add(new PostingLine(SALES, line.getAmount(), product == null ? null : product.productTypeCode(),
                    CUSTOMER_ROLE, null, null, invoice.getCustomerId(), line.getProductId(), null, null, null,
                    line.getId(), line.getDescription()));
        }
        if (invoice.getTaxAmount().signum() > 0) {
            lines.add(new PostingLine(SALES_VAT, invoice.getTaxAmount(), null, CUSTOMER_ROLE, null, null,
                    invoice.getCustomerId(), null, null, null, null, null, "Thuế GTGT " + invoice.invoiceLabel()));
        }
        return lines;
    }

    private static String description(SalesInvoice invoice) {
        return invoice.getDescription() != null ? invoice.getDescription()
                : "Hóa đơn bán " + invoice.invoiceLabel();
    }
}
