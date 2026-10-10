package com.company.erp.masterdata.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.masterdata.application.WarehouseService;
import com.company.erp.masterdata.application.dto.LocationResponse;
import com.company.erp.masterdata.application.dto.UpdateLocationRequest;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.dto.ChangeStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Kho")
@RestController
@RequestMapping("/api/v1/warehouse-locations")
public class WarehouseLocationController {

    private final WarehouseService warehouseService;

    public WarehouseLocationController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<LocationResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                @Valid @RequestBody UpdateLocationRequest request) {
        return ApiResponse.ok(warehouseService.updateLocation(user, id, request));
    }

    @Operation(summary = "Ngừng / kích hoạt vị trí", description = "Vị trí mặc định không ngừng được (409 DATA_IN_USE).")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public ApiResponse<LocationResponse> changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                      @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.ok(warehouseService.changeLocationStatus(user, id, request));
    }
}
