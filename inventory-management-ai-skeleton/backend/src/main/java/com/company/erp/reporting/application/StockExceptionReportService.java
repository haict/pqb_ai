package com.company.erp.reporting.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.dto.StockExceptionResponse.ExceptionRow;
import com.company.erp.reporting.application.dto.StockExceptionResponse.Exceptions;
import com.company.erp.reporting.application.dto.StockExceptionResponse.MissingDocuments;
import com.company.erp.reporting.application.dto.StockExceptionResponse.MissingRow;
import com.company.erp.reporting.application.dto.StockExceptionResponse.SlowMoving;
import com.company.erp.reporting.application.dto.StockExceptionResponse.SlowRow;
import com.company.erp.reporting.infrastructure.StockExceptionReportJdbc;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.Scope;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hàng cần xử lý (BR-RPT-23): tồn lâu theo tham số {@code inventory.slow-moving-days}, hàng ngoại lệ, hàng thiếu hồ sơ
 * bắt buộc. Trong phạm vi kho của người xem (BR-RPT-12); "hôm nay" theo lịch nghiệp vụ (D-15).
 */
@Service
public class StockExceptionReportService {

    static final String SLOW_MOVING_DAYS = "inventory.slow-moving-days";
    private static final int MAX_DAYS = 3650;

    private final StockExceptionReportJdbc jdbc;
    private final ReportAccess access;
    private final ParameterService parameters;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public StockExceptionReportService(StockExceptionReportJdbc jdbc, ReportAccess access,
                                       ParameterService parameters, BusinessCalendar calendar, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** {@code days} null: theo tham số của công ty. */
    @Transactional(readOnly = true)
    public SlowMoving slowMoving(CurrentUser actor, Long companyId, Long warehouseId, Integer days) {
        Scope scope = scope(actor, companyId, warehouseId);
        int threshold = days != null ? days : parameters.integerValue(companyId, SLOW_MOVING_DAYS);
        if (threshold < 1 || threshold > MAX_DAYS) {
            throw BusinessException.invalidField("days", "Số ngày từ 1 đến " + MAX_DAYS + ".");
        }
        LocalDate today = calendar.today();
        LocalDate cutoff = today.minusDays(threshold);
        List<SlowRow> rows = jdbc.slowMoving(scope, cutoff).stream().map(row -> {
            LocalDate last = row.lastOutDate() != null ? row.lastOutDate() : row.lastInDate();
            return new SlowRow(row.productId(), row.productCode(), row.productName(), row.unitName(),
                    row.warehouseId(), row.warehouseCode(), row.onHand(), row.lastInDate(), row.lastOutDate(),
                    last == null ? null : ChronoUnit.DAYS.between(last, today));
        }).toList();
        return new SlowMoving(threshold, cutoff, rows, clock.instant());
    }

    @Transactional(readOnly = true)
    public Exceptions exceptions(CurrentUser actor, Long companyId, Long warehouseId) {
        Scope scope = scope(actor, companyId, warehouseId);
        LocalDate today = calendar.today();
        List<ExceptionRow> rows = jdbc.exceptions(scope, today).stream().map(row -> {
            List<String> reasons = new ArrayList<>();
            if (row.expiryDate() != null && row.expiryDate().isBefore(today)) {
                reasons.add("EXPIRED");
            }
            if (!"PASSED".equals(row.qualityStatus())) {
                reasons.add("QUALITY");
            }
            if (row.blocked()) {
                reasons.add("BLOCKED");
            }
            return new ExceptionRow(row.productId(), row.productCode(), row.productName(), row.unitName(),
                    row.warehouseCode(), row.lotNo(), row.expiryDate(), row.serialNo(), row.qualityStatus(),
                    List.copyOf(reasons), row.onHand());
        }).toList();
        return new Exceptions(today, rows, clock.instant());
    }

    @Transactional(readOnly = true)
    public MissingDocuments missingDocuments(CurrentUser actor, Long companyId, Long warehouseId) {
        Scope scope = scope(actor, companyId, warehouseId);
        LocalDate today = calendar.today();
        List<MissingRow> rows = jdbc.missingDocuments(scope, today).stream()
                .map(row -> new MissingRow(row.productId(), row.productCode(), row.productName(), row.productType(),
                        row.documentTypeCode(), row.documentTypeName(), row.expiredDocumentNo(), row.expiredOn(),
                        row.onHand()))
                .toList();
        return new MissingDocuments(today, rows, clock.instant());
    }

    private Scope scope(CurrentUser actor, Long companyId, Long warehouseId) {
        access.requirePermission(actor, StockLedgerReportService.VIEW);
        return new Scope(companyId, access.warehouses(actor, companyId, warehouseId));
    }
}
