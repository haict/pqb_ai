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
import com.company.erp.masterdata.application.WarehouseService;
import com.company.erp.masterdata.application.dto.LocationRequest;
import com.company.erp.masterdata.application.dto.LocationResponse;
import com.company.erp.masterdata.application.dto.UpdateWarehouseRequest;
import com.company.erp.masterdata.application.dto.WarehouseRequest;
import com.company.erp.masterdata.application.dto.WarehouseResponse;
import com.company.erp.masterdata.domain.WarehouseType;
import java.util.List;

/** Kho (BR-MD-40, BR-MD-41). Kho ngoài phạm vi dữ liệu (chi nhánh / kho được gán) trả 404. */
@Tag(name = "Kho")
@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('warehouse:view')")
    public ApiResponse<PageResponse<WarehouseResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) WarehouseType warehouseType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RecordStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(warehouseService.search(user, companyId, branchId, warehouseType, keyword, status, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:view')")
    public ApiResponse<WarehouseResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(warehouseService.get(user, id));
    }

    @Operation(summary = "Thêm mới", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseResponse> create(@AuthenticationPrincipal CurrentUser user,
                                      @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                      @Valid @RequestBody WarehouseRequest request) {
        return ApiResponse.ok(warehouseService.create(user, idempotencyKey, request));
    }

    @Operation(summary = "Sửa", description = "Gửi version đã đọc; dữ liệu đã đổi: 409 CONCURRENT_MODIFICATION.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                      @Valid @RequestBody UpdateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.update(user, id, request));
    }

    @Operation(summary = "Ngừng hoạt động / kích hoạt lại")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<WarehouseResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(warehouseService.changeStatus(user, id, request));
    }

    @GetMapping("/{id}/locations")
    @PreAuthorize("hasAuthority('warehouse:view')")
    public ApiResponse<List<LocationResponse>> locations(@AuthenticationPrincipal CurrentUser user,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(warehouseService.locations(user, id));
    }

    @Operation(summary = "Thêm vị trí kho", description = "Bắt buộc header Idempotency-Key.")
    @PostMapping("/{id}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<LocationResponse> addLocation(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                     @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                     @Valid @RequestBody LocationRequest request) {
        return ApiResponse.ok(warehouseService.addLocation(user, id, idempotencyKey, request));
    }
}
