package com.company.erp.reporting.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.reporting.application.PurchaseReportService;
import com.company.erp.reporting.application.SalesOrderReportService;
import com.company.erp.reporting.application.dto.OrderReportResponse.OrderProgress;
import com.company.erp.reporting.application.dto.OrderReportResponse.PriceHistory;
import com.company.erp.reporting.application.dto.OrderReportResponse.SalesJournal;
import com.company.erp.reporting.application.dto.OrderReportResponse.StockBacklog;
import com.company.erp.reporting.application.dto.OrderReportResponse.SupplierDefects;
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

/** Báo cáo mua / bán M10 đợt 10B (BR-RPT-25, 26, ADR-18). */
@Tag(name = "Báo cáo - mua, bán")
@RestController
@RequestMapping("/api/v1/reports")
public class OrderReportController {

    private final PurchaseReportService purchases;
    private final SalesOrderReportService sales;

    public OrderReportController(PurchaseReportService purchases, SalesOrderReportService sales) {
        this.purchases = purchases;
        this.sales = sales;
    }

    @Operation(summary = "Tình hình thực hiện đơn mua", description = "BR-RPT-25. Theo ngày chứng từ: trạng thái, trễ hẹn "
            + "nhận, trả lại / giảm giá, đã thanh toán, còn phải trả (tiền gồm thuế). Bỏ đơn đã hủy trừ khi lọc status.")
    @GetMapping("/purchase-order-progress")
    @PreAuthorize("hasAuthority('purchase-report:view')")
    public ApiResponse<OrderProgress> purchaseOrderProgress(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String status,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(purchases.orderProgress(user, companyId, branchId, supplierId, status, dateFrom, dateTo));
    }

    @Operation(summary = "Lịch sử giá mua", description = "BR-RPT-25. Dòng hàng của đơn mua đang ghi sổ theo ngày hạch "
            + "toán; tổng hợp giá thấp / cao nhất / gần nhất theo đơn vị cơ sở. productIds dạng 1,2,3 (tối đa 200).")
    @GetMapping("/purchase-price-history")
    @PreAuthorize("hasAuthority('purchase-report:view')")
    public ApiResponse<PriceHistory> purchasePriceHistory(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) List<Long> productIds, @RequestParam(required = false) Long supplierId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(purchases.priceHistory(user, companyId, branchId, productIds, supplierId, dateFrom,
                dateTo));
    }

    @Operation(summary = "Hàng lỗi theo NCC", description = "BR-RPT-25. Phiếu hàng lỗi khách báo theo ngày nhận; NCC truy "
            + "qua lô (lần nhập mua đầu tiên của lô); không xác định: supplierId null.")
    @GetMapping("/supplier-defects")
    @PreAuthorize("hasAuthority('purchase-report:view')")
    public ApiResponse<SupplierDefects> supplierDefects(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(purchases.supplierDefects(user, companyId, branchId, dateFrom, dateTo));
    }

    @Operation(summary = "Tình hình thực hiện đơn bán", description = "BR-RPT-26. Theo ngày chứng từ: trạng thái, giao "
            + "hàng, trễ hẹn, trả lại / giảm giá, đã thu, còn phải thu (tiền gồm thuế).")
    @GetMapping("/sales-order-progress")
    @PreAuthorize("hasAuthority('sales-order-report:view')")
    public ApiResponse<OrderProgress> salesOrderProgress(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long customerId, @RequestParam(required = false) String status,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(sales.orderProgress(user, companyId, branchId, customerId, status, dateFrom, dateTo));
    }

    @Operation(summary = "Sổ nhật ký bán hàng", description = "BR-RPT-26. Theo chứng từ bán đã ghi sổ: phải thu, doanh thu "
            + "hàng hóa / dịch vụ, giảm trừ, thuế GTGT; chứng từ đảo có dòng âm theo ngày đảo.")
    @GetMapping("/sales-journal")
    @PreAuthorize("hasAuthority('sales-order-report:view')")
    public ApiResponse<SalesJournal> salesJournal(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return ApiResponse.ok(sales.salesJournal(user, companyId, branchId, dateFrom, dateTo));
    }

    @Operation(summary = "Tồn so với đặt hàng chưa giao", description = "BR-RPT-26, H-06. Đơn bán đã gửi / duyệt chưa ghi "
            + "sổ theo hàng × kho, so với tồn, giữ chỗ, đang về; thiếu = đặt − tồn − đang về.")
    @GetMapping("/stock-backlog")
    @PreAuthorize("hasAuthority('sales-order-report:view')")
    public ApiResponse<StockBacklog> stockBacklog(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> productIds) {
        return ApiResponse.ok(sales.stockBacklog(user, companyId, warehouseId, productIds));
    }
}
