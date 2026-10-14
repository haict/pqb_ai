package com.company.erp.reporting.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.dto.OrderReportResponse.BacklogRow;
import com.company.erp.reporting.application.dto.OrderReportResponse.JournalRow;
import com.company.erp.reporting.application.dto.OrderReportResponse.JournalTotals;
import com.company.erp.reporting.application.dto.OrderReportResponse.OrderProgress;
import com.company.erp.reporting.application.dto.OrderReportResponse.SalesJournal;
import com.company.erp.reporting.application.dto.OrderReportResponse.StockBacklog;
import com.company.erp.reporting.infrastructure.BacklogReportJdbc;
import com.company.erp.reporting.infrastructure.OrderProgressJdbc;
import com.company.erp.reporting.infrastructure.OrderProgressJdbc.Filter;
import com.company.erp.reporting.infrastructure.SalesJournalJdbc;
import com.company.erp.reporting.infrastructure.SalesJournalJdbc.Row;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo bán hàng (BR-RPT-26): tình hình thực hiện đơn bán (giao hàng, trả lại, đã thu, còn phải thu), sổ nhật ký bán
 * hàng theo chứng từ, tồn so với đặt hàng chưa giao (H-06). Phạm vi chi nhánh / kho và "chỉ dữ liệu của mình"
 * (BR-RPT-12, BR-SYS-04).
 */
@Service
public class SalesOrderReportService {

    static final String VIEW = "sales-order-report:view";
    static final int MAX_PRODUCTS = 200;

    private final OrderProgressJdbc orders;
    private final SalesJournalJdbc journal;
    private final BacklogReportJdbc backlog;
    private final OrderProgressAssembler assembler;
    private final ReportAccess access;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public SalesOrderReportService(OrderProgressJdbc orders, SalesJournalJdbc journal, BacklogReportJdbc backlog,
                                   OrderProgressAssembler assembler, ReportAccess access, BusinessCalendar calendar,
                                   Clock clock) {
        this.orders = orders;
        this.journal = journal;
        this.backlog = backlog;
        this.assembler = assembler;
        this.access = access;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderProgress orderProgress(CurrentUser actor, Long companyId, Long branchId, Long customerId,
                                       String status, LocalDate from, LocalDate to) {
        Set<Long> branches = scope(actor, companyId, branchId, from, to);
        return assembler.progress("DBH", from, to, orders.salesOrders(new Filter(companyId, branches, from, to,
                customerId, OrderProgressAssembler.status(status), ReportAccess.owner(actor))), calendar.today(),
                clock.instant());
    }

    @Transactional(readOnly = true)
    public SalesJournal salesJournal(CurrentUser actor, Long companyId, Long branchId, LocalDate from,
                                     LocalDate to) {
        Set<Long> branches = scope(actor, companyId, branchId, from, to);
        List<JournalRow> rows = journal.journal(companyId, branches, ReportAccess.owner(actor), from, to).stream()
                .map(SalesOrderReportService::journalRow).toList();
        JournalTotals total = new JournalTotals(sum(rows, JournalRow::receivable), sum(rows, JournalRow::goodsAmount),
                sum(rows, JournalRow::serviceAmount), sum(rows, JournalRow::deductionAmount),
                sum(rows, JournalRow::taxAmount));
        return new SalesJournal(from, to, rows, total, clock.instant());
    }

    @Transactional(readOnly = true)
    public StockBacklog stockBacklog(CurrentUser actor, Long companyId, Long warehouseId, List<Long> productIds) {
        access.requirePermission(actor, VIEW);
        if (productIds != null && productIds.size() > MAX_PRODUCTS) {
            throw BusinessException.invalidField("productIds", "Chọn tối đa " + MAX_PRODUCTS + " hàng mỗi lần xem.");
        }
        Set<Long> warehouses = access.warehouses(actor, companyId, warehouseId);
        List<BacklogRow> rows = backlog.backlog(companyId, warehouses, productIds, ReportAccess.owner(actor)).stream()
                .map(row -> {
                    BigDecimal shortage = row.ordered().subtract(row.onHand()).subtract(row.incoming())
                            .max(BigDecimal.ZERO);
                    return new BacklogRow(row.productId(), row.productCode(), row.productName(), row.unitName(),
                            row.warehouseId(), row.warehouseCode(), row.ordered(), row.orderCount(), row.onHand(),
                            row.reserved(), row.onHand().subtract(row.reserved()), row.incoming(), shortage);
                }).toList();
        return new StockBacklog(rows, clock.instant());
    }

    /** Dòng đảo đổi dấu mọi cột tiền. */
    private static JournalRow journalRow(Row row) {
        BigDecimal sign = row.reversal() ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        return new JournalRow(row.entryDate(), row.documentType(), row.documentId(), row.documentNo(),
                row.documentDate(), row.invoiceDate(), row.invoiceNo(), row.customerCode(), row.customerName(),
                row.description(), row.totalAmount().multiply(sign), row.goodsAmount().multiply(sign),
                row.serviceAmount().multiply(sign), row.deductionAmount().multiply(sign),
                row.taxAmount().multiply(sign), row.reversal());
    }

    private static BigDecimal sum(List<JournalRow> rows, Function<JournalRow, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Set<Long> scope(CurrentUser actor, Long companyId, Long branchId, LocalDate from, LocalDate to) {
        access.requirePermission(actor, VIEW);
        ReportAccess.requireRange(from, to);
        return access.branches(actor, companyId, branchId);
    }
}
