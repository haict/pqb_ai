package com.company.erp.reporting.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.ForecastPeriods.Granularity;
import com.company.erp.reporting.application.ForecastPeriods.Period;
import com.company.erp.reporting.application.dto.CashForecastResponse.CashBalance;
import com.company.erp.reporting.application.dto.CashForecastResponse.Column;
import com.company.erp.reporting.application.dto.CashForecastResponse.Forecast;
import com.company.erp.reporting.application.dto.CashForecastResponse.ItemRow;
import com.company.erp.reporting.application.dto.CashForecastResponse.Items;
import com.company.erp.reporting.application.dto.CashForecastResponse.SourceLine;
import com.company.erp.reporting.infrastructure.CashForecastJdbc;
import com.company.erp.reporting.infrastructure.CashForecastJdbc.DailyAmount;
import com.company.erp.reporting.infrastructure.CashForecastJdbc.Scope;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dòng tiền dự kiến (BR-RPT-09, 30, BR-CSH-06): số dư quỹ / ngân hàng hôm nay, dự thu / dự chi theo nguồn và kỳ; khoản
 * có ngày dự kiến trước hôm nay vào cột "Quá hạn" (chủ dự án chọn 2026-10-14), sau kỳ cuối vào cột "Sau". Phạm vi chi
 * nhánh và "chỉ dữ liệu của mình" (BR-RPT-12).
 */
@Service
public class CashForecastService {

    static final String VIEW = "cash-forecast:view";
    static final int DEFAULT_PERIODS = 12;
    static final List<String> SOURCES = List.of("RECEIVABLE", "SALES_ORDER", "PAYABLE", "PURCHASE_ORDER");

    private final CashForecastJdbc jdbc;
    private final ReportAccess access;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public CashForecastService(CashForecastJdbc jdbc, ReportAccess access, BusinessCalendar calendar, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Forecast forecast(CurrentUser actor, Long companyId, Long branchId, String granularity, Integer periods) {
        Granularity unit = ForecastPeriods.granularity(granularity);
        Scope scope = scope(actor, companyId, branchId);
        LocalDate today = calendar.today();
        List<Period> range = ForecastPeriods.of(unit, today, periods == null ? DEFAULT_PERIODS : periods);
        List<Column> columns = columns(range);
        List<Predicate<LocalDate>> buckets = buckets(range, today);

        boolean cashIncluded = scope.ownerId() == null;
        List<CashBalance> accounts = cashIncluded ? jdbc.balances(scope, today).stream()
                .map(row -> new CashBalance(row.id(), row.code(), row.name(), row.accountType(), row.balance()))
                .toList() : List.of();
        BigDecimal opening = accounts.stream().map(CashBalance::balance).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DailyAmount> daily = jdbc.dailyAmounts(scope);
        List<SourceLine> lines = SOURCES.stream().map(source -> line(source, daily, buckets)).toList();
        List<BigDecimal> inflow = columnSum(lines, "IN", columns.size());
        List<BigDecimal> outflow = columnSum(lines, "OUT", columns.size());
        List<BigDecimal> net = new ArrayList<>();
        List<BigDecimal> closing = new ArrayList<>();
        BigDecimal running = opening;
        for (int i = 0; i < columns.size(); i++) {
            net.add(inflow.get(i).subtract(outflow.get(i)));
            running = running.add(net.get(i));
            closing.add(running);
        }
        return new Forecast(unit.name(), today, cashIncluded, opening, accounts, columns, lines, inflow, outflow, net,
                closing, clock.instant());
    }

    /** Khoản cấu thành một ô; {@code source} null: mọi nguồn. Ngày null: không giới hạn. */
    @Transactional(readOnly = true)
    public Items items(CurrentUser actor, Long companyId, Long branchId, String source, LocalDate from,
                       LocalDate to) {
        if (source != null && !SOURCES.contains(source)) {
            throw BusinessException.invalidField("source", "Nguồn không hợp lệ.");
        }
        if (from != null && to != null) {
            ReportAccess.requireRange(from, to);
        }
        Scope scope = scope(actor, companyId, branchId);
        LocalDate today = calendar.today();
        List<ItemRow> rows = jdbc.items(scope, source, from, to).stream()
                .map(item -> new ItemRow(item.source(), item.documentType(), item.documentId(), item.documentNo(),
                        item.partnerCode(), item.partnerName(), item.expectedDate(),
                        item.expectedDate().isBefore(today) ? ChronoUnit.DAYS.between(item.expectedDate(), today) : 0,
                        item.amount()))
                .toList();
        BigDecimal total = rows.stream().map(ItemRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Items(source, from, to, today, rows, total, clock.instant());
    }

    private static String direction(String source) {
        return "RECEIVABLE".equals(source) || "SALES_ORDER".equals(source) ? "IN" : "OUT";
    }

    private Scope scope(CurrentUser actor, Long companyId, Long branchId) {
        access.requirePermission(actor, VIEW);
        return new Scope(companyId, access.branches(actor, companyId, branchId), ReportAccess.owner(actor));
    }

    /** Quá hạn, các kỳ, sau kỳ cuối. */
    private static List<Column> columns(List<Period> range) {
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("OVERDUE", null, range.get(0).from().minusDays(1)));
        for (int i = 0; i < range.size(); i++) {
            columns.add(new Column("P" + (i + 1), range.get(i).from(), range.get(i).to()));
        }
        columns.add(new Column("LATER", range.get(range.size() - 1).to().plusDays(1), null));
        return columns;
    }

    private static List<Predicate<LocalDate>> buckets(List<Period> range, LocalDate today) {
        List<Predicate<LocalDate>> buckets = new ArrayList<>();
        buckets.add(date -> date.isBefore(today));
        range.forEach(period -> buckets.add(period::contains));
        LocalDate end = range.get(range.size() - 1).to();
        buckets.add(date -> date.isAfter(end));
        return buckets;
    }

    private static SourceLine line(String source, List<DailyAmount> daily, List<Predicate<LocalDate>> buckets) {
        List<BigDecimal> amounts = new ArrayList<>(Collections.nCopies(buckets.size(), BigDecimal.ZERO));
        daily.stream().filter(row -> row.source().equals(source)).forEach(row -> {
            for (int i = 0; i < buckets.size(); i++) {
                if (buckets.get(i).test(row.expectedDate())) {
                    amounts.set(i, amounts.get(i).add(row.amount()));
                    return;
                }
            }
        });
        BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SourceLine(source, direction(source), amounts, total);
    }

    private static List<BigDecimal> columnSum(List<SourceLine> lines, String direction, int size) {
        List<BigDecimal> sums = new ArrayList<>(Collections.nCopies(size, BigDecimal.ZERO));
        lines.stream().filter(line -> line.direction().equals(direction)).forEach(line -> {
            for (int i = 0; i < size; i++) {
                sums.set(i, sums.get(i).add(line.amounts().get(i)));
            }
        });
        return sums;
    }
}
