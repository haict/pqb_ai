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
import com.company.erp.inventory.application.InventoryReservationService;
import com.company.erp.inventory.application.InventoryReservationService.ReservationUse;
import com.company.erp.inventory.application.StockOutService;
import com.company.erp.inventory.application.StockOutService.IssuedItem;
import com.company.erp.inventory.application.StockOutService.StockOutCommand;
import com.company.erp.inventory.application.StockOutService.StockOutLine;
import com.company.erp.inventory.application.StockOutService.StockOutResult;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.ConfirmDeliveryRequest;
import com.company.erp.sales.application.dto.EinvoiceReferenceRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesOrderResponse;
import com.company.erp.sales.application.dto.ShipRequest;
import com.company.erp.sales.domain.SalesDocument;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderItem;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.infrastructure.persistence.SalesComplaintRepository;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ / đảo đơn bán (D-54, BR-SAL-21, 30, BR mục 5.3) trong một transaction: phiếu xuất kho PXK liên kết đơn (FEFO
 * hoặc lô chỉ định, dùng phần hàng đã giữ cho đơn, kiểm tra hạn dùng còn lại tối thiểu của khách D-07), công nợ phải
 * thu (một khoản cho cả đơn, nhân viên kinh doanh), bút toán doanh thu và thuế GTGT đầu ra theo quy tắc hạch toán DBH;
 * giá vốn ghi ở Giai đoạn 8. Theo dõi giao hàng (đang giao, khách nhận) và số hóa đơn điện tử. Giao thất bại = đảo đơn
 * (BR-SAL-23). Thứ tự khóa: đơn bán → (kho: kỳ, tổng (hàng, kho), giữ chỗ, tồn chi tiết, serial) → khoản công nợ → bút
 * toán. READ COMMITTED như ghi sổ kho.
 */
@Service
public class SalesOrderPostingService {

    static final String POST = "sales-order:post";
    static final String DELIVER = "sales-order:deliver";
    static final String REVERSE = "sales-order:reverse";
    static final String UNPOST = "sales-order:unpost";
    static final String SALES = "SALES";
    static final String SALES_VAT = "SALES_VAT";
    static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final Logger log = LoggerFactory.getLogger(SalesOrderPostingService.class);

    private final SalesOrderService orders;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final StockOutService stockOut;
    private final InventoryReservationService reservations;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final SalesComplaintRepository complaints;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public SalesOrderPostingService(SalesOrderService orders, SalesDocumentFlow flow, SalesAccess access,
                                    StockOutService stockOut, InventoryReservationService reservations,
                                    DebtPostingService debtPosting, JournalPostingService journalPosting,
                                    SalesComplaintRepository complaints, BusinessCalendar calendar, Clock clock) {
        this.orders = orders;
        this.flow = flow;
        this.access = access;
        this.stockOut = stockOut;
        this.reservations = reservations;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.complaints = complaints;
        this.calendar = calendar;
        this.clock = clock;
    }

    /**
     * Ghi sổ đơn từ nháp (D-61, hoặc đơn đã duyệt trước D-61): kiểm tra thẩm quyền giá / tín dụng, xuất kho, doanh thu,
     * công nợ, bút toán.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + SalesOrderService.PATH + "/" + id + "/post", id,
                SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    SalesOrder order = flow.lock(actor, id, orders.spec());
                    if (!SalesDocument.POSTABLE.contains(order.getStatus())) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + order.getDocumentNo()
                                + " ở trạng thái " + order.getStatus() + ", chỉ ghi sổ đơn nháp.");
                    }
                    String authorityNote = orders.requirePostingAuthority(actor, order);
                    Issue issue = issue(actor, order);
                    reservations.releaseAll(order.getCompanyId(), SalesOrder.TYPE_CODE, order.getId());
                    order.markPosted(issue.inventoryId(), issue.items(), actor.id(), Instant.now(clock));
                    if (order.getTotalAmount().signum() > 0) {
                        debtPosting.recordInvoice(new InvoiceDebtCommand(order.getCompanyId(), order.getBranchId(),
                                InvoiceSide.RECEIVABLE, order.getCustomerId(), SalesOrder.TYPE_CODE, order.getId(),
                                order.getDocumentNo(), order.invoiceLabel(), order.getPostingDate(),
                                order.getDueDate(), order.getTotalAmount(), order.getPostingDate(),
                                order.getSalesUserId(), description(order), actor.id()));
                        journalPosting.post(new PostingRequest(order.getCompanyId(), order.getBranchId(),
                                order.getPostingDate(), SalesOrder.TYPE_CODE, order.getId(), order.getDocumentNo(),
                                description(order), actor.id(), journalLines(order)));
                    }
                    log.info("Ghi sổ đơn bán {}", order.getDocumentNo());
                    return flow.act(actor, order, "POST", authorityNote, orders.spec());
                });
    }

    /**
     * Đảo đơn đã ghi sổ (lập sai, hoặc giao thất bại: hàng về kho, BR-SAL-23); bắt buộc lý do. Còn trả hàng / giảm
     * giá chưa đảo, hàng đã xuất tiếp, đã thu tiền / bù trừ: 409 {@code HAS_DEPENDENT_DOCUMENTS}.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesOrderService.PATH + "/" + id + "/reverse", request,
                SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    SalesOrder order = flow.lock(actor, id, orders.spec());
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    Long inventoryId = order.getInventoryDocumentId();
                    order.markReversed(actor.id(), Instant.now(clock), date, reason);
                    undoLedgers(actor, order, inventoryId, date, "Đảo", reason);
                    return flow.act(actor, order, "REVERSE", reason, orders.spec());
                });
    }

    /**
     * Bỏ ghi sổ để sửa lại (D-62): dòng ngược ngày hạch toán gốc (kỳ gốc phải còn mở), đơn về nháp giữ số, không giữ
     * hàng lại (người dùng giữ hàng trên đơn nháp nếu cần). Chặn như đảo khi có chứng từ phụ thuộc hoặc khiếu nại;
     * đã có hóa đơn điện tử hoặc đã chuyển đi giao thì chỉ đảo được.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesOrderResponse unpost(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesOrderService.PATH + "/" + id + "/unpost", request,
                SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, UNPOST);
                    SalesOrder order = flow.lock(actor, id, orders.spec());
                    if (complaints.existsBySalesOrderId(order.getId())) {
                        throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Đơn bán "
                                + order.getDocumentNo() + " đã có khiếu nại, không bỏ ghi sổ được.");
                    }
                    String reason = request.reason().trim();
                    Long inventoryId = order.getInventoryDocumentId();
                    order.markUnposted(reason);
                    undoLedgers(actor, order, inventoryId, order.getPostingDate(), "Bỏ ghi sổ", reason);
                    log.info("Bỏ ghi sổ đơn bán {}", order.getDocumentNo());
                    return flow.act(actor, order, "UNPOST", reason, orders.spec());
                });
    }

    /** Dòng ngược cho phiếu xuất kho, công nợ, bút toán của lần ghi sổ hiện hành (BR-GEN-21). */
    private void undoLedgers(CurrentUser actor, SalesOrder order, Long inventoryId, LocalDate date, String action,
                             String reason) {
        if (inventoryId != null) {
            stockOut.reverse(actor, inventoryId, date, reason);
        }
        if (order.getTotalAmount().signum() > 0) {
            debtPosting.reverse(new DebtReversalCommand(order.getCompanyId(), order.getBranchId(),
                    SalesOrder.TYPE_CODE, order.getId(), date, order.getDocumentNo(), actor.id()));
            journalPosting.reverse(new ReversalRequest(order.getCompanyId(), SalesOrder.TYPE_CODE, order.getId(),
                    date, action + " " + order.getDocumentNo() + ": " + reason, actor.id()));
        }
    }

    /** Chuyển sang đang giao (BR-SAL-22); ghi người / đơn vị vận chuyển. */
    @Transactional
    public SalesOrderResponse ship(CurrentUser actor, Long id, String key, ShipRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesOrderService.PATH + "/" + id + "/ship", request,
                SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, DELIVER);
                    SalesOrder order = flow.lock(actor, id, orders.spec());
                    order.markShipped(request == null ? null : SalesAccess.trim(request.shipperName()),
                            Instant.now(clock));
                    return flow.act(actor, order, "SHIP", null, orders.spec());
                });
    }

    /** Khách nhận hàng (BR-SAL-22): ngày nhận, người nhận, số biên bản đã ký. */
    @Transactional
    public SalesOrderResponse confirm(CurrentUser actor, Long id, String key, ConfirmDeliveryRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesOrderService.PATH + "/" + id + "/confirm", request,
                SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, DELIVER);
                    SalesOrder order = flow.lock(actor, id, orders.spec());
                    order.markDelivered(request.receivedDate(), SalesAccess.trim(request.receivedBy()),
                            SalesAccess.trim(request.signedDocumentRef()), SalesAccess.trim(request.note()));
                    return flow.act(actor, order, "CONFIRM", null, orders.spec());
                });
    }

    /**
     * Ghi số hóa đơn điện tử đã phát hành trên MISA (BR-SAL-31), trước hoặc sau khi ghi sổ; số đã dùng ở đơn khác: 409
     * {@code DUPLICATE_DOCUMENT}. Nhãn công nợ của đơn đã ghi sổ giữ nguyên số đơn `[Giả định]`.
     */
    @Transactional
    public SalesOrderResponse recordEinvoice(CurrentUser actor, Long id, String key, EinvoiceReferenceRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesOrderService.PATH + "/" + id + "/einvoice-reference",
                request, SalesOrderResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    SalesOrder order = flow.lock(actor, id, orders.spec());
                    // Kiểm tra trước khi đổi entity: truy vấn sau khi đổi sẽ tự flush và vướng ràng buộc unique.
                    orders.requireUniqueEinvoice(order, SalesOrder.einvoiceKey(request.series(), request.number()));
                    order.recordEinvoice(new SalesOrder.EinvoiceReference(request.series(), request.number(),
                            request.date(), SalesAccess.trim(request.taxAuthorityCode())));
                    return flow.act(actor, order, "EINVOICE_REFERENCE", order.invoiceLabel(), orders.spec());
                });
    }

    private record Issue(Long inventoryId, Map<Long, List<SalesOrderItem>> items) {
    }

    /** Phiếu xuất kho cho dòng hàng hóa, dùng phần hàng đã giữ cho đơn; trống khi đơn chỉ có dịch vụ. */
    private Issue issue(CurrentUser actor, SalesOrder order) {
        List<SalesOrderLine> goods = order.goodsLines();
        if (goods.isEmpty()) {
            return new Issue(null, Map.of());
        }
        List<ReservationUse> uses = goods.stream()
                .map(line -> new ReservationUse(line.getId(), line.getProductId(), order.getWarehouseId(),
                        line.getBaseQuantity()))
                .toList();
        StockOutResult result = stockOut.issue(actor, new StockOutCommand(SalesOrder.TYPE_CODE, order.getId(),
                order.getDocumentNo(), StockOutService.SALE, order.getWarehouseId(), order.getCustomerId(),
                order.getPostingDate(), order.getPostingDate(), "Xuất bán theo " + order.getDocumentNo(),
                goods.stream().map(SalesOrderPostingService::toStockLine).toList(), SalesOrder.TYPE_CODE,
                order.getId(), uses));
        requireShelfLife(order, result.items());
        Map<Long, List<SalesOrderItem>> items = new HashMap<>();
        for (IssuedItem item : result.items()) {
            items.computeIfAbsent(item.sourceLineId(), ignored -> new ArrayList<>()).add(new SalesOrderItem(
                    item.lotId(), item.lotNo(), item.expiryDate(), item.serialNo(), item.qualityStatus(),
                    item.quantity()));
        }
        log.info("Đơn bán {} xuất kho {}", order.getDocumentNo(), result.documentNo());
        return new Issue(result.documentId(), items);
    }

    /** Hạn dùng còn lại tối thiểu theo khách (D-07): lô xuất phải còn hạn ít nhất số ngày khách yêu cầu. */
    private void requireShelfLife(SalesOrder order, List<IssuedItem> items) {
        CustomerProfile customer = access.customerProfile(order.getCustomerId());
        Integer days = customer.minRemainingShelfLifeDays();
        if (days == null || days <= 0) {
            return;
        }
        LocalDate limit = order.getPostingDate().plusDays(days);
        items.stream().filter(item -> item.expiryDate() != null && item.expiryDate().isBefore(limit)).findFirst()
                .ifPresent(item -> {
                    throw new BusinessException(ErrorCode.LOT_NOT_SELLABLE, "Lô " + item.lotNo() + " hết hạn ngày "
                            + item.expiryDate() + ", còn dưới " + days + " ngày khách " + customer.code()
                            + " yêu cầu (D-07); chọn lô khác.");
                });
    }

    private static StockOutLine toStockLine(SalesOrderLine line) {
        return new StockOutLine(line.getId(), line.getProductId(), line.getUnitId(), line.getQuantity(),
                line.getLotNo(), null, line.getLotOverrideReason(), line.getDescription(),
                List.copyOf(line.getSerialNumbers()));
    }

    /** Dòng hạch toán: doanh thu từng dòng theo loại hàng (hàng hóa / dịch vụ), thuế GTGT đầu ra gộp một dòng. */
    private List<PostingLine> journalLines(SalesOrder order) {
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(SalesOrderLine::getProductId).toList());
        List<PostingLine> lines = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            if (line.getAmount().signum() == 0) {
                continue;
            }
            ProductInfo product = products.get(line.getProductId());
            lines.add(new PostingLine(SALES, line.getAmount(), product == null ? null : product.productTypeCode(),
                    CUSTOMER_ROLE, null, null, order.getCustomerId(), line.getProductId(), null, null, null,
                    line.getId(), line.getDescription()));
        }
        if (order.getTaxAmount().signum() > 0) {
            lines.add(new PostingLine(SALES_VAT, order.getTaxAmount(), null, CUSTOMER_ROLE, null, null,
                    order.getCustomerId(), null, null, null, null, null, "Thuế GTGT " + order.invoiceLabel()));
        }
        return lines;
    }

    private static String description(SalesOrder order) {
        return order.getDescription() != null ? order.getDescription() : "Bán hàng " + order.invoiceLabel();
    }
}
