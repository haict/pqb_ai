package com.company.erp.reporting.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.reporting.application.dto.TraceabilityResponse.CustomerTotal;
import com.company.erp.reporting.application.dto.TraceabilityResponse.CustomerTrace;
import com.company.erp.reporting.application.dto.TraceabilityResponse.Line;
import com.company.erp.reporting.application.dto.TraceabilityResponse.LotTrace;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.ProductHeader;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.Scope;
import com.company.erp.reporting.infrastructure.TraceabilityReportJdbc;
import com.company.erp.reporting.infrastructure.TraceabilityReportJdbc.LotHeader;
import com.company.erp.reporting.infrastructure.TraceabilityReportJdbc.TraceRow;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truy xuất lô / serial (BR-RPT-24, phục vụ thu hồi BR-INV-42): lô → nơi nhập, khách nhận, tồn còn lại; khách → lô,
 * serial đã nhận. Trong phạm vi kho của người xem (BR-RPT-12).
 */
@Service
public class TraceabilityReportService {

    private static final String ISSUE = "SALES_ISSUE";
    private static final Set<String> SALES_TYPES = Set.of(ISSUE, "SALES_RETURN");

    private final TraceabilityReportJdbc jdbc;
    private final StockLedgerReportJdbc ledger;
    private final ReportAccess access;
    private final MasterDataQueryService masterData;
    private final Clock clock;

    public TraceabilityReportService(TraceabilityReportJdbc jdbc, StockLedgerReportJdbc ledger, ReportAccess access,
                                     MasterDataQueryService masterData, Clock clock) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.access = access;
        this.masterData = masterData;
        this.clock = clock;
    }

    /** Truy xuất theo lô hoặc theo serial (chọn đúng một). */
    @Transactional(readOnly = true)
    public LotTrace lotTrace(CurrentUser actor, Long companyId, Long lotId, Long serialId) {
        access.requirePermission(actor, StockLedgerReportService.VIEW);
        if ((lotId == null) == (serialId == null)) {
            throw BusinessException.invalidField("lotId", "Chọn một lô hoặc một serial.");
        }
        Scope scope = new Scope(companyId, access.warehouses(actor, companyId, null));
        List<TraceRow> rows = jdbc.lotMovements(scope, lotId, serialId);
        List<Line> lines = rows.stream().map(TraceabilityReportService::line).toList();
        BigDecimal onHand = rows.stream().map(TraceRow::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lotId != null) {
            LotHeader lot = jdbc.lot(companyId, lotId)
                    .orElseThrow(() -> BusinessException.invalidField("lotId", "Lô không tồn tại."));
            return new LotTrace(lot.productId(), lot.productCode(), lot.productName(), lot.unitName(), lot.lotNo(),
                    lot.expiryDate(), null, lines, customers(rows), onHand, clock.instant());
        }
        Long productId = ledger.productOfSerial(companyId, serialId)
                .orElseThrow(() -> BusinessException.invalidField("serialId", "Serial không tồn tại."));
        ProductHeader product = ledger.product(companyId, productId).orElseThrow(BusinessException::notFound);
        TraceRow first = rows.isEmpty() ? null : rows.get(0);
        return new LotTrace(product.id(), product.code(), product.name(), product.unitName(),
                first == null ? null : first.lotNo(), first == null ? null : first.expiryDate(),
                first == null ? null : first.serialNo(), lines, customers(rows), onHand, clock.instant());
    }

    @Transactional(readOnly = true)
    public CustomerTrace customerTrace(CurrentUser actor, Long companyId, Long customerId, LocalDate from,
                                       LocalDate to) {
        access.requirePermission(actor, StockLedgerReportService.VIEW);
        ReportAccess.requireRange(from, to);
        Scope scope = new Scope(companyId, access.warehouses(actor, companyId, null));
        PartnerInfo customer = masterData.partner(customerId).filter(found -> found.companyId().equals(companyId))
                .orElseThrow(() -> BusinessException.invalidField("customerId", "Khách hàng không tồn tại."));
        List<Line> lines = jdbc.customerMovements(scope, customerId, from, to).stream()
                .map(TraceabilityReportService::line).toList();
        return new CustomerTrace(customer.id(), customer.code(), customer.name(), from, to, lines, clock.instant());
    }

    /**
     * Tổng theo khách của dòng bán / trả lại, kể cả dòng đảo (loại gốc ở {@code baseType}): xuất bán ghi âm trên sổ kho
     * nên đổi dấu, đảo xuất bán (dương) làm giảm số đã xuất; trả lại ghi dương, đảo trả lại làm giảm số trả lại.
     */
    private static List<CustomerTotal> customers(List<TraceRow> rows) {
        Map<Long, BigDecimal[]> totals = new LinkedHashMap<>();
        Map<Long, TraceRow> names = new LinkedHashMap<>();
        for (TraceRow row : rows) {
            if (row.partnerId() == null || !SALES_TYPES.contains(row.baseType())) {
                continue;
            }
            names.putIfAbsent(row.partnerId(), row);
            BigDecimal[] sums = totals.computeIfAbsent(row.partnerId(),
                    key -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            if (ISSUE.equals(row.baseType())) {
                sums[0] = sums[0].subtract(row.quantity());
            } else {
                sums[1] = sums[1].add(row.quantity());
            }
        }
        return totals.entrySet().stream().map(entry -> {
            TraceRow name = names.get(entry.getKey());
            BigDecimal issued = entry.getValue()[0];
            BigDecimal returned = entry.getValue()[1];
            return new CustomerTotal(entry.getKey(), name.partnerCode(), name.partnerName(), issued, returned,
                    issued.subtract(returned));
        }).toList();
    }

    private static Line line(TraceRow row) {
        return new Line(row.postingDate(), row.transactionType(), row.referenceType(), row.referenceId(),
                row.referenceNo(), row.reversal(), row.warehouseCode(), row.partnerId(), row.partnerCode(),
                row.partnerName(), row.productId(), row.productCode(), row.productName(), row.unitName(),
                row.lotNo(), row.expiryDate(), row.serialNo(), row.quantity());
    }
}
