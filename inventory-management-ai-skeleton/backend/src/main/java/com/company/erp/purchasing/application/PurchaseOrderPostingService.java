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
import com.company.erp.inventory.application.StockInService;
import com.company.erp.inventory.application.StockInService.StockInCommand;
import com.company.erp.inventory.application.StockInService.StockInLine;
import com.company.erp.inventory.application.StockInService.StockInResult;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.PurchaseOrderResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.PurchaseDocument;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
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
 * Ghi sổ / đảo đơn mua hàng (D-54, BR-PUR-08, 09, BR mục 5.3) trong một transaction: phiếu nhập kho PNK liên kết đơn
 * mua (dòng hàng hóa, đúng lô / hạn dùng / serial, đơn giá sau chiết khấu), trừ hàng đang về (đơn đã duyệt trước D-61),
 * công nợ phải trả theo hóa đơn (hoặc theo đơn khi không có hóa đơn), bút toán theo quy tắc hạch toán DMH. Thứ tự khóa:
 * đơn mua → (kho: kỳ, trạng thái kho, tổng tồn, tồn chi tiết, serial) → tổng tồn cho hàng đang về → khoản công nợ → bút
 * toán. READ COMMITTED như ghi sổ kho (ADR-08).
 */
@Service
public class PurchaseOrderPostingService {

    static final String POST = "purchase-order:post";
    static final String REVERSE = "purchase-order:reverse";
    static final String UNPOST = "purchase-order:unpost";
    static final String POST_OVER_THRESHOLD = "purchase-order:post-over-threshold";
    static final String THRESHOLD = "purchasing.order-approval-threshold";
    static final String PURCHASE = "PURCHASE";
    static final String PURCHASE_VAT = "PURCHASE_VAT";
    static final String SUPPLIER_ROLE = "SUPPLIER";
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderPostingService.class);

    private final PurchaseOrderService orders;
    private final PurchaseOrderEffects effects;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final StockInService stockIn;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final ParameterService parameters;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public PurchaseOrderPostingService(PurchaseOrderService orders, PurchaseOrderEffects effects,
                                       PurchaseDocumentFlow flow, PurchasingAccess access, StockInService stockIn,
                                       DebtPostingService debtPosting, JournalPostingService journalPosting,
                                       ParameterService parameters, BusinessCalendar calendar, Clock clock) {
        this.orders = orders;
        this.effects = effects;
        this.flow = flow;
        this.access = access;
        this.stockIn = stockIn;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PurchaseOrderService.PATH + "/" + id + "/post", id,
                PurchaseOrderResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    PurchaseOrder order = flow.lock(actor, id, orders.spec());
                    if (!PurchaseDocument.POSTABLE.contains(order.getStatus())) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn mua "
                                + order.getDocumentNo() + " ở trạng thái " + order.getStatus()
                                + ", chỉ ghi sổ đơn nháp.");
                    }
                    String authorityNote = requireThresholdAuthority(actor, order);
                    boolean hadIncoming = order.getStatus() == PurchaseDocumentStatus.APPROVED;
                    Long inventoryId = receive(actor, order);
                    // Chỉ đơn đã duyệt trước D-61 còn hàng đang về; đơn nháp chưa từng cộng.
                    if (hadIncoming) {
                        effects.released(order);
                    }
                    if (order.getTotalAmount().signum() > 0) {
                        debtPosting.recordInvoice(new InvoiceDebtCommand(order.getCompanyId(), order.getBranchId(),
                                InvoiceSide.PAYABLE, order.getSupplierId(), PurchaseOrder.TYPE_CODE, order.getId(),
                                order.getDocumentNo(), order.invoiceLabel(),
                                order.hasInvoice() ? order.getInvoiceDate() : order.getPostingDate(),
                                order.getDueDate(), order.getTotalAmount(), order.getPostingDate(), null,
                                description(order), actor.id()));
                        journalPosting.post(new PostingRequest(order.getCompanyId(), order.getBranchId(),
                                order.getPostingDate(), PurchaseOrder.TYPE_CODE, order.getId(),
                                order.getDocumentNo(), description(order), actor.id(), journalLines(order)));
                    }
                    order.markPosted(inventoryId, actor.id(), Instant.now(clock));
                    log.info("Ghi sổ đơn mua {} ({})", order.getDocumentNo(), order.invoiceLabel());
                    return flow.act(actor, order, "POST", authorityNote, orders.spec());
                });
    }

    /**
     * D-61: đơn mua có tổng tiền từ ngưỡng {@code purchasing.order-approval-threshold} trở lên chỉ người có quyền ghi
     * sổ vượt ngưỡng được ghi sổ (thay cho bước duyệt). Trả ghi chú audit khi dùng quyền này.
     */
    private String requireThresholdAuthority(CurrentUser actor, PurchaseOrder order) {
        BigDecimal threshold = parameters.decimalValue(order.getCompanyId(), THRESHOLD);
        if (order.getTotalAmount().compareTo(threshold) < 0) {
            return null;
        }
        String amount = threshold.stripTrailingZeros().toPlainString();
        if (!actor.hasPermission(POST_OVER_THRESHOLD)) {
            throw new BusinessException(ErrorCode.AUTHORITY_LIMIT_EXCEEDED, "Đơn mua " + order.getDocumentNo()
                    + " có tổng tiền từ ngưỡng " + amount + " đồng trở lên; cần người có quyền ghi sổ đơn mua vượt "
                    + "ngưỡng.");
        }
        return "Ghi sổ đơn mua vượt ngưỡng " + amount + " đồng";
    }

    /**
     * Đảo: còn trả hàng / giảm giá / phân bổ chưa đảo, hàng đã xuất, đã thanh toán / bù trừ thì 409
     * {@code HAS_DEPENDENT_DOCUMENTS} (BR-GEN-04). Hàng đang về không cộng lại (đơn đã kết thúc).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + PurchaseOrderService.PATH + "/" + id + "/reverse", request,
                PurchaseOrderResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    PurchaseOrder order = flow.lock(actor, id, orders.spec());
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    Long inventoryId = order.getInventoryDocumentId();
                    order.markReversed(actor.id(), Instant.now(clock), date, reason);
                    undoLedgers(actor, order, inventoryId, date, "Đảo", reason);
                    return flow.act(actor, order, "REVERSE", reason, orders.spec());
                });
    }

    /**
     * Bỏ ghi sổ để sửa lại (D-62): dòng ngược ngày hạch toán gốc (kỳ gốc phải còn mở), đơn về nháp giữ số; chặn như
     * đảo khi có chứng từ phụ thuộc (409 {@code HAS_DEPENDENT_DOCUMENTS}).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse unpost(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.idempotent(actor, key, "POST " + PurchaseOrderService.PATH + "/" + id + "/unpost", request,
                PurchaseOrderResponse.class, () -> {
                    access.requirePermission(actor, UNPOST);
                    PurchaseOrder order = flow.lock(actor, id, orders.spec());
                    String reason = request.reason().trim();
                    Long inventoryId = order.getInventoryDocumentId();
                    order.markUnposted(reason);
                    undoLedgers(actor, order, inventoryId, order.getPostingDate(), "Bỏ ghi sổ", reason);
                    log.info("Bỏ ghi sổ đơn mua {}", order.getDocumentNo());
                    return flow.act(actor, order, "UNPOST", reason, orders.spec());
                });
    }

    /** Dòng ngược cho phiếu nhập kho, công nợ, bút toán của lần ghi sổ hiện hành (BR-GEN-21). */
    private void undoLedgers(CurrentUser actor, PurchaseOrder order, Long inventoryId, LocalDate date, String action,
                             String reason) {
        if (inventoryId != null) {
            stockIn.reverse(actor, inventoryId, date, reason);
        }
        if (order.getTotalAmount().signum() > 0) {
            debtPosting.reverse(new DebtReversalCommand(order.getCompanyId(), order.getBranchId(),
                    PurchaseOrder.TYPE_CODE, order.getId(), date, order.getDocumentNo(), actor.id()));
            journalPosting.reverse(new ReversalRequest(order.getCompanyId(), PurchaseOrder.TYPE_CODE,
                    order.getId(), date, action + " " + order.getDocumentNo() + ": " + reason, actor.id()));
        }
    }

    /** Phiếu nhập kho cho dòng hàng hóa; {@code null} khi đơn chỉ có dịch vụ. */
    private Long receive(CurrentUser actor, PurchaseOrder order) {
        List<StockInLine> lines = order.getLines().stream().filter(line -> !line.isServiceLine())
                .map(PurchaseOrderPostingService::toStockLine).toList();
        if (lines.isEmpty()) {
            return null;
        }
        StockInResult result = stockIn.receive(actor, new StockInCommand(PurchaseOrder.TYPE_CODE, order.getId(),
                order.getDocumentNo(), order.getWarehouseId(), order.getSupplierId(), order.getPostingDate(),
                order.getPostingDate(), "Nhập theo " + order.getDocumentNo(), lines));
        log.info("Đơn mua {} nhập kho {}", order.getDocumentNo(), result.documentNo());
        return result.documentId();
    }

    private static StockInLine toStockLine(PurchaseOrderLine line) {
        return new StockInLine(line.getId(), line.getProductId(), line.getUnitId(), line.getQuantity(),
                line.getLocationId(), line.getLotNo(), line.getManufactureDate(), line.getExpiryDate(), null,
                line.netUnitPrice(), line.getNote(), List.copyOf(line.getSerialNumbers()));
    }

    /** Dòng hạch toán: giá trị sau chiết khấu từng dòng theo loại hàng (hàng hóa / dịch vụ), thuế GTGT gộp một dòng. */
    private List<PostingLine> journalLines(PurchaseOrder order) {
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(PurchaseOrderLine::getProductId).toList());
        List<PostingLine> lines = new ArrayList<>();
        for (PurchaseOrderLine line : order.getLines()) {
            if (line.netAmount().signum() == 0) {
                continue;
            }
            ProductInfo product = products.get(line.getProductId());
            lines.add(new PostingLine(PURCHASE, line.netAmount(), product == null ? null : product.productTypeCode(),
                    SUPPLIER_ROLE, null, null, order.getSupplierId(), line.getProductId(),
                    line.isServiceLine() ? null : order.getWarehouseId(), null, null, line.getId(), line.getNote()));
        }
        if (order.getTaxAmount().signum() > 0) {
            lines.add(new PostingLine(PURCHASE_VAT, order.getTaxAmount(), null, SUPPLIER_ROLE, null, null,
                    order.getSupplierId(), null, null, null, null, null, "Thuế GTGT " + order.invoiceLabel()));
        }
        return lines;
    }

    private static String description(PurchaseOrder order) {
        return order.getDescription() != null ? order.getDescription() : "Mua hàng " + order.invoiceLabel();
    }
}
