package com.company.erp.finance.application.dto;

import com.company.erp.common.api.PageResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Tuổi nợ tại ngày {@code asOf} (BR-AR-05): mốc {@code bucketDays} từ tham số; nhóm 1 quá hạn 1..m1 ngày, nhóm 2
 * m1+1..m2, nhóm 3 m2+1..m3, nhóm 4 trên m3.
 */
public record AgingResponse(
        LocalDate asOf,
        List<Integer> bucketDays,
        PageResponse<AgingRowResponse> rows,
        AgingRowResponse totals) {
}
