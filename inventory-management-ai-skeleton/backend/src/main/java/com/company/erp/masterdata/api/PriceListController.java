package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.company.erp.masterdata.application.PriceListService;
import com.company.erp.masterdata.application.dto.PriceListItemRequest;
import com.company.erp.masterdata.application.dto.PriceListItemResponse;
import com.company.erp.masterdata.application.dto.PriceListRequest;
import com.company.erp.masterdata.application.dto.PriceListResponse;
import com.company.erp.masterdata.application.dto.UpdatePriceListRequest;
import com.company.erp.masterdata.domain.PriceType;
import org.springframework.web.bind.annotation.DeleteMapping;

/** Bảng giá (BR-MD-42). Giá mua tham khảo chỉ hiện với quyền purchase-price:view (BR-SYS-05). */
@Tag(name = "Bảng giá")
@RestController
@RequestMapping("/api/v1/price-lists")
public class PriceListController {

    private final PriceListService priceListService;

    public PriceListController(PriceListService priceListService) {
        this.priceListService = priceListService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('price:view')")
    public ApiResponse<PageResponse<PriceListResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) PriceType priceType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RecordStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(priceListService.search(user, companyId, priceType, keyword, status, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('price:view')")
    public ApiResponse<PriceListResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(priceListService.get(user, id));
    }

    @Operation(summary = "Thêm mới", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('price:manage')")
    public ApiResponse<PriceListResponse> create(@AuthenticationPrincipal CurrentUser user,
                                      @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                      @Valid @RequestBody PriceListRequest request) {
        return ApiResponse.ok(priceListService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa", description = "Gửi version đã đọc; dữ liệu đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('price:manage')")
    public ApiResponse<PriceListResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody UpdatePriceListRequest request) {
        return ApiResponse.ok(priceListService.update(user, id, request));
    }

    @Operation(summary = "Ngừng hoạt động / kích hoạt lại")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('price:manage')")
    public ApiResponse<PriceListResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(priceListService.changeStatus(user, id, request));
    }

    @Operation(summary = "Giá trong bảng giá (phân trang)")
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('price:view')")
    public ApiResponse<PageResponse<PriceListItemResponse>> items(@AuthenticationPrincipal CurrentUser user,
                                                                  @PathVariable Long id, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(priceListService.items(user, id, pageable), Function.identity()));
    }

    @Operation(summary = "Đặt giá theo hàng, đơn vị, bậc số lượng (đã có thì đổi giá)")
    @PutMapping("/{id}/items")
    @PreAuthorize("hasAuthority('price:manage')")
    public ApiResponse<PriceListItemResponse> upsertItem(@AuthenticationPrincipal CurrentUser user,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody PriceListItemRequest request) {
        return ApiResponse.ok(priceListService.upsertItem(user, id, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('price:manage')")
    public ApiResponse<Void> deleteItem(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                        @PathVariable Long itemId) {
        priceListService.deleteItem(user, id, itemId);
        return ApiResponse.ok(null);
    }
}
