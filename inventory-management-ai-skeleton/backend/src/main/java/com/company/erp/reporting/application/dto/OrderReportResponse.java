package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Báo cáo mua / bán đợt 10B (BR-RPT-25, 26); {@code generatedAt}: thời điểm lấy số liệu (BR-RPT-13). */
public final class OrderReportResponse {

    private OrderReportResponse() {
    }

    /** Tình hình thực hiện đơn mua (DMH) hoặc đơn bán (DBH). */
    public record OrderProgress(String documentType, LocalDate dateFrom, LocalDate dateTo, List<OrderRow> rows,
                                OrderTotals total, Instant generatedAt) {
    }

    /**
     * {@code overdueDays}: số ngày trễ so với ngày dự kiến (đơn chưa ghi sổ; đơn bán đã ghi sổ mà khách chưa nhận), null
     * khi không trễ. Tiền gồm thuế: {@code returnedAmount} trả lại / giảm giá, {@code paidAmount} đã thanh toán / thu,
     * {@code remainingAmount} còn nợ.
     */
    public record OrderRow(Long documentId, String documentNo, LocalDate documentDate, LocalDate postingDate,
                           Long partnerId, String partnerCode, String partnerName, String status,
                           LocalDate expectedDate, String deliveryStatus, Long overdueDays, BigDecimal totalAmount,
                           BigDecimal returnedAmount, BigDecimal paidAmount, BigDecimal remainingAmount) {
    }

    public record OrderTotals(long orderCount, long overdueCount, BigDecimal totalAmount, BigDecimal returnedAmount,
                              BigDecimal paidAmount, BigDecimal remainingAmount) {
    }

    /** Lịch sử giá mua: từng lần mua và tổng hợp theo hàng (giá sau chiết khấu theo đơn vị cơ sở). */
    public record PriceHistory(LocalDate dateFrom, LocalDate dateTo, List<PriceRow> rows,
                               List<PriceSummary> summaries, Instant generatedAt) {
    }

    public record PriceRow(Long productId, String productCode, String productName, LocalDate postingDate,
                           Long documentId, String documentNo, String supplierCode, String supplierName,
                           String unitName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercent,
                           BigDecimal netUnitPrice, String baseUnitName, BigDecimal netBasePrice) {
    }

    public record PriceSummary(Long productId, String productCode, String productName, String baseUnitName,
                               long purchaseCount, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal lastPrice,
                               LocalDate lastDate, String lastSupplierName) {
    }

    /** Hàng lỗi theo NCC: từng phiếu và tổng theo NCC ({@code supplierId} null: không xác định được NCC của lô). */
    public record SupplierDefects(LocalDate dateFrom, LocalDate dateTo, List<DefectRow> rows,
                                  List<SupplierTotal> suppliers, Instant generatedAt) {
    }

    public record DefectRow(Long complaintId, String complaintNo, LocalDate receivedDate, String customerCode,
                            String customerName, Long productId, String productCode, String productName,
                            String lotNo, String serialNo, BigDecimal quantity, String status, String decision,
                            Long supplierId, String supplierCode, String supplierName) {
    }

    public record SupplierTotal(Long supplierId, String supplierCode, String supplierName, long complaintCount,
                                long productCount, BigDecimal quantity) {
    }

    /** Sổ nhật ký bán hàng theo chứng từ; dòng đảo mang số âm. */
    public record SalesJournal(LocalDate dateFrom, LocalDate dateTo, List<JournalRow> rows, JournalTotals total,
                               Instant generatedAt) {
    }

    /**
     * {@code receivable}: phải thu (tổng thanh toán, trả lại / giảm giá âm); doanh thu hàng hóa, dịch vụ (trước thuế);
     * {@code deductionAmount}: trả lại / giảm giá (trước thuế); {@code taxAmount}: thuế GTGT đầu ra (giảm trừ âm).
     */
    public record JournalRow(LocalDate postingDate, String documentType, Long documentId, String documentNo,
                             LocalDate documentDate, LocalDate invoiceDate, String invoiceNo, String customerCode,
                             String customerName, String description, BigDecimal receivable, BigDecimal goodsAmount,
                             BigDecimal serviceAmount, BigDecimal deductionAmount, BigDecimal taxAmount,
                             boolean reversal) {
    }

    public record JournalTotals(BigDecimal receivable, BigDecimal goodsAmount, BigDecimal serviceAmount,
                                BigDecimal deductionAmount, BigDecimal taxAmount) {
    }

    /** Tồn so với đặt hàng chưa giao (đơn vị cơ sở). */
    public record StockBacklog(List<BacklogRow> rows, Instant generatedAt) {
    }

    /**
     * {@code available} = tồn thực tế − giữ chỗ; {@code shortage} = đặt chưa giao − tồn thực tế − đang về (không âm):
     * phần phải mua thêm nếu mọi đơn đều giao.
     */
    public record BacklogRow(Long productId, String productCode, String productName, String unitName,
                             Long warehouseId, String warehouseCode, BigDecimal ordered, long orderCount,
                             BigDecimal onHand, BigDecimal reserved, BigDecimal available, BigDecimal incoming,
                             BigDecimal shortage) {
    }
}
