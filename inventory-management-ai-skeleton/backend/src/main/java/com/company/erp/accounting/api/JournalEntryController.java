package com.company.erp.accounting.api;

import com.company.erp.accounting.application.JournalQueryService;
import com.company.erp.accounting.application.dto.JournalEntryResponse;
import com.company.erp.accounting.application.dto.JournalEntrySummaryResponse;
import com.company.erp.accounting.domain.JournalEntryStatus;
import com.company.erp.common.api.ApiResponse;
import com.company.erp.common.api.PageResponse;
import com.company.erp.system.application.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bút toán (chỉ đọc): sinh tự động khi chứng từ nguồn ghi sổ, không sửa tay, chỉ đảo theo chứng từ gốc (BR-ACC-03).
 */
@Tag(name = "Bút toán")
@RestController
@RequestMapping("/api/v1/journal-entries")
public class JournalEntryController {

    private final JournalQueryService queryService;

    public JournalEntryController(JournalQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('journal:view')")
    public ApiResponse<PageResponse<JournalEntrySummaryResponse>> search(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String sourceType, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) JournalEntryStatus status, Pageable pageable) {
        JournalQueryService.JournalFilter filter = new JournalQueryService.JournalFilter(companyId, branchId,
                dateFrom, dateTo, sourceType, keyword, accountId, status);
        return ApiResponse.ok(PageResponse.from(queryService.search(user, filter, pageable), Function.identity()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('journal:view')")
    public ApiResponse<JournalEntryResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(queryService.get(user, id));
    }

    @Operation(summary = "Bút toán của một chứng từ nguồn", description = "Gồm bút toán gốc và bút toán đảo.")
    @GetMapping("/by-source")
    @PreAuthorize("hasAuthority('journal:view')")
    public ApiResponse<List<JournalEntryResponse>> bySource(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long companyId, @RequestParam String sourceType, @RequestParam Long sourceId) {
        return ApiResponse.ok(queryService.bySource(user, companyId, sourceType, sourceId));
    }
}
