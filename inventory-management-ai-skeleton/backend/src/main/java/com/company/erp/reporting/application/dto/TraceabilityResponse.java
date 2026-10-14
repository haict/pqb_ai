package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Truy xuất lô / serial (BR-RPT-24): lô → nơi nhập, khách nhận; khách → lô, serial đã nhận. */
public final class TraceabilityResponse {

    private TraceabilityResponse() {
    }

    /**
     * Lô (hoặc serial): mọi dòng sổ kho theo thứ tự, tổng theo khách (xuất bán − trả lại), tồn hiện tại trong phạm vi
     * kho người xem.
     */
    public record LotTrace(Long productId, String productCode, String productName, String unitName, String lotNo,
                           LocalDate expiryDate, String serialNo, List<Line> lines, List<CustomerTotal> customers,
                           BigDecimal onHand, Instant generatedAt) {
    }

    /** Khách: hàng đã nhận / trả lại theo lô, serial trong khoảng ngày. */
    public record CustomerTrace(Long customerId, String customerCode, String customerName, LocalDate dateFrom,
                                LocalDate dateTo, List<Line> lines, Instant generatedAt) {
    }

    /** {@code quantity}: nhập +, xuất −; {@code referenceType}, {@code referenceId}: phiếu kho. */
    public record Line(LocalDate postingDate, String transactionType, String referenceType, Long referenceId,
                       String referenceNo, boolean reversal, String warehouseCode, Long partnerId, String partnerCode,
                       String partnerName, Long productId, String productCode, String productName, String unitName,
                       String lotNo, LocalDate expiryDate, String serialNo, BigDecimal quantity) {
    }

    /** {@code issued}: tổng đã xuất bán (sau đảo); {@code returned}: tổng khách trả lại; {@code net} = issued − returned. */
    public record CustomerTotal(Long partnerId, String partnerCode, String partnerName, BigDecimal issued,
                                BigDecimal returned, BigDecimal net) {
    }
}
