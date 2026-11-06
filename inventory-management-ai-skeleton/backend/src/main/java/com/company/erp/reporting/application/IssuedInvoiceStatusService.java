package com.company.erp.reporting.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.dto.IssuedInvoiceStatusResponse;
import com.company.erp.reporting.infrastructure.IssuedInvoiceStatusJdbc;
import com.company.erp.reporting.infrastructure.IssuedInvoiceStatusJdbc.OrderRow;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.Scope;
import com.company.erp.system.application.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cột "đã / chưa xuất hóa đơn" của màn hình Tồn kho (D-68): số lượng xuất bán trong kỳ theo trạng thái số hóa đơn của
 * đơn bán, và danh sách đơn cấu thành. Theo phạm vi kho của người xem, quyền {@code inventory-report:view}.
 */
@Service
public class IssuedInvoiceStatusService {

    private static final int MAX_PRODUCTS = 200;
    private static final int MAX_ORDERS = 1000;

    private final IssuedInvoiceStatusJdbc jdbc;
    private final ReportAccess access;

    public IssuedInvoiceStatusService(IssuedInvoiceStatusJdbc jdbc, ReportAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public IssuedInvoiceStatusResponse quantities(CurrentUser actor, Long companyId, Long warehouseId,
                                                  List<Long> productIds, LocalDate from, LocalDate to) {
        access.requirePermission(actor, StockLedgerReportService.VIEW);
        ReportAccess.requireRange(from, to);
        if (productIds != null && productIds.size() > MAX_PRODUCTS) {
            throw BusinessException.invalidField("productIds", "Chọn tối đa " + MAX_PRODUCTS + " hàng mỗi lần xem.");
        }
        Scope scope = new Scope(companyId, access.warehouses(actor, companyId, warehouseId));
        List<IssuedInvoiceStatusResponse.Row> rows = jdbc.quantities(scope, productIds, from, to).stream()
                .map(row -> new IssuedInvoiceStatusResponse.Row(row.productId(), row.warehouseId(),
                        row.invoicedQuantity(), row.uninvoicedQuantity()))
                .toList();
        return new IssuedInvoiceStatusResponse(from, to, rows);
    }

    @Transactional(readOnly = true)
    public IssuedInvoiceStatusResponse.Orders orders(CurrentUser actor, Long companyId, Long warehouseId,
                                                     Long productId, boolean invoiced, LocalDate from, LocalDate to) {
        access.requirePermission(actor, StockLedgerReportService.VIEW);
        ReportAccess.requireRange(from, to);
        Scope scope = new Scope(companyId, access.warehouses(actor, companyId, warehouseId));
        List<OrderRow> rows = jdbc.orders(scope, productId, invoiced, from, to, MAX_ORDERS + 1);
        List<IssuedInvoiceStatusResponse.Order> orders = rows.stream().limit(MAX_ORDERS)
                .map(row -> new IssuedInvoiceStatusResponse.Order(row.orderId(), row.documentNo(), row.postingDate(),
                        row.customerCode(), row.customerName(), row.einvoiceSeries(), row.einvoiceNo(),
                        row.einvoiceDate(), row.quantity()))
                .toList();
        return new IssuedInvoiceStatusResponse.Orders(orders, rows.size() > MAX_ORDERS);
    }
}
