package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.Size;

/** Chuyển sang đang giao; ghi người / đơn vị vận chuyển (tùy chọn). */
public record ShipRequest(
        @Size(max = 200) String shipperName) {
}
