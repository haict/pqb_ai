package com.company.erp.reporting.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.dto.OrderReportResponse.DefectRow;
import com.company.erp.reporting.application.dto.OrderReportResponse.OrderProgress;
import com.company.erp.reporting.application.dto.OrderReportResponse.PriceHistory;
import com.company.erp.reporting.application.dto.OrderReportResponse.PriceRow;
import com.company.erp.reporting.application.dto.OrderReportResponse.PriceSummary;
import com.company.erp.reporting.application.dto.OrderReportResponse.SupplierDefects;
import com.company.erp.reporting.application.dto.OrderReportResponse.SupplierTotal;
import com.company.erp.reporting.infrastructure.OrderProgressJdbc;
import com.company.erp.reporting.infrastructure.OrderProgressJdbc.Filter;
import com.company.erp.reporting.infrastructure.PurchaseAnalysisJdbc;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo mua hàng (BR-RPT-25): tình hình thực hiện đơn mua (hẹn nhận, trả lại, đã thanh toán, còn phải trả), lịch sử
 * giá mua, hàng lỗi theo NCC truy qua lô. Theo phạm vi chi nhánh của người xem (BR-RPT-12).
 */
@Service
public class PurchaseReportService {

    static final String VIEW = "purchase-report:view";
    static final int MAX_PRODUCTS = 200;
    private static final int PRICE_SCALE = 4;

    private final OrderProgressJdbc orders;
    private final PurchaseAnalysisJdbc analysis;
    private final OrderProgressAssembler assembler;
    private final ReportAccess access;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public PurchaseReportService(OrderProgressJdbc orders, PurchaseAnalysisJdbc analysis,
                                 OrderProgressAssembler assembler, ReportAccess access, BusinessCalendar calendar,
                                 Clock clock) {
        this.orders = orders;
        this.analysis = analysis;
        this.assembler = assembler;
        this.access = access;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderProgress orderProgress(CurrentUser actor, Long companyId, Long branchId, Long supplierId,
                                       String status, LocalDate from, LocalDate to) {
        Set<Long> branches = scope(actor, companyId, branchId, from, to);
        LocalDate today = calendar.today();
        return assembler.progress("DMH", from, to, orders.purchaseOrders(new Filter(companyId, branches, from, to,
                supplierId, OrderProgressAssembler.status(status), null)), today, clock.instant());
    }

    @Transactional(readOnly = true)
    public PriceHistory priceHistory(CurrentUser actor, Long companyId, Long branchId, List<Long> productIds,
                                     Long supplierId, LocalDate from, LocalDate to) {
        Set<Long> branches = scope(actor, companyId, branchId, from, to);
        if (productIds != null && productIds.size() > MAX_PRODUCTS) {
            throw BusinessException.invalidField("productIds", "Chọn tối đa " + MAX_PRODUCTS + " hàng mỗi lần xem.");
        }
        List<PriceRow> rows = analysis.prices(companyId, branches, productIds, supplierId, from, to).stream()
                .map(row -> new PriceRow(row.productId(), row.productCode(), row.productName(), row.postingDate(),
                        row.documentId(), row.documentNo(), row.supplierCode(), row.supplierName(), row.unitName(),
                        row.quantity(), row.unitPrice(), row.discountPercent(),
                        divide(row.netAmount(), row.quantity()), row.baseUnitName(),
                        divide(row.netAmount(), row.baseQuantity())))
                .toList();
        Map<Long, List<PriceRow>> byProduct = rows.stream().collect(Collectors.groupingBy(PriceRow::productId,
                LinkedHashMap::new, Collectors.toList()));
        List<PriceSummary> summaries = byProduct.values().stream().map(PurchaseReportService::summary).toList();
        return new PriceHistory(from, to, rows, summaries, clock.instant());
    }

    @Transactional(readOnly = true)
    public SupplierDefects supplierDefects(CurrentUser actor, Long companyId, Long branchId, LocalDate from,
                                           LocalDate to) {
        Set<Long> branches = scope(actor, companyId, branchId, from, to);
        List<DefectRow> rows = analysis.defects(companyId, branches, from, to).stream()
                .map(row -> new DefectRow(row.complaintId(), row.complaintNo(), row.receivedDate(),
                        row.customerCode(), row.customerName(), row.productId(), row.productCode(),
                        row.productName(), row.lotNo(), row.serialNo(), row.quantity(), row.status(), row.decision(),
                        row.supplierId(), row.supplierCode(), row.supplierName()))
                .toList();
        Map<Optional<Long>, List<DefectRow>> bySupplier = rows.stream().collect(Collectors.groupingBy(
                row -> Optional.ofNullable(row.supplierId()), LinkedHashMap::new, Collectors.toList()));
        List<SupplierTotal> suppliers = bySupplier.values().stream().map(group -> {
            DefectRow first = group.get(0);
            return new SupplierTotal(first.supplierId(), first.supplierCode(), first.supplierName(), group.size(),
                    group.stream().map(DefectRow::productId).distinct().count(),
                    group.stream().map(DefectRow::quantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        }).sorted(Comparator.comparing(SupplierTotal::complaintCount).reversed()).toList();
        return new SupplierDefects(from, to, rows, suppliers, clock.instant());
    }

    /** Tổng hợp một hàng: giá thấp / cao nhất, giá và NCC lần mua gần nhất (dòng đã sắp theo ngày tăng dần). */
    private static PriceSummary summary(List<PriceRow> rows) {
        PriceRow last = rows.get(rows.size() - 1);
        List<BigDecimal> prices = rows.stream().map(PriceRow::netBasePrice).toList();
        return new PriceSummary(last.productId(), last.productCode(), last.productName(), last.baseUnitName(),
                rows.size(), prices.stream().min(BigDecimal::compareTo).orElse(null),
                prices.stream().max(BigDecimal::compareTo).orElse(null), last.netBasePrice(), last.postingDate(),
                last.supplierName());
    }

    private static BigDecimal divide(BigDecimal amount, BigDecimal quantity) {
        return quantity == null || quantity.signum() == 0 ? null
                : amount.divide(quantity, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private Set<Long> scope(CurrentUser actor, Long companyId, Long branchId, LocalDate from, LocalDate to) {
        access.requirePermission(actor, VIEW);
        ReportAccess.requireRange(from, to);
        return access.branches(actor, companyId, branchId);
    }
}
