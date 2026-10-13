package com.company.erp.purchasing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu in phiếu nhập kho của đơn mua đã ghi sổ (BR-PUR-08). {@code slipNo}: số phiếu nhập kho sinh khi ghi sổ;
 * {@code debitAccounts}, {@code creditAccounts}: tài khoản trong bút toán phần hàng hóa (không gồm dịch vụ, thuế GTGT).
 * {@code totalAmount}: cộng tiền hàng; {@code taxRates}: các thuế suất GTGT (%) của dòng hàng hóa; {@code taxAmount},
 * {@code paymentAmount}: tiền thuế và tổng thanh toán của dòng hàng hóa. Đơn giá, thành tiền sau chiết khấu, trước
 * thuế; chỉ dòng hàng hóa.
 */
public record StockInSlipResponse(
        String companyName,
        String companyAddress,
        Long orderId,
        String orderNo,
        String slipNo,
        LocalDate slipDate,
        List<String> debitAccounts,
        List<String> creditAccounts,
        String supplierName,
        String supplierAddress,
        String supplierTaxCode,
        String description,
        String invoiceSeries,
        String invoiceNo,
        LocalDate invoiceDate,
        String warehouseCode,
        String warehouseName,
        List<Line> lines,
        BigDecimal totalAmount,
        List<BigDecimal> taxRates,
        BigDecimal taxAmount,
        BigDecimal paymentAmount) {

    public record Line(
            int lineNo,
            String productCode,
            String productName,
            String lotNo,
            LocalDate expiryDate,
            Long unitId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount) {
    }
}
