package com.company.erp.sales.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu in phiếu xuất kho của đơn bán đã ghi sổ. {@code slipNo}: số phiếu xuất kho sinh khi ghi sổ;
 * {@code debitAccounts}, {@code creditAccounts}: tài khoản trong bút toán doanh thu phần hàng hóa (không gồm dịch vụ,
 * thuế GTGT). {@code totalAmount}: cộng tiền hàng; {@code taxRates}: các thuế suất GTGT (%) của dòng hàng hóa;
 * {@code taxAmount}, {@code paymentAmount}: tiền thuế và tổng thanh toán của dòng hàng hóa. Đơn giá, thành tiền là
 * giá bán sau chiết khấu, trước thuế; {@code items}: lô / hạn / serial thực xuất.
 */
public record StockOutSlipResponse(
        String companyName,
        String companyAddress,
        Long orderId,
        String orderNo,
        LocalDate orderDate,
        String slipNo,
        LocalDate slipDate,
        List<String> debitAccounts,
        List<String> creditAccounts,
        String customerName,
        String customerAddress,
        String customerTaxCode,
        String receiverName,
        String description,
        String einvoiceSeries,
        String einvoiceNo,
        LocalDate einvoiceDate,
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
            Long unitId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            List<Item> items) {
    }

    public record Item(String lotNo, LocalDate expiryDate, String serialNo, BigDecimal baseQuantity) {
    }
}
