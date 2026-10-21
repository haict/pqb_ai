package com.company.erp.inventory.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.inventory.application.StockCountService;
import com.company.erp.inventory.application.dto.CountedLinesRequest;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.StockCountRequest;
import com.company.erp.inventory.application.dto.StockCountResponse;
import com.company.erp.inventory.domain.StockCountStatus;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Kiểm kê kho (BR-INV-31, D-11): bắt đầu đếm khóa nhập xuất của kho; ghi sổ sinh phiếu điều chỉnh chênh lệch. */
@Tag(name = "Kiểm kê kho")
@RestController
@RequestMapping("/api/v1/stock-counts")
public class StockCountController {

    private final StockCountService service;

    public StockCountController(StockCountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('stock-count:view')")
    public ApiResponse<PageResponse<StockCountResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) StockCountStatus status, Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(user, companyId, warehouseId, status, pageable),
                Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('stock-count:view')")
    public ApiResponse<StockCountResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.get(user, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('stock-count:create')")
    public ApiResponse<StockCountResponse> create(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey, @Valid @RequestBody StockCountRequest request) {
        return ApiResponse.ok(service.create(user, idempotencyKey, request));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('stock-count:create')")
    public ApiResponse<StockCountResponse> start(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.start(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('stock-count:post')")
    public ApiResponse<StockCountResponse> post(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey) {
        return ApiResponse.ok(service.post(user, id, idempotencyKey));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('stock-count:create')")
    public ApiResponse<StockCountResponse> cancel(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id, @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
            @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(user, id, idempotencyKey, request));
    }

    /** Nhập số đếm khi đang đếm; dòng không có lineId là hàng tìm thấy ngoài sổ. */
    @PutMapping("/{id}/counts")
    @PreAuthorize("hasAuthority('stock-count:create')")
    public ApiResponse<StockCountResponse> count(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                 @Valid @RequestBody CountedLinesRequest request) {
        return ApiResponse.ok(service.count(user, id, request));
    }
}
