package com.company.erp.sales.application;

import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.sales.application.dto.ConfirmDeliveryRequest;
import com.company.erp.sales.application.dto.QuickSaleRequest;
import com.company.erp.sales.application.dto.QuickSaleResponse;
import com.company.erp.sales.application.dto.SalesDeliveryLineRequest;
import com.company.erp.sales.application.dto.SalesDeliveryRequest;
import com.company.erp.sales.application.dto.SalesInvoiceLineRequest;
import com.company.erp.sales.application.dto.SalesInvoiceRequest;
import com.company.erp.sales.application.dto.SalesOrderRequest;
import com.company.erp.sales.domain.SalesDelivery;
import com.company.erp.sales.domain.SalesDeliveryLine;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesInvoice;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.system.application.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bán nhanh (BR-SAL-42, H-31): một thao tác sinh đơn bán → phiếu giao (xuất kho FEFO, khách nhận ngay) → hóa đơn bán
 * (ghi sổ công nợ, doanh thu), mỗi chứng từ riêng, liên kết, cùng một transaction (lỗi ở bước nào thì không lưu gì).
 * Cần quyền bán nhanh và quyền của từng bước (không nâng quyền). Đơn / phiếu giao / hóa đơn phải duyệt thì dừng ở bước
 * đó và trả các chứng từ đã lập. Phiếu thu lập riêng ở phân hệ thu chi `[Giả định]`.
 */
@Service
public class QuickSaleService {

    static final String PERMISSION = "sales-quick-sale:execute";

    private final SalesOrderService orders;
    private final SalesDeliveryService deliveries;
    private final SalesDeliveryPostingService deliveryPosting;
    private final SalesInvoiceService invoices;
    private final SalesInvoicePostingService invoicePosting;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final IdempotencyService idempotency;

    @SuppressWarnings("java:S107")
    public QuickSaleService(SalesOrderService orders, SalesDeliveryService deliveries,
                            SalesDeliveryPostingService deliveryPosting, SalesInvoiceService invoices,
                            SalesInvoicePostingService invoicePosting, SalesDocumentFlow flow, SalesAccess access,
                            IdempotencyService idempotency) {
        this.orders = orders;
        this.deliveries = deliveries;
        this.deliveryPosting = deliveryPosting;
        this.invoices = invoices;
        this.invoicePosting = invoicePosting;
        this.flow = flow;
        this.access = access;
        this.idempotency = idempotency;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QuickSaleResponse execute(CurrentUser actor, String key, QuickSaleRequest request) {
        return idempotency.execute(new IdempotentCommand(actor.id(), key, "POST /api/v1/sales/quick-sale", request),
                QuickSaleResponse.class, () -> run(actor, request));
    }

    private QuickSaleResponse run(CurrentUser actor, QuickSaleRequest request) {
        access.requirePermission(actor, PERMISSION);
        access.requirePermission(actor, SalesDeliveryPostingService.POST);
        access.requirePermission(actor, SalesDeliveryPostingService.DELIVER);
        boolean withInvoice = !Boolean.FALSE.equals(request.createInvoice());
        if (withInvoice) {
            access.requirePermission(actor, SalesInvoicePostingService.POST);
        }
        SalesOrder order = orders.createDocument(actor, new SalesOrderRequest(request.branchId(), request.customerId(),
                request.warehouseId(), request.documentDate(), null, request.deliveryAddress(), request.receiverName(),
                null, request.documentDate(), null, false, request.description(), request.lines(), List.of(), null));
        flow.submitLocked(actor, order, orders.spec());
        flow.act(actor, order, "SUBMIT", null, orders.spec());
        if (order.getStatus() != SalesDocumentStatus.APPROVED) {
            return new QuickSaleResponse(order.getId(), order.getDocumentNo(), order.getStatus(), null, null, null,
                    null, "Đơn bán phải duyệt (" + order.getApprovalReasons() + "); duyệt rồi giao hàng, lập hóa đơn.");
        }
        SalesDelivery delivery = null;
        List<SalesDeliveryLineRequest> goods = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            if (!line.isServiceLine()) {
                goods.add(new SalesDeliveryLineRequest(line.getId(), line.getQuantity(), null, null, List.of(), null));
            }
        }
        if (!goods.isEmpty()) {
            delivery = deliveries.createDocument(actor, new SalesDeliveryRequest(order.getId(), request.documentDate(),
                    null, null, null, null, null, "Bán nhanh " + order.getDocumentNo(), goods, null), null);
            flow.submitLocked(actor, delivery, deliveries.spec());
            if (delivery.getStatus() != SalesDocumentStatus.APPROVED) {
                flow.act(actor, delivery, "SUBMIT", null, deliveries.spec());
                return result(order, delivery, null, "Phiếu giao phải duyệt; duyệt rồi xuất kho.");
            }
            deliveryPosting.issueLocked(actor, delivery);
            deliveryPosting.confirmLocked(delivery, new ConfirmDeliveryRequest(request.documentDate(),
                    request.receiverName(), null, "Bán nhanh"));
            flow.act(actor, delivery, "QUICK_SALE", null, deliveries.spec());
        }
        if (!withInvoice) {
            return result(order, delivery, null, "Đã lập đơn bán và giao hàng.");
        }
        List<SalesInvoiceLineRequest> invoiceLines = new ArrayList<>();
        if (delivery != null) {
            for (SalesDeliveryLine line : delivery.getLines()) {
                invoiceLines.add(new SalesInvoiceLineRequest(line.getId(), null, line.getQuantity(), null));
            }
        }
        order.getLines().stream().filter(SalesOrderLine::isServiceLine).forEach(line -> invoiceLines.add(
                new SalesInvoiceLineRequest(null, line.getId(), line.getQuantity(), null)));
        SalesInvoice invoice = invoices.createDocument(actor, new SalesInvoiceRequest(order.getBranchId(),
                order.getCustomerId(), request.documentDate(), null, null, null, null, null, null,
                "Bán nhanh " + order.getDocumentNo(), invoiceLines, null));
        flow.submitLocked(actor, invoice, invoices.spec());
        if (invoice.getStatus() != SalesDocumentStatus.APPROVED) {
            flow.act(actor, invoice, "SUBMIT", null, invoices.spec());
            return result(order, delivery, invoice, "Hóa đơn phải duyệt; duyệt rồi ghi sổ.");
        }
        invoicePosting.postLocked(actor, invoice);
        flow.act(actor, invoice, "QUICK_SALE", null, invoices.spec());
        return result(order, delivery, invoice, "Đã lập đơn bán, giao hàng và hóa đơn bán.");
    }

    private static QuickSaleResponse result(SalesOrder order, SalesDelivery delivery, SalesInvoice invoice,
                                            String message) {
        return new QuickSaleResponse(order.getId(), order.getDocumentNo(), order.getStatus(),
                delivery == null ? null : delivery.getId(), delivery == null ? null : delivery.getDocumentNo(),
                invoice == null ? null : invoice.getId(), invoice == null ? null : invoice.getDocumentNo(), message);
    }
}
