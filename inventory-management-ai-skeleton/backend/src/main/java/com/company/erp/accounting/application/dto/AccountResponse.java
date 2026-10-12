package com.company.erp.accounting.application.dto;

import com.company.erp.accounting.domain.BalanceSide;
import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;

/** Tài khoản; {@code postable = false} là tài khoản tổng hợp (có tài khoản con), không hạch toán trực tiếp. */
public record AccountResponse(
        Long id,
        Long companyId,
        String code,
        String name,
        Long parentId,
        String parentCode,
        int level,
        BalanceSide balanceSide,
        boolean postable,
        boolean partnerRequired,
        RecordStatus status,
        int version,
        Instant updatedAt) {
}
