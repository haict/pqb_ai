package com.company.erp.reporting.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.dto.DashboardResponse.AgingBucket;
import com.company.erp.reporting.application.dto.DashboardResponse.Backlog;
import com.company.erp.reporting.application.dto.DashboardResponse.BacklogDetails;
import com.company.erp.reporting.application.dto.DashboardResponse.BacklogRow;
import com.company.erp.reporting.application.dto.DashboardResponse.Debt;
import com.company.erp.reporting.application.dto.DashboardResponse.DebtDetails;
import com.company.erp.reporting.application.dto.DashboardResponse.DebtRow;
import com.company.erp.reporting.application.dto.DashboardResponse.Expiry;
import com.company.erp.reporting.application.dto.DashboardResponse.ExpiryDetails;
import com.company.erp.reporting.application.dto.DashboardResponse.ExpiryRow;
import com.company.erp.reporting.application.dto.DashboardResponse.Ranked;
import com.company.erp.reporting.application.dto.DashboardResponse.Summary;
import com.company.erp.reporting.application.dto.DashboardResponse.Trade;
import com.company.erp.reporting.application.dto.DashboardResponse.TradeDetails;
import com.company.erp.reporting.application.dto.DashboardResponse.TradeRow;
import com.company.erp.reporting.infrastructure.DashboardStatusJdbc;
import com.company.erp.reporting.infrastructure.DashboardStatusJdbc.Scope;
import com.company.erp.reporting.infrastructure.DashboardTradeJdbc;
import com.company.erp.reporting.infrastructure.DashboardTradeJdbc.Filter;
import com.company.erp.reporting.infrastructure.DashboardTradeJdbc.Kind;
import com.company.erp.reporting.infrastructure.DashboardTradeJdbc.Totals;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard điều hành (BR-RPT-01, 02, 04..06, 10): chỉ tiêu và danh sách chi tiết đến chứng từ nguồn, tổng chi tiết bằng
 * số trên chỉ tiêu (BR-RPT-11). Phạm vi chi nhánh / kho và "chỉ dữ liệu của mình" của người xem (BR-RPT-12); "hôm nay"
 * theo lịch nghiệp vụ (D-15), thời điểm lấy số liệu (BR-RPT-13).
 */
@Service
public class DashboardService {

    static final String VIEW = "dashboard:view";
    static final String EXPIRY_WARNING_DAYS = "inventory.expiry-warning-days";
    static final List<String> AGING_BUCKETS = List.of("finance.aging-bucket-1-days", "finance.aging-bucket-2-days",
            "finance.aging-bucket-3-days");
    static final int DEFAULT_TOP = 10;
    static final int MAX_TOP = 50;

    private final DashboardTradeJdbc trade;
    private final DashboardStatusJdbc status;
    private final ReportAccess access;
    private final ParameterService parameters;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public DashboardService(DashboardTradeJdbc trade, DashboardStatusJdbc status, ReportAccess access,
                            ParameterService parameters, BusinessCalendar calendar, Clock clock) {
        this.trade = trade;
        this.status = status;
        this.access = access;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(CurrentUser actor, Long companyId, Long branchId, LocalDate from, LocalDate to,
                           Integer top) {
        int limit = top == null ? DEFAULT_TOP : top;
        if (limit < 1 || limit > MAX_TOP) {
            throw BusinessException.invalidField("top", "Số dòng top từ 1 đến " + MAX_TOP + ".");
        }
        Set<Long> branches = periodScope(actor, companyId, branchId, from, to);
        Long owner = ReportAccess.owner(actor);
        Filter sales = new Filter(Kind.SALES, companyId, branches, owner, from, to);
        Filter purchases = new Filter(Kind.PURCHASES, companyId, branches, owner, from, to);
        Scope scope = new Scope(companyId, branches, owner);
        LocalDate today = calendar.today();
        List<Integer> aging = agingBuckets(companyId);
        return new Summary(from, to, today, trade(trade.totals(sales)), trade(trade.totals(purchases)),
                debt(debtRows(scope, "RECEIVABLE", today), aging), debt(debtRows(scope, "PAYABLE", today), aging),
                expiry(expiryDetails(actor, companyId, branchId)), backlog(status.openPurchaseOrders(scope), today),
                backlog(status.openSalesOrders(scope), today), ranked(trade.topProducts(sales, limit)),
                ranked(trade.topPartners(sales, limit)), ranked(trade.topPartners(purchases, limit)), clock.instant());
    }

    /** {@code kind}: {@code revenue} (chứng từ bán) hoặc {@code purchases} (chứng từ mua). */
    @Transactional(readOnly = true)
    public TradeDetails tradeDetails(CurrentUser actor, String kind, Long companyId, Long branchId, LocalDate from,
                                     LocalDate to, Long partnerId, Long productId) {
        Kind tradeKind = switch (kind) {
            case "revenue" -> Kind.SALES;
            case "purchases" -> Kind.PURCHASES;
            default -> throw BusinessException.notFound();
        };
        Set<Long> branches = periodScope(actor, companyId, branchId, from, to);
        List<TradeRow> rows = trade.documents(new Filter(tradeKind, companyId, branches, ReportAccess.owner(actor),
                from, to), partnerId, productId).stream().map(row -> new TradeRow(row.entryDate(),
                row.documentType(), row.documentId(), row.documentNo(), row.reversal(), row.partnerCode(),
                row.partnerName(), row.amount())).toList();
        return new TradeDetails(kind, from, to, rows, sum(rows, TradeRow::amount), clock.instant());
    }

    /** {@code debtType}: {@code receivables} hoặc {@code payables}. */
    @Transactional(readOnly = true)
    public DebtDetails debtDetails(CurrentUser actor, String debtType, Long companyId, Long branchId) {
        String type = switch (debtType) {
            case "receivables" -> "RECEIVABLE";
            case "payables" -> "PAYABLE";
            default -> throw BusinessException.notFound();
        };
        access.requirePermission(actor, VIEW);
        Scope scope = new Scope(companyId, access.branches(actor, companyId, branchId), ReportAccess.owner(actor));
        LocalDate today = calendar.today();
        List<DebtRow> rows = debtRows(scope, type, today);
        return new DebtDetails(type, today, rows, sum(rows, DebtRow::remainingAmount),
                sum(rows.stream().filter(row -> row.overdueDays() > 0).toList(), DebtRow::remainingAmount),
                clock.instant());
    }

    @Transactional(readOnly = true)
    public ExpiryDetails expiryDetails(CurrentUser actor, Long companyId, Long branchId) {
        access.requirePermission(actor, VIEW);
        access.branches(actor, companyId, branchId);
        Set<Long> warehouses = access.warehouses(actor, companyId, null);
        int days = parameters.integerValue(companyId, EXPIRY_WARNING_DAYS);
        LocalDate today = calendar.today();
        LocalDate until = today.plusDays(days);
        List<ExpiryRow> rows = status.expiringLots(companyId, warehouses, branchId, until).stream()
                .map(lot -> new ExpiryRow(lot.productId(), lot.productCode(), lot.productName(), lot.unitName(),
                        lot.warehouseCode(), lot.lotId(), lot.lotNo(), lot.expiryDate(),
                        ChronoUnit.DAYS.between(today, lot.expiryDate()), lot.onHand()))
                .toList();
        return new ExpiryDetails(days, today, until, rows, clock.instant());
    }

    /** {@code kind}: {@code purchase-backlog} (đơn mua) hoặc {@code sales-backlog} (đơn bán). */
    @Transactional(readOnly = true)
    public BacklogDetails backlogDetails(CurrentUser actor, String kind, Long companyId, Long branchId) {
        boolean sales = switch (kind) {
            case "sales-backlog" -> true;
            case "purchase-backlog" -> false;
            default -> throw BusinessException.notFound();
        };
        access.requirePermission(actor, VIEW);
        Scope scope = new Scope(companyId, access.branches(actor, companyId, branchId), ReportAccess.owner(actor));
        LocalDate today = calendar.today();
        List<BacklogRow> rows = (sales ? status.openSalesOrders(scope) : status.openPurchaseOrders(scope)).stream()
                .map(order -> new BacklogRow(order.id(), order.documentNo(), order.documentDate(),
                        order.partnerCode(), order.partnerName(), order.status(), order.deliveryStatus(),
                        order.expectedDate(), overdueDays(order.expectedDate(), today), order.totalAmount()))
                .toList();
        return new BacklogDetails(sales ? "DBH" : "DMH", today, rows, sum(rows, BacklogRow::totalAmount),
                clock.instant());
    }

    private Set<Long> periodScope(CurrentUser actor, Long companyId, Long branchId, LocalDate from, LocalDate to) {
        access.requirePermission(actor, VIEW);
        ReportAccess.requireRange(from, to);
        return access.branches(actor, companyId, branchId);
    }

    private List<DebtRow> debtRows(Scope scope, String type, LocalDate today) {
        return status.openDebts(scope, type).stream().map(item -> new DebtRow(item.id(), item.sourceType(),
                item.sourceId(), item.sourceNo(), item.invoiceNo(), item.partnerCode(), item.partnerName(),
                item.documentDate(), item.dueDate(), item.originalAmount(), item.settledAmount(),
                item.remainingAmount(), overdueDays(item.dueDate(), today))).toList();
    }

    private static Trade trade(Totals totals) {
        return new Trade(totals.grossAmount(), totals.deductionAmount(),
                totals.grossAmount().subtract(totals.deductionAmount()), totals.documentCount(), false);
    }

    /** Mốc tuổi nợ tăng dần của công ty (dùng chung với báo cáo tuổi nợ của module công nợ). */
    private List<Integer> agingBuckets(Long companyId) {
        List<Integer> limits = AGING_BUCKETS.stream().map(key -> parameters.integerValue(companyId, key)).toList();
        if (limits.get(0) >= limits.get(1) || limits.get(1) >= limits.get(2)) {
            throw BusinessException.invalidField("companyId",
                    "Tham số mốc tuổi nợ phải tăng dần (mốc 1 < mốc 2 < mốc 3).");
        }
        return limits;
    }

    private static Debt debt(List<DebtRow> rows, List<Integer> limits) {
        List<DebtRow> overdue = rows.stream().filter(row -> row.overdueDays() > 0).toList();
        List<AgingBucket> buckets = new ArrayList<>();
        int from = 1;
        for (Integer limit : limits) {
            buckets.add(agingBucket(overdue, from, limit));
            from = limit + 1;
        }
        buckets.add(agingBucket(overdue, from, null));
        return new Debt(sum(rows, DebtRow::remainingAmount), rows.size(), sum(overdue, DebtRow::remainingAmount),
                overdue.size(), buckets);
    }

    private static AgingBucket agingBucket(List<DebtRow> overdue, int fromDays, Integer toDays) {
        return new AgingBucket(fromDays, toDays, sum(overdue.stream().filter(row -> row.overdueDays() >= fromDays
                && (toDays == null || row.overdueDays() <= toDays)).toList(), DebtRow::remainingAmount));
    }

    private static Expiry expiry(ExpiryDetails details) {
        long expired = details.rows().stream().filter(row -> row.daysLeft() < 0).count();
        return new Expiry(details.warningDays(), details.warningUntil(), expired, details.rows().size() - expired);
    }

    private static Backlog backlog(List<DashboardStatusJdbc.OpenOrder> orders, LocalDate today) {
        return new Backlog(orders.size(), sum(orders, DashboardStatusJdbc.OpenOrder::totalAmount),
                orders.stream().filter(order -> overdueDays(order.expectedDate(), today) > 0).count());
    }

    private static List<Ranked> ranked(List<DashboardTradeJdbc.Ranked> rows) {
        return rows.stream().map(row -> new Ranked(row.id(), row.code(), row.name(), row.amount())).toList();
    }

    private static long overdueDays(LocalDate due, LocalDate today) {
        return due == null || !due.isBefore(today) ? 0 : ChronoUnit.DAYS.between(due, today);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
