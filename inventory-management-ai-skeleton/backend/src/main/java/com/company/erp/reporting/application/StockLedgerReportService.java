package com.company.erp.reporting.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.CatalogLookupService;
import com.company.erp.reporting.application.dto.InventoryMovementResponse;
import com.company.erp.reporting.application.dto.StockCardResponse;
import com.company.erp.reporting.application.dto.StockCardResponse.Line;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.CardRow;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.ProductHeader;
import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.Scope;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thẻ kho, lịch sử serial (BR-RPT-21) và nhập - xuất - tồn theo kỳ (BR-RPT-22) từ sổ nhật ký kho. Số liệu trong phạm vi
 * kho của người xem (BR-RPT-12); tồn cuối thẻ kho bằng tổng sổ nhật ký đến ngày cuối kỳ.
 */
@Service
public class StockLedgerReportService {

    static final String VIEW = "inventory-report:view";
    /** Số dòng thẻ kho tối đa một lần xem; vượt thì yêu cầu thu hẹp khoảng ngày / kho / lô. */
    static final int MAX_CARD_LINES = 20_000;
    static final int MAX_PRODUCTS = 200;

    private final StockLedgerReportJdbc jdbc;
    private final ReportAccess access;
    private final CatalogLookupService catalogs;
    private final Clock clock;

    public StockLedgerReportService(StockLedgerReportJdbc jdbc, ReportAccess access, CatalogLookupService catalogs,
                                    Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.catalogs = catalogs;
        this.clock = clock;
    }

    /** Điều kiện thẻ kho: hàng, hoặc serial (lấy hàng của serial); kho, lô tùy chọn. */
    public record CardCriteria(Long companyId, Long warehouseId, Long productId, Long lotId, Long serialId,
                               LocalDate dateFrom, LocalDate dateTo) {
    }

    @Transactional(readOnly = true)
    public StockCardResponse stockCard(CurrentUser actor, CardCriteria criteria) {
        access.requirePermission(actor, VIEW);
        ReportAccess.requireRange(criteria.dateFrom(), criteria.dateTo());
        Scope scope = new Scope(criteria.companyId(), access.warehouses(actor, criteria.companyId(),
                criteria.warehouseId()));
        Long productId = criteria.productId();
        if (criteria.serialId() != null) {
            Long serialProduct = jdbc.productOfSerial(criteria.companyId(), criteria.serialId())
                    .orElseThrow(() -> BusinessException.invalidField("serialId", "Serial không tồn tại."));
            if (productId != null && !productId.equals(serialProduct)) {
                throw BusinessException.invalidField("serialId", "Serial không thuộc hàng đã chọn.");
            }
            productId = serialProduct;
        }
        if (productId == null) {
            throw BusinessException.invalidField("productId", "Chọn hàng hóa hoặc serial.");
        }
        ProductHeader product = jdbc.product(criteria.companyId(), productId)
                .orElseThrow(() -> BusinessException.invalidField("productId", "Hàng hóa không tồn tại."));
        BigDecimal opening = jdbc.opening(scope, productId, criteria.lotId(), criteria.serialId(), criteria.dateFrom());
        List<CardRow> rows = jdbc.cardLines(scope, productId, criteria.lotId(), criteria.serialId(),
                criteria.dateFrom(), criteria.dateTo(), MAX_CARD_LINES + 1);
        if (rows.size() > MAX_CARD_LINES) {
            throw BusinessException.invalidField("dateFrom", "Thẻ kho quá " + MAX_CARD_LINES
                    + " dòng; thu hẹp khoảng ngày hoặc chọn kho / lô.");
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal balance = opening;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (CardRow row : rows) {
            boolean in = "IN".equals(row.direction());
            balance = in ? balance.add(row.quantity()) : balance.subtract(row.quantity());
            totalIn = in ? totalIn.add(row.quantity()) : totalIn;
            totalOut = in ? totalOut : totalOut.add(row.quantity());
            lines.add(new Line(row.postingDate(), row.transactionType(), row.referenceType(), row.referenceId(),
                    row.referenceNo(), row.reversal(), row.warehouseCode(), row.lotNo(), row.expiryDate(),
                    row.serialNo(), row.partnerName(), row.qualityStatus(), in ? row.quantity() : null,
                    in ? null : row.quantity(), balance));
        }
        return new StockCardResponse(product.id(), product.code(), product.name(), product.unitName(),
                criteria.dateFrom(), criteria.dateTo(), opening, lines, totalIn, totalOut, balance, clock.instant());
    }

    /** Điều kiện nhập - xuất - tồn; danh sách hàng {@code null} / rỗng: mọi hàng. */
    public record MovementCriteria(Long companyId, Long warehouseId, Long productCategoryId, List<Long> productIds,
                                   LocalDate dateFrom, LocalDate dateTo) {
    }

    @Transactional(readOnly = true)
    public InventoryMovementResponse movement(CurrentUser actor, MovementCriteria criteria) {
        access.requirePermission(actor, VIEW);
        ReportAccess.requireRange(criteria.dateFrom(), criteria.dateTo());
        if (criteria.productIds() != null && criteria.productIds().size() > MAX_PRODUCTS) {
            throw BusinessException.invalidField("productIds", "Chọn tối đa " + MAX_PRODUCTS + " hàng mỗi lần xem.");
        }
        Scope scope = new Scope(criteria.companyId(), access.warehouses(actor, criteria.companyId(),
                criteria.warehouseId()));
        Set<Long> categories = null;
        if (criteria.productCategoryId() != null) {
            categories = catalogs.categoryWithDescendants(criteria.companyId(), criteria.productCategoryId());
            if (categories.isEmpty()) {
                throw BusinessException.invalidField("productCategoryId", "Nhóm hàng không tồn tại.");
            }
        }
        List<InventoryMovementResponse.Row> rows = jdbc.movement(scope, criteria.productIds(), categories,
                        criteria.dateFrom(), criteria.dateTo()).stream()
                .map(row -> new InventoryMovementResponse.Row(row.productId(), row.productCode(), row.productName(),
                        row.unitName(), row.warehouseId(), row.warehouseCode(), row.warehouseName(), row.opening(),
                        row.inQuantity(), row.outQuantity(),
                        row.opening().add(row.inQuantity()).subtract(row.outQuantity())))
                .toList();
        return new InventoryMovementResponse(criteria.dateFrom(), criteria.dateTo(), rows, false, clock.instant());
    }
}
