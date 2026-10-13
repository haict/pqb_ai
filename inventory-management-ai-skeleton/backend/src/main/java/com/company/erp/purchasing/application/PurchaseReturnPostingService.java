package com.company.erp.purchasing.application;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.finance.application.DebtPostingService;
import com.company.erp.finance.application.DebtPostingService.CreditKind;
import com.company.erp.finance.application.DebtPostingService.DebtReversalCommand;
import com.company.erp.finance.application.DebtPostingService.InvoiceCreditCommand;
import com.company.erp.finance.application.DebtPostingService.InvoiceSide;
import com.company.erp.inventory.application.StockOutService;
import com.company.erp.inventory.application.StockOutService.StockOutCommand;
import com.company.erp.inventory.application.StockOutService.StockOutLine;
import com.company.erp.inventory.application.StockOutService.StockOutResult;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.PurchaseReturnResponse;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.PurchaseCostEntry;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.domain.PurchaseReturn;
import com.company.erp.purchasing.domain.PurchaseReturnLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseCostEntryRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ / đảo trả hàng nhà cung cấp và giảm giá hàng mua (BR-PUR-14, D-54). Trả hàng: số đã trả và giá trị đã giảm
 * của dòng đơn mua, phiếu xuất trả PXK đúng lô / serial, giảm công nợ phải trả, bút toán. Giảm giá: giá trị đã giảm của
 * dòng đơn mua, điều chỉnh giá trị nhập theo dòng, giảm công nợ, bút toán. Thứ tự khóa: chứng từ → đơn mua → kho →
 * công nợ → bút toán. READ COMMITTED.
 */@Service
public class PurchaseReturnPostingService {

    static final String POST = "purchase-return:post";
    static final String REVERSE = "purchase-return:reverse";
    static final String SUPPLIER_ROLE = "SUPPLIER";
    private static final Logger log = LoggerFactory.getLogger(PurchaseReturnPostingService.class);

    private final PurchaseReturnService returns;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseCostEntryRepository costRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final StockOutService stockOut;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public PurchaseReturnPostingService(PurchaseReturnService returns, PurchaseOrderRepository orderRepository,
                                        PurchaseCostEntryRepository costRepository, PurchaseDocumentFlow flow,
                                        PurchasingAccess access, StockOutService stockOut,
                                        DebtPostingService debtPosting, JournalPostingService journalPosting,
                                        BusinessCalendar calendar, Clock clock) {
        this.returns = returns;
        this.orderRepository = orderRepository;
        this.costRepository = costRepository;
        this.flow = flow;
        this.access = access;
        this.stockOut = stockOut;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReturnResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PurchaseReturnService.PATH + "/" + id + "/post", id,
                PurchaseReturnResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    PurchaseReturn document = flow.lock(actor, id, returns.spec());
                    if (document.getStatus() != PurchaseDocumentStatus.APPROVED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ "
                                + document.getDocumentNo() + " ở trạng thái " + document.getStatus()
                                + ", chỉ ghi sổ chứng từ đã duyệt.");
                    }
                    PurchaseOrder order = lockOrder(document);
                    applySources(document, order, BigDecimal.ONE);
                    Long inventoryId = null;
                    if (document.isGoodsReturn()) {
                        inventoryId = issue(actor, document).documentId();
                    } else {
                        recordDiscountCosts(actor, document, order);
                    }
                    if (document.getTotalAmount().signum() > 0) {
                        debtPosting.reduceInvoice(new InvoiceCreditCommand(document.getCompanyId(),
                                document.getBranchId(), InvoiceSide.PAYABLE, document.getSupplierId(),
                                PurchaseOrder.TYPE_CODE, order.getId(), document.typeCode(), document.getId(),
                                document.getDocumentNo(), document.getPostingDate(), document.getTotalAmount(),
                                document.isGoodsReturn() ? CreditKind.RETURN : CreditKind.DISCOUNT,
                                description(document), actor.id()));
                        journalPosting.post(new PostingRequest(document.getCompanyId(), document.getBranchId(),
                                document.getPostingDate(), document.typeCode(), document.getId(),
                                document.getDocumentNo(), description(document), actor.id(),
                                journalLines(document)));
                    }
                    document.markPosted(inventoryId, actor.id(), Instant.now(clock));
                    log.info("Ghi sổ {} {}", document.typeCode(), document.getDocumentNo());
                    return flow.act(actor, document, "POST", null, returns.spec());
                });
    }

    /** Đảo: hoàn số đã trả / đã giảm, đảo phiếu xuất trả, công nợ, bút toán (BR-GEN-21). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReturnResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + PurchaseReturnService.PATH + "/" + id + "/reverse", request,
                PurchaseReturnResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    PurchaseReturn document = flow.lock(actor, id, returns.spec());
                    if (document.getStatus() != PurchaseDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ "
                                + document.getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
                    }
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    applySources(document, lockOrder(document), BigDecimal.ONE.negate());
                    Instant now = Instant.now(clock);
                    if (document.getInventoryDocumentId() != null) {
                        stockOut.reverse(actor, document.getInventoryDocumentId(), date, reason);
                    }
                    costRepository.saveAll(costRepository.findOriginals(document.typeCode(), document.getId())
                            .stream().map(entry -> entry.reversed(date, actor.id(), now)).toList());
                    if (document.getTotalAmount().signum() > 0) {
                        debtPosting.reverseReduction(new DebtReversalCommand(document.getCompanyId(),
                                document.getBranchId(), document.typeCode(), document.getId(), date,
                                document.getDocumentNo(), actor.id()));
                        journalPosting.reverse(new ReversalRequest(document.getCompanyId(), document.typeCode(),
                                document.getId(), date, "Đảo " + document.getDocumentNo() + ": " + reason,
                                actor.id()));
                    }
                    document.markReversed(actor.id(), now, date, reason);
                    return flow.act(actor, document, "REVERSE", reason, returns.spec());
                });
    }

    private PurchaseOrder lockOrder(PurchaseReturn document) {
        return orderRepository.findByIdForUpdate(document.getPurchaseOrderId())
                .orElseThrow(BusinessException::notFound);
    }

    /** Cộng ({@code sign} = 1) / trừ số đã trả, giá trị đã giảm của dòng đơn mua. */
    private void applySources(PurchaseReturn document, PurchaseOrder order, BigDecimal sign) {
        for (PurchaseReturnLine line : document.getLines()) {
            String field = "lines[" + (line.getLineNo() - 1) + "]";
            if (document.isGoodsReturn()) {
                order.recordReturn(line.getOrderLineId(), line.getBaseQuantity().multiply(sign),
                        line.getAmount().multiply(sign), field);
            } else {
                order.recordDiscount(line.getOrderLineId(), line.getAmount().multiply(sign), field);
            }
        }
        orderRepository.save(order);
    }

    /** Phiếu xuất trả nhà cung cấp đúng lô, serial, trạng thái của dòng (BR-PUR-14). */
    private StockOutResult issue(CurrentUser actor, PurchaseReturn document) {
        List<StockOutLine> lines = document.getLines().stream()
                .map(line -> new StockOutLine(line.getId(), line.getProductId(), line.getUnitId(), line.getQuantity(),
                        line.getLotNo(), line.getQualityStatus(), "Trả đúng lô của đơn mua", line.getDescription(),
                        line.getSerialNumbers()))
                .toList();
        StockOutResult result = stockOut.issue(actor, new StockOutCommand(document.typeCode(), document.getId(),
                document.getDocumentNo(), StockOutService.PURCHASE_RETURN, document.getWarehouseId(),
                document.getSupplierId(), document.getPostingDate(), document.getPostingDate(),
                "Trả hàng theo " + document.getDocumentNo(), lines, null, null, List.of()));
        log.info("Trả hàng {} xuất kho {}", document.getDocumentNo(), result.documentNo());
        return result;
    }

    /** Giảm giá hàng hóa: điều chỉnh giảm giá trị nhập theo dòng đơn mua (giá vốn tính lại ở Giai đoạn 8). */
    private void recordDiscountCosts(CurrentUser actor, PurchaseReturn document, PurchaseOrder order) {
        Instant now = Instant.now(clock);
        List<PurchaseCostEntry> entries = new ArrayList<>();
        for (PurchaseReturnLine line : document.getLines()) {
            PurchaseOrderLine orderLine = order.line(line.getOrderLineId(), "lines");
            if (orderLine.isServiceLine() || line.getAmount().signum() == 0) {
                continue;
            }
            entries.add(new PurchaseCostEntry(document.getCompanyId(), orderLine, order.getWarehouseId(),
                    PurchaseCostEntry.EntryType.DISCOUNT, document.getPostingDate(), line.getAmount().negate(),
                    new PurchaseCostEntry.Reference(document.typeCode(), document.getId(), line.getId(),
                            document.getDocumentNo()), actor.id(), now));
        }
        costRepository.saveAll(entries);
    }

    /**
     * Bút toán theo quy tắc hạch toán của loại chứng từ: giá trị từng dòng theo loại hàng (trả hàng: PURCHASE_RETURN;
     * giảm giá: PURCHASE_DISCOUNT), thuế GTGT gộp một dòng (_VAT).
     */
    private List<PostingLine> journalLines(PurchaseReturn document) {
        String code = document.isGoodsReturn() ? "PURCHASE_RETURN" : "PURCHASE_DISCOUNT";
        Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                .map(PurchaseReturnLine::getProductId).toList());
        List<PostingLine> lines = new ArrayList<>();
        for (PurchaseReturnLine line : document.getLines()) {
            if (line.getAmount().signum() == 0) {
                continue;
            }
            ProductInfo product = products.get(line.getProductId());
            lines.add(new PostingLine(code, line.getAmount(), product == null ? null : product.productTypeCode(),
                    SUPPLIER_ROLE, null, null, document.getSupplierId(), line.getProductId(),
                    document.getWarehouseId(), null, null, line.getId(), line.getDescription()));
        }
        if (document.getTaxAmount().signum() > 0) {
            lines.add(new PostingLine(code + "_VAT", document.getTaxAmount(), null, SUPPLIER_ROLE, null, null,
                    document.getSupplierId(), null, null, null, null, null, "Thuế GTGT " + document.getDocumentNo()));
        }
        return lines;
    }

    private static String description(PurchaseReturn document) {
        if (document.getDescription() != null) {
            return document.getDescription();
        }
        return (document.isGoodsReturn() ? "Trả hàng nhà cung cấp " : "Giảm giá hàng mua ") + document.getDocumentNo();
    }
}
