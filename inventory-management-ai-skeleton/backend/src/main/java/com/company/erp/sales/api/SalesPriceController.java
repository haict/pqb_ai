package com.company.erp.sales.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.sales.application.SalesQuoteService;
import com.company.erp.sales.application.dto.PriceSuggestionResponse;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tra giá bán áp dụng (BR-SAL-02). */
@Tag(name = "Giá bán")
@RestController
@RequestMapping("/api/v1/sales")
public class SalesPriceController {

    private final SalesQuoteService service;

    public SalesPriceController(SalesQuoteService service) {
        this.service = service;
    }

    @Operation(summary = "Giá áp dụng", description = "Thứ tự: bảng giá riêng của khách → theo nhóm khách / đại lý → "
            + "giá niêm yết; kèm giá sàn, chiết khấu đại lý, thuế suất mặc định. Giá hợp đồng thầu: Giai đoạn 7.")
    @GetMapping("/price")
    @PreAuthorize("hasAuthority('sales-quote:view') or hasAuthority('sales-order:view')")
    public ApiResponse<PriceSuggestionResponse> price(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) Long customerId, @RequestParam Long productId, @RequestParam Long unitId,
            @RequestParam(required = false) BigDecimal quantity, @RequestParam(required = false) LocalDate date) {
        return ApiResponse.ok(service.price(user, customerId, productId, unitId, quantity, date));
    }
}
