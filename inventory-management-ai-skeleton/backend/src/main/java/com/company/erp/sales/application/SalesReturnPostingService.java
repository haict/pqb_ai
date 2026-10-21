package com.company.erp.sales.application;

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
import com.company.erp.inventory.application.StockInService;
import com.company.erp.inventory.application.StockInService.StockInCommand;
import com.company.erp.inventory.application.StockInService.StockInLine;
import com.company.erp.inventory.application.StockInService.StockInResult;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesReturnResponse;
import com.company.erp.sales.domain.SalesDocument;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesReturn;
import com.company.erp.sales.domain.SalesReturnLine;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
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
 * Ghi sổ / đảo trả hàng bán và giảm giá hàng bán (BR-SAL-32, 36, D-54). Trả hàng: số đã trả và giá trị đã giảm của
 * dòng đơn bán; nhập lại kho đúng lô / serial, mặc định CÁCH LY (PNK nguồn TLB qua {@link StockInService}); giảm công
 * nợ phải thu và bút toán giảm doanh thu. Giảm giá: giá trị đã giảm của dòng đơn, công nợ, bút toán; không đổi tồn. Giá
 * vốn hàng trả lại tính ở Giai đoạn 8 (phiếu nhập ghi đơn giá 0 `[Giả định]`). Thứ tự khóa: chứng từ → đơn bán → kho →
 * công nợ → bút toán (cùng chiều với ghi sổ đơn bán). READ COMMITTED.
 */@Service
public class SalesReturnPostingService {

    static final String POST = "sales-return:post";
    static final String REVERSE = "sales-return:reverse";
    static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final Logger log = LoggerFactory.getLogger(SalesReturnPostingService.class);

    private final SalesReturnService returns;
    private final SalesOrderRepository orderRepository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final StockInService stockIn;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public SalesReturnPostingService(SalesReturnService returns, SalesOrderRepository orderRepository,
                                     SalesDocumentFlow flow, SalesAccess access, StockInService stockIn,
                                     DebtPostingService debtPosting, JournalPostingService journalPosting,
                                     BusinessCalendar calendar, Clock clock) {
        this.returns = returns;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.stockIn = stockIn;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesReturnResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + SalesReturnService.PATH + "/" + id + "/post", id,
                SalesReturnResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    SalesReturn document = flow.lock(actor, id, returns.spec());
                    if (!SalesDocument.POSTABLE.contains(document.getStatus())) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ "
                                + document.getDocumentNo() + " ở trạng thái " + document.getStatus()
                                + ", chỉ ghi sổ chứng từ nháp.");
                    }
                    SalesOrder order = lockOrder(document);
                    applySources(document, order, BigDecimal.ONE);
                    Long inventoryId = document.isGoodsReturn() ? receive(actor, document).documentId() : null;
                    if (document.getTotalAmount().signum() > 0) {
                        debtPosting.reduceInvoice(new InvoiceCreditCommand(document.getCompanyId(),
                                document.getBranchId(), InvoiceSide.RECEIVABLE, document.getCustomerId(),
                                SalesOrder.TYPE_CODE, order.getId(), document.typeCode(), document.getId(),
                                document.getDocumentNo(), document.getPostingDate(), document.getTotalAmount(),
                                document.isGoodsReturn() ? CreditKind.RETURN : CreditKind.DISCOUNT,
                                description(document), actor.id()));
                        journalPosting.post(new PostingRequest(document.getCompanyId(), document.getBranchId(),
                                document.getPostingDate(), document.typeCode(), document.getId(),
                                document.getDocumentNo(), description(document), actor.id(), journalLines(document)));
                    }
                    document.markPosted(inventoryId, actor.id(), Instant.now(clock));
                    log.info("Ghi sổ {} {}", document.typeCode(), document.getDocumentNo());
                    return flow.act(actor, document, "POST", null, returns.spec());
                });
    }

    /** Đảo: hoàn số đã trả / đã giảm, đảo phiếu nhập lại (hàng đã xuất tiếp: 409), công nợ, bút toán (BR-GEN-21). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesReturnResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesReturnService.PATH + "/" + id + "/reverse", request,
                SalesReturnResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    SalesReturn document = flow.lock(actor, id, returns.spec());
                    if (document.getStatus() != SalesDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ "
                                + document.getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
                    }
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    applySources(document, lockOrder(document), BigDecimal.ONE.negate());
                    if (document.getInventoryDocumentId() != null) {
                        stockIn.reverse(actor, document.getInventoryDocumentId(), date, reason);
                    }
                    if (document.getTotalAmount().signum() > 0) {
                        debtPosting.reverseReduction(new DebtReversalCommand(document.getCompanyId(),
                                document.getBranchId(), document.typeCode(), document.getId(), date,
                                document.getDocumentNo(), actor.id()));
                        journalPosting.reverse(new ReversalRequest(document.getCompanyId(), document.typeCode(),
                                document.getId(), date, "Đảo " + document.getDocumentNo() + ": " + reason,
                                actor.id()));
                    }
                    document.markReversed(actor.id(), Instant.now(clock), date, reason);
                    return flow.act(actor, document, "REVERSE", reason, returns.spec());
                });
    }

    private SalesOrder lockOrder(SalesReturn document) {
        return orderRepository.findByIdForUpdate(document.getSalesOrderId()).orElseThrow(BusinessException::notFound);
    }

    /** Cộng ({@code sign} = 1) / trừ số đã trả, giá trị đã giảm của dòng đơn bán. */
    private void applySources(SalesReturn document, SalesOrder order, BigDecimal sign) {
        for (SalesReturnLine line : document.getLines()) {
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

    /** Nhập lại kho đúng lô, hạn dùng, serial; trạng thái theo dòng (mặc định CÁCH LY, BR-SAL-32). */
    private StockInResult receive(CurrentUser actor, SalesReturn document) {
        List<StockInLine> lines = document.getLines().stream()
                .map(line -> new StockInLine(line.getId(), line.getProductId(), line.getUnitId(), line.getQuantity(),
                        null, line.getLotNo(), null, line.getExpiryDate(), line.getQualityStatus(), BigDecimal.ZERO,
                        line.getDescription(), line.getSerialNumbers()))
                .toList();
        StockInResult result = stockIn.receive(actor, new StockInCommand(document.typeCode(), document.getId(),
                document.getDocumentNo(), document.getWarehouseId(), document.getCustomerId(),
                document.getPostingDate(), document.getPostingDate(), "Hàng bán bị trả lại " + document.getDocumentNo(),
                lines, StockInService.SALES_RETURN));
        log.info("Trả hàng bán {} nhập kho {}", document.getDocumentNo(), result.documentNo());
        return result;
    }

    /**
     * Bút toán theo quy tắc của loại chứng từ: giảm doanh thu từng dòng theo loại hàng (trả hàng: SALES_RETURN; giảm
     * giá: SALES_DISCOUNT), thuế GTGT đầu ra gộp một dòng (_VAT).
     */
    private List<PostingLine> journalLines(SalesReturn document) {
        String code = document.isGoodsReturn() ? "SALES_RETURN" : "SALES_DISCOUNT";
        Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                .map(SalesReturnLine::getProductId).toList());
        List<PostingLine> lines = new ArrayList<>();
        for (SalesReturnLine line : document.getLines()) {
            if (line.getAmount().signum() == 0) {
                continue;
            }
            ProductInfo product = products.get(line.getProductId());
            lines.add(new PostingLine(code, line.getAmount(), product == null ? null : product.productTypeCode(),
                    CUSTOMER_ROLE, null, null, document.getCustomerId(), line.getProductId(), null, null, null,
                    line.getId(), line.getDescription()));
        }
        if (document.getTaxAmount().signum() > 0) {
            lines.add(new PostingLine(code + "_VAT", document.getTaxAmount(), null, CUSTOMER_ROLE, null, null,
                    document.getCustomerId(), null, null, null, null, null, "Thuế GTGT " + document.getDocumentNo()));
        }
        return lines;
    }

    private static String description(SalesReturn document) {
        if (document.getDescription() != null) {
            return document.getDescription();
        }
        return (document.isGoodsReturn() ? "Hàng bán bị trả lại " : "Giảm giá hàng bán ") + document.getDocumentNo();
    }
}
