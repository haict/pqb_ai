package com.company.erp.system.api;

import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.FiscalPeriodService;
import com.company.erp.system.application.PeriodLockService;
import com.company.erp.system.application.dto.FiscalPeriodResponse;
import com.company.erp.system.application.dto.GeneratePeriodsRequest;
import com.company.erp.system.application.dto.GeneratePeriodsResponse;
import com.company.erp.system.application.dto.LockPeriodRequest;
import com.company.erp.system.application.dto.PeriodLockResult;
import com.company.erp.system.application.dto.UnlockPeriodRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Kỳ kế toán, kỳ kho")
@RestController
@RequestMapping("/api/v1/fiscal-periods")
public class FiscalPeriodController {

    private final FiscalPeriodService periodService;
    private final PeriodLockService lockService;

    public FiscalPeriodController(FiscalPeriodService periodService, PeriodLockService lockService) {
        this.periodService = periodService;
        this.lockService = lockService;
    }

    @Operation(summary = "Kỳ của một năm kèm trạng thái khóa từng phân hệ của một chi nhánh",
            description = "Tối đa 12 dòng nên không phân trang. Chi nhánh ngoài phạm vi: 404.")
    @GetMapping
    @PreAuthorize("hasAuthority('period:view')")
    public ApiResponse<List<FiscalPeriodResponse>> list(@AuthenticationPrincipal CurrentUser user,
                                                        @RequestParam Long companyId,
                                                        @RequestParam int year,
                                                        @RequestParam Long branchId) {
        return ApiResponse.ok(periodService.list(user, companyId, year, branchId));
    }

    @Operation(summary = "Tạo 12 kỳ tháng của một năm",
            description = "Cần phạm vi toàn công ty. Tháng đã có thì bỏ qua. Bắt buộc header Idempotency-Key.")
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('period:manage')")
    public ApiResponse<GeneratePeriodsResponse> generate(@AuthenticationPrincipal CurrentUser user,
                                                         @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                         @Valid @RequestBody GeneratePeriodsRequest request) {
        return ApiResponse.ok(periodService.generate(user, idempotencyKey, request));
    }

    @Operation(summary = "Khóa kỳ cho một phân hệ ở các chi nhánh",
            description = "Kỳ trước phải đã khóa; kỳ kế toán khóa sau kỳ kho (BR-SYS-31). Lỗi: 409 INVALID_PERIOD_STATE, "
                    + "422 PERIOD_ORDER_VIOLATION, 422 PERIOD_CLOSE_PRECONDITION. Bắt buộc header Idempotency-Key.")
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('period:manage')")
    public ApiResponse<PeriodLockResult> lock(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                              @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                              @Valid @RequestBody LockPeriodRequest request) {
        return ApiResponse.ok(lockService.lock(user, id, idempotencyKey, request));
    }

    @Operation(summary = "Mở lại kỳ đã khóa (Kế toán trưởng, Giám đốc)",
            description = "Bắt buộc lý do (BR-SYS-32). Kỳ sau phải đang mở; kỳ kho chỉ mở khi kỳ kế toán cùng tháng "
                    + "đang mở. Bắt buộc header Idempotency-Key.")
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('period:unlock')")
    public ApiResponse<PeriodLockResult> unlock(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                @RequestHeader(IdempotentCommand.HEADER) String idempotencyKey,
                                                @Valid @RequestBody UnlockPeriodRequest request) {
        return ApiResponse.ok(lockService.unlock(user, id, idempotencyKey, request));
    }
}
