package com.company.erp.reporting.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.reporting.application.StockExceptionReportService;
import com.company.erp.reporting.application.StockLedgerReportService;
import com.company.erp.reporting.application.StockLedgerReportService.CardCriteria;
import com.company.erp.reporting.application.StockLedgerReportService.MovementCriteria;
import com.company.erp.reporting.application.TraceabilityReportService;
import com.company.erp.reporting.application.dto.InventoryMovementResponse;
import com.company.erp.reporting.application.dto.StockCardResponse;
import com.company.erp.reporting.application.dto.StockExceptionResponse.Exceptions;
import com.company.erp.reporting.application.dto.StockExceptionResponse.MissingDocuments;
import com.company.erp.reporting.application.dto.StockExceptionResponse.SlowMoving;
import com.company.erp.reporting.application.dto.TraceabilityResponse.CustomerTrace;
import com.company.erp.reporting.application.dto.TraceabilityResponse.LotTrace;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Báo cáo kho M10 (BR-RPT-21..24, ADR-18). Mọi báo cáo theo phạm vi kho của người xem. */
@Tag(name = "Báo cáo - kho")
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('inventory-report:view')")
public class InventoryReportController {

    private final StockLedgerReportService ledger;
    private final StockExceptionReportService exceptions;
    private final TraceabilityReportService traceability;

    public InventoryReportController(StockLedgerReportService ledger, StockExceptionReportService exceptions,
                                     TraceabilityReportService traceability) {
        this.ledger = ledger;
        this.exceptions = exceptions;
        this.traceability = traceability;
    }

    @Operation(summary = "Thẻ kho, lịch sử serial", description = "BR-RPT-21. Chọn productId hoặc serialId; kho, lô tùy "
            + "chọn. Tồn đầu, dòng nhập / xuất với tồn lũy kế, tồn cuối (đơn vị cơ sở). Tối đa 20.000 dòng.")
    @GetMapping("/stock-card")
    public ApiResponse<StockCardResponse> stockCard(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId, @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long serialId, @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(ledger.stockCard(user, new CardCriteria(companyId, warehouseId, productId, lotId,
                serialId, dateFrom, dateTo)));
    }

    @Operation(summary = "Nhập - xuất - tồn", description = "BR-RPT-22. Theo hàng × kho; đảo trừ vào chiều chứng từ gốc; "
            + "chuyển trạng thái chất lượng không tính. Giá trị: Giai đoạn 8. productIds dạng 1,2,3 (tối đa 200).")
    @GetMapping("/inventory-movement")
    public ApiResponse<InventoryMovementResponse> movement(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productCategoryId,
            @RequestParam(required = false) List<Long> productIds, @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(ledger.movement(user, new MovementCriteria(companyId, warehouseId, productCategoryId,
                productIds, dateFrom, dateTo)));
    }

    @Operation(summary = "Hàng tồn lâu", description = "BR-RPT-23. Còn tồn, không xuất trong days ngày (mặc định tham số "
            + "inventory.slow-moving-days); chưa từng xuất thì tính từ lần nhập gần nhất.")
    @GetMapping("/slow-moving")
    public ApiResponse<SlowMoving> slowMoving(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Integer days) {
        return ApiResponse.ok(exceptions.slowMoving(user, companyId, warehouseId, days));
    }

    @Operation(summary = "Hàng ngoại lệ", description = "BR-RPT-23. Còn tồn: hết hạn dùng, trạng thái khác ĐẠT (chờ kiểm, "
            + "cách ly, không đạt, hết hạn, thu hồi), lô / serial bị khóa.")
    @GetMapping("/stock-exceptions")
    public ApiResponse<Exceptions> stockExceptions(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId) {
        return ApiResponse.ok(exceptions.exceptions(user, companyId, warehouseId));
    }

    @Operation(summary = "Hàng thiếu hồ sơ", description = "BR-RPT-23. Hàng còn tồn thuộc loại hàng bắt buộc của loại hồ sơ "
            + "áp dụng cho hàng hóa nhưng không có hồ sơ còn hiệu lực.")
    @GetMapping("/missing-documents")
    public ApiResponse<MissingDocuments> missingDocuments(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId) {
        return ApiResponse.ok(exceptions.missingDocuments(user, companyId, warehouseId));
    }

    @Operation(summary = "Truy xuất lô / serial", description = "BR-RPT-24. Chọn lotId hoặc serialId: mọi phát sinh kho, "
            + "tổng theo khách (xuất bán − trả lại), tồn còn lại trong phạm vi kho.")
    @GetMapping("/lot-trace")
    public ApiResponse<LotTrace> lotTrace(@AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) Long lotId, @RequestParam(required = false) Long serialId) {
        return ApiResponse.ok(traceability.lotTrace(user, companyId, lotId, serialId));
    }

    @Operation(summary = "Truy xuất theo khách", description = "BR-RPT-24. Lô, serial khách đã nhận / trả lại trong kỳ.")
    @GetMapping("/customer-trace")
    public ApiResponse<CustomerTrace> customerTrace(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam Long customerId, @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(traceability.customerTrace(user, companyId, customerId, dateFrom, dateTo));
    }
}
