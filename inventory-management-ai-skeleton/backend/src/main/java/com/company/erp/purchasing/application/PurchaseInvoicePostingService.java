package com.company.erp.purchasing.application;

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
import com.company.erp.purchasing.application.dto.PurchaseInvoiceResponse;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.PurchaseCostEntry;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseInvoice;
import com.company.erp.purchasing.domain.PurchaseInvoiceLine;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import com.company.erp.purchasing.domain.PurchaseReceiptLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseCostEntryRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReceiptRepository;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ / đảo hóa đơn mua (BR-PUR-09, 10, D-34): số đã nhận hóa đơn của dòng phiếu nhận và dòng đơn mua, chênh lệch
 * giá so với giá tạm tính theo dòng phiếu nhận, công nợ phải trả, bút toán theo quy tắc hạch toán. Thứ tự khóa: hóa
 * đơn → phiếu nhận hàng (id tăng dần) → đơn mua (id tăng dần) → khoản công nợ → bút toán; cùng chiều phiếu nhận → đơn
 * mua với Giai đoạn 5B. READ COMMITTED như ghi sổ công nợ.
 */
@Service
public class PurchaseInvoicePostingService {

    static final String POST = "purchase-invoice:post";
    static final String REVERSE = "purchase-invoice:reverse";
    static final String PURCHASE = "PURCHASE";
    static final String PURCHASE_VAT = "PURCHASE_VAT";
    static final String SUPPLIER_ROLE = "SUPPLIER";
    private static final Logger log = LoggerFactory.getLogger(PurchaseInvoicePostingService.class);

    private final PurchaseInvoiceService invoices;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseCostEntryRepository costRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public PurchaseInvoicePostingService(PurchaseInvoiceService invoices, PurchaseReceiptRepository receiptRepository,
                                         PurchaseOrderRepository orderRepository,
                                         PurchaseCostEntryRepository costRepository, PurchaseDocumentFlow flow,
                                         PurchasingAccess access, DebtPostingService debtPosting,
                                         JournalPostingService journalPosting, BusinessCalendar calendar,
                                         Clock clock) {
        this.invoices = invoices;
        this.receiptRepository = receiptRepository;
        this.orderRepository = orderRepository;
        this.costRepository = costRepository;
        this.flow = flow;
        this.access = access;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseInvoiceResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PurchaseInvoiceService.PATH + "/" + id + "/post", id,
                PurchaseInvoiceResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    PurchaseInvoice invoice = flow.lock(actor, id, invoices.spec());
                    if (invoice.getStatus() != PurchaseDocumentStatus.APPROVED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Hóa đơn "
                                + invoice.getDocumentNo() + " ở trạng thái " + invoice.getStatus()
                                + ", chỉ ghi sổ hóa đơn đã duyệt.");
                    }
                    invoices.requireUnique(invoice);
                    Map<Long, PurchaseReceipt> receipts = applySources(invoice, BigDecimal.ONE);
                    recordPriceDifferences(actor, invoice, receipts);
                    if (invoice.getTotalAmount().signum() > 0) {
                        debtPosting.recordInvoice(new InvoiceDebtCommand(invoice.getCompanyId(),
                                invoice.getBranchId(), InvoiceSide.PAYABLE, invoice.getSupplierId(),
                                PurchaseInvoice.TYPE_CODE, invoice.getId(), invoice.getDocumentNo(),
                                invoice.invoiceLabel(), invoice.getInvoiceDate(), invoice.getDueDate(),
                                invoice.getTotalAmount(), invoice.getPostingDate(), null, description(invoice),
                                actor.id()));
                        journalPosting.post(new PostingRequest(invoice.getCompanyId(), invoice.getBranchId(),
                                invoice.getPostingDate(), PurchaseInvoice.TYPE_CODE, invoice.getId(),
                                invoice.getDocumentNo(), description(invoice), actor.id(),
                                journalLines(invoice, receipts)));
                    }
                    invoice.markPosted(actor.id(), Instant.now(clock));
                    log.info("Ghi sổ hóa đơn mua {} ({})", invoice.getDocumentNo(), invoice.invoiceLabel());
                    return flow.act(actor, invoice, "POST", null, invoices.spec());
                });
    }

    /** Đảo: hóa đơn đã thanh toán / bù trừ thì 409 {@code HAS_DEPENDENT_DOCUMENTS} (BR-GEN-22). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseInvoiceResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + PurchaseInvoiceService.PATH + "/" + id + "/reverse", request,
                PurchaseInvoiceResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    PurchaseInvoice invoice = flow.lock(actor, id, invoices.spec());
                    if (invoice.getStatus() != PurchaseDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Hóa đơn "
                                + invoice.getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
                    }
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    applySources(invoice, BigDecimal.ONE.negate());
                    Instant now = Instant.now(clock);
                    costRepository.saveAll(costRepository.findOriginals(PurchaseInvoice.TYPE_CODE, invoice.getId())
                            .stream().map(entry -> entry.reversed(date, actor.id(), now)).toList());
                    if (invoice.getTotalAmount().signum() > 0) {
                        debtPosting.reverse(new DebtReversalCommand(invoice.getCompanyId(), invoice.getBranchId(),
                                PurchaseInvoice.TYPE_CODE, invoice.getId(), date, invoice.getDocumentNo(),
                                actor.id()));
                        journalPosting.reverse(new ReversalRequest(invoice.getCompanyId(), PurchaseInvoice.TYPE_CODE,
                                invoice.getId(), date, "Đảo " + invoice.getDocumentNo() + ": " + reason, actor.id()));
                    }
                    invoice.markReversed(actor.id(), now, date, reason);
                    return flow.act(actor, invoice, "REVERSE", reason, invoices.spec());
                });
    }

    /**
     * Khóa phiếu nhận rồi đơn mua theo id tăng dần; cộng ({@code sign} = 1) / trừ số đã nhận hóa đơn. Trả phiếu nhận
     * theo id dòng phiếu nhận.
     */
    private Map<Long, PurchaseReceipt> applySources(PurchaseInvoice invoice, BigDecimal sign) {
        Map<Long, PurchaseReceipt> byLine = new HashMap<>();
        List<Long> receiptLineIds = invoice.getLines().stream().map(PurchaseInvoiceLine::getReceiptLineId)
                .filter(Objects::nonNull).toList();
        if (!receiptLineIds.isEmpty()) {
            for (Long receiptId : receiptRepository.findIdsByLineIds(receiptLineIds)) {
                PurchaseReceipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                        .orElseThrow(BusinessException::notFound);
                receipt.getLines().forEach(line -> byLine.put(line.getId(), receipt));
            }
            for (PurchaseInvoiceLine line : invoice.getLines()) {
                if (line.isGoods()) {
                    byLine.get(line.getReceiptLineId()).recordInvoice(line.getReceiptLineId(),
                            line.getBaseQuantity().multiply(sign), "lines[" + (line.getLineNo() - 1) + "]");
                }
            }
            receiptRepository.saveAll(byLine.values().stream().distinct().toList());
        }
        List<Long> orderLineIds = invoice.getLines().stream().map(PurchaseInvoiceLine::getOrderLineId)
                .filter(Objects::nonNull).toList();
        if (!orderLineIds.isEmpty()) {
            for (Long orderId : orderRepository.findIdsByLineIds(orderLineIds)) {
                PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                        .orElseThrow(BusinessException::notFound);
                invoice.getLines().stream()
                        .filter(line -> line.getOrderLineId() != null && order.getLines().stream()
                                .anyMatch(orderLine -> orderLine.getId().equals(line.getOrderLineId())))
                        .forEach(line -> order.recordInvoice(line.getOrderLineId(),
                                line.getBaseQuantity().multiply(sign)));
                orderRepository.save(order);
            }
        }
        return byLine;
    }

    /** Chênh lệch giá hóa đơn so với giá tạm tính theo dòng phiếu nhận (BR-PUR-10); giá vốn tính lại ở Giai đoạn 8. */
    private void recordPriceDifferences(CurrentUser actor, PurchaseInvoice invoice,
                                        Map<Long, PurchaseReceipt> receipts) {
        Instant now = Instant.now(clock);
        List<PurchaseCostEntry> entries = new ArrayList<>();
        for (PurchaseInvoiceLine line : invoice.getLines()) {
            if (!line.isGoods()) {
                continue;
            }
            PurchaseReceipt receipt = receipts.get(line.getReceiptLineId());
            PurchaseReceiptLine receiptLine = receipt.getLines().stream()
                    .filter(candidate -> candidate.getId().equals(line.getReceiptLineId())).findFirst().orElseThrow();
            BigDecimal provisional = receiptLine.getUnitCost().multiply(line.getQuantity())
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal difference = line.getAmount().subtract(provisional);
            if (difference.signum() != 0) {
                entries.add(new PurchaseCostEntry(invoice.getCompanyId(), receiptLine, receipt.getWarehouseId(),
                        PurchaseCostEntry.EntryType.INVOICE_PRICE, invoice.getPostingDate(), difference,
                        new PurchaseCostEntry.Reference(PurchaseInvoice.TYPE_CODE, invoice.getId(), line.getId(),
                                invoice.getDocumentNo()), actor.id(), now));
            }
        }
        costRepository.saveAll(entries);
    }

    /** Dòng hạch toán: giá trị từng dòng theo loại hàng (hàng hóa / dịch vụ), thuế GTGT gộp một dòng (D-34). */
    private List<PostingLine> journalLines(PurchaseInvoice invoice, Map<Long, PurchaseReceipt> receipts) {
        Map<Long, ProductInfo> products = access.products(invoice.getLines().stream()
                .map(PurchaseInvoiceLine::getProductId).toList());
        List<PostingLine> lines = new ArrayList<>();
        for (PurchaseInvoiceLine line : invoice.getLines()) {
            if (line.getAmount().signum() == 0) {
                continue;
            }
            ProductInfo product = products.get(line.getProductId());
            Long warehouseId = line.isGoods() ? receipts.get(line.getReceiptLineId()).getWarehouseId() : null;
            lines.add(new PostingLine(PURCHASE, line.getAmount(), product == null ? null : product.productTypeCode(),
                    SUPPLIER_ROLE, null, null, invoice.getSupplierId(), line.getProductId(), warehouseId, null, null,
                    line.getId(), line.getDescription()));
        }
        if (invoice.getTaxAmount().signum() > 0) {
            lines.add(new PostingLine(PURCHASE_VAT, invoice.getTaxAmount(), null, SUPPLIER_ROLE, null, null,
                    invoice.getSupplierId(), null, null, null, null, null, "Thuế GTGT " + invoice.invoiceLabel()));
        }
        return lines;
    }

    private static String description(PurchaseInvoice invoice) {
        return invoice.getDescription() != null ? invoice.getDescription()
                : "Hóa đơn mua " + invoice.invoiceLabel();
    }
}
