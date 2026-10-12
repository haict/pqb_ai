package com.company.erp.sales.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.sales.application.dto.SalesSourceDocument;
import com.company.erp.sales.application.dto.SalesSummaryResponse;
import com.company.erp.sales.application.dto.SalesSummaryRow;
import com.company.erp.sales.infrastructure.persistence.SalesReportJdbc;
import com.company.erp.sales.domain.SalesReportGrouping;
import com.company.erp.sales.infrastructure.persistence.SalesReportJdbc.Row;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo doanh số (BR-SAL-51): doanh thu, giảm trừ, doanh thu thuần, thuế theo nhân viên kinh doanh / khách / hàng /
 * chi nhánh trong khoảng ngày hạch toán, kèm công nợ phải thu còn lại; mỗi số liệu truy ngược được tới chứng từ
 * (business-integrity mục 11). Lợi nhuận gộp cần giá vốn (Giai đoạn 8).
 */
@Service
public class SalesReportService {

    static final String VIEW = "sales-report:view";

    private final SalesReportJdbc report;
    private final SalesAccess access;

    public SalesReportService(SalesReportJdbc report, SalesAccess access) {
        this.report = report;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(CurrentUser actor, Long companyId, Long branchId, LocalDate from, LocalDate to,
                                        SalesReportGrouping grouping) {
        requireScope(actor, companyId, branchId, from, to);
        List<Row> rows = report.summary(companyId, branchId, from, to, grouping);
        Map<Long, BigDecimal> receivables = new HashMap<>();
        report.receivables(companyId, branchId, grouping)
                .forEach(row -> receivables.put((Long) row[0], (BigDecimal) row[1]));
        Map<Long, String[]> labels = labels(grouping, rows.stream().map(Row::key).toList());
        List<SalesSummaryRow> result = rows.stream().map(row -> {
            String[] label = labels.getOrDefault(row.key(), new String[] {null, null});
            return new SalesSummaryRow(row.key(), label[0], label[1], row.invoiceCount(), row.revenue(),
                    row.deductions(), row.revenue().subtract(row.deductions()), row.tax(),
                    grouping == SalesReportGrouping.PRODUCT ? null : receivables.getOrDefault(row.key(), BigDecimal.ZERO));
        }).toList();
        SalesSummaryRow total = new SalesSummaryRow(null, null, "Tổng cộng",
                result.stream().mapToLong(SalesSummaryRow::invoiceCount).sum(),
                sum(result.stream().map(SalesSummaryRow::revenue).toList()),
                sum(result.stream().map(SalesSummaryRow::deductions).toList()),
                sum(result.stream().map(SalesSummaryRow::netRevenue).toList()),
                sum(result.stream().map(SalesSummaryRow::taxAmount).toList()),
                grouping == SalesReportGrouping.PRODUCT ? null : sum(result.stream().map(SalesSummaryRow::receivable).toList()));
        return new SalesSummaryResponse(grouping.name(), from, to, result, total);
    }

    /** Chứng từ cấu thành một nhóm. */
    @Transactional(readOnly = true)
    public List<SalesSourceDocument> sources(CurrentUser actor, Long companyId, Long branchId, LocalDate from,
                                             LocalDate to, SalesReportGrouping grouping, Long key) {
        requireScope(actor, companyId, branchId, from, to);
        return report.sources(companyId, branchId, from, to, grouping, key).stream()
                .map(source -> new SalesSourceDocument(source.type(), source.id(), source.number(),
                        source.postingDate(), source.customerId(), source.amount()))
                .toList();
    }

    /** Toàn công ty cần phạm vi toàn công ty; không thì phải chọn chi nhánh trong phạm vi. */
    private void requireScope(CurrentUser actor, Long companyId, Long branchId, LocalDate from, LocalDate to) {
        access.requirePermission(actor, VIEW);
        if (from.isAfter(to)) {
            throw BusinessException.invalidField("dateFrom", "Từ ngày phải trước đến ngày.");
        }
        if (branchId == null) {
            if (!actor.dataScope().hasFullCompanyAccess(companyId)) {
                throw BusinessException.invalidField("branchId", "Chọn chi nhánh trong phạm vi của bạn.");
            }
        } else {
            BranchRef branch = access.usableBranch(actor, branchId);
            if (!branch.companyId().equals(companyId)) {
                throw BusinessException.notFound();
            }
        }
    }

    private Map<Long, String[]> labels(SalesReportGrouping grouping, List<Long> keys) {
        Map<Long, String[]> labels = new HashMap<>();
        switch (grouping) {
            case CUSTOMER -> access.partners(keys).forEach((id, partner) -> labels.put(id,
                    new String[] {partner.code(), partner.name()}));
            case PRODUCT -> access.products(keys).forEach((id, product) -> labels.put(id,
                    new String[] {product.code(), product.name()}));
            case SALES_USER -> access.users(keys).forEach((id, user) -> labels.put(id,
                    new String[] {user.username(), user.fullName()}));
            case BRANCH -> keys.forEach(id -> access.branch(id).ifPresent(branch -> labels.put(id,
                    new String[] {branch.code(), branch.name()})));
        }
        return labels;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
