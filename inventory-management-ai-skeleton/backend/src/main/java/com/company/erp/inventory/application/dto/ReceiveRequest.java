package com.company.erp.inventory.application.dto;

import java.time.LocalDate;

/** Nhận hàng chuyển kho khác chi nhánh; ngày hạch toán nhận trống thì là hôm nay. */
public record ReceiveRequest(LocalDate postingDate) {
}
