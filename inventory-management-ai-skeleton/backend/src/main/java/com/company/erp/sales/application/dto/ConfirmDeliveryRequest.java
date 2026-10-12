package com.company.erp.sales.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Khách nhận hàng (BR-SAL-22): ngày nhận, người nhận, số biên bản giao nhận đã ký, ghi chú. */
public record ConfirmDeliveryRequest(
        @NotNull LocalDate receivedDate,
        @Size(max = 200) String receivedBy,
        @Size(max = 200) String signedDocumentRef,
        @Size(max = 500) String note) {
}
