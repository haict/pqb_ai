package com.company.erp.inventory.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.InventoryQueryService;
import com.company.erp.inventory.application.LotSerialBlockService;
import com.company.erp.inventory.application.dto.FefoAllocationResponse;
import com.company.erp.inventory.application.dto.InventoryTransactionResponse;
import com.company.erp.inventory.application.dto.LotResponse;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.SerialResponse;
import com.company.erp.inventory.application.dto.StockAlertResponse;
import com.company.erp.inventory.application.dto.StockBalanceResponse;
import com.company.erp.inventory.application.dto.StockSummaryResponse;
import com.company.erp.inventory.domain.SerialStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tra cứu tồn kho (chỉ đọc, quyền {@code inventory:view}) và khóa / mở khóa lô, serial ({@code lot:block}). Không có
 * endpoint sửa trực tiếp số dư tồn (api.md mục 2).
 */
@Tag(name = "Tồn kho")
@RestController
@RequestMapping("/api/v1/inventory")
public class StockController {

    private final InventoryQueryService queryService;
    private final LotSerialBlockService blockService;

    public StockController(InventoryQueryService queryService, LotSerialBlockService blockService) {
        this.queryService = queryService;
        this.blockService = blockService;
    }

    @Operation(summary = "Tồn theo hàng, kho", description = "Tồn thực tế, bán được, giữ chỗ, khả dụng, đang về.")
    @GetMapping("/stock-summaries")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<PageResponse<StockSummaryResponse>> summaries(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId, Pageable pageable) {
        InventoryQueryService.StockFilter filter = new InventoryQueryService.StockFilter(companyId, warehouseId,
                productId, null, null, null);
        return ApiResponse.ok(PageResponse.from(queryService.summaries(user, filter, pageable), Function.identity()));
    }

    @Operation(summary = "Tồn chi tiết", description = "Theo vị trí, lô, serial, trạng thái chất lượng.")
    @GetMapping("/stock-balances")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<PageResponse<StockBalanceResponse>> balances(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId, @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long serialId,
            @RequestParam(required = false) QualityStatus qualityStatus, Pageable pageable) {
        InventoryQueryService.StockFilter filter = new InventoryQueryService.StockFilter(companyId, warehouseId,
                productId, lotId, serialId, qualityStatus);
        return ApiResponse.ok(PageResponse.from(queryService.balances(user, filter, pageable), Function.identity()));
    }

    @Operation(summary = "Sổ nhật ký kho", description = "Truy vết nhập, xuất theo hàng, lô, serial, chứng từ.")
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<PageResponse<InventoryTransactionResponse>> transactions(
            @AuthenticationPrincipal CurrentUser user, @RequestParam Long companyId,
            @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long lotId, @RequestParam(required = false) Long serialId,
            @RequestParam(required = false) String referenceType, @RequestParam(required = false) Long referenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Pageable pageable) {
        InventoryQueryService.LedgerFilter filter = new InventoryQueryService.LedgerFilter(companyId, warehouseId,
                productId, lotId, serialId, referenceType, referenceId, dateFrom, dateTo);
        return ApiResponse.ok(PageResponse.from(queryService.transactions(user, filter, pageable),
                Function.identity()));
    }

    @GetMapping("/lots")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<PageResponse<LotResponse>> lots(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(queryService.lots(user, companyId, productId, keyword, pageable),
                Function.identity()));
    }

    @GetMapping("/serials")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<PageResponse<SerialResponse>> serials(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) SerialStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(queryService.serials(user, companyId, productId, keyword, status,
                pageable), Function.identity()));
    }

    @Operation(summary = "Đề xuất lô theo FEFO", description = "Lô bán được, hạn dùng sớm nhất trước (BR-INV-21).")
    @GetMapping("/fefo-suggestions")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<List<FefoAllocationResponse>> fefo(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long warehouseId, @RequestParam Long productId,
            @RequestParam @Positive BigDecimal quantity) {
        return ApiResponse.ok(queryService.fefo(user, warehouseId, productId, quantity));
    }

    @Operation(summary = "Cảnh báo tồn kho", description = "Sắp / đã hết hạn còn tồn, tồn dưới định mức (BR-INV-40).")
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('inventory:view')")
    public ApiResponse<List<StockAlertResponse>> alerts(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId) {
        return ApiResponse.ok(queryService.alerts(user, companyId, warehouseId));
    }

    @PostMapping("/lots/{id}/block")
    @PreAuthorize("hasAuthority('lot:block')")
    public ApiResponse<Void> blockLot(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody ReasonRequest request) {
        blockService.blockLot(user, id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/lots/{id}/unblock")
    @PreAuthorize("hasAuthority('lot:block')")
    public ApiResponse<Void> unblockLot(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                        @Valid @RequestBody ReasonRequest request) {
        blockService.unblockLot(user, id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/serials/{id}/block")
    @PreAuthorize("hasAuthority('lot:block')")
    public ApiResponse<Void> blockSerial(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                         @Valid @RequestBody ReasonRequest request) {
        blockService.blockSerial(user, id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/serials/{id}/unblock")
    @PreAuthorize("hasAuthority('lot:block')")
    public ApiResponse<Void> unblockSerial(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                           @Valid @RequestBody ReasonRequest request) {
        blockService.unblockSerial(user, id, request);
        return ApiResponse.ok(null);
    }
}
