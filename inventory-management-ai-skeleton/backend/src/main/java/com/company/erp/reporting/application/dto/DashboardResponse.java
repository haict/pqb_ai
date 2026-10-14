package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard điều hành đợt 10C (BR-RPT-01, 02, 04..06, 10): chỉ tiêu tổng hợp và danh sách chi tiết đến chứng từ nguồn;
 * tổng chi tiết bằng số trên chỉ tiêu (BR-RPT-11). {@code today}: ngày nghiệp vụ tính quá hạn / hết hạn,
 * {@code generatedAt}: thời điểm lấy số liệu (BR-RPT-13).
 */
public final class DashboardResponse {

    private DashboardResponse() {
    }

    public record Summary(LocalDate dateFrom, LocalDate dateTo, LocalDate today, Trade revenue, Trade purchases,
                          Debt receivables, Debt payables, Expiry expiry, Backlog purchaseBacklog,
                          Backlog salesBacklog, List<Ranked> topProducts, List<Ranked> topCustomers,
                          List<Ranked> topSuppliers, Instant generatedAt) {
    }

    /**
     * Doanh thu (đơn bán) hoặc giá trị mua (đơn mua) đã ghi sổ trong kỳ, chưa thuế, sau chiết khấu, quy đổi VND;
     * {@code deductionAmount}: trả lại / giảm giá; chứng từ đảo trừ vào ngày đảo. {@code netAmount} = gross − giảm trừ.
     * Giá vốn, lợi nhuận chưa có đến Giai đoạn 8 ({@code costAvailable = false}).
     */
    public record Trade(BigDecimal grossAmount, BigDecimal deductionAmount, BigDecimal netAmount, long documentCount,
                        boolean costAvailable) {
    }

    /**
     * Công nợ còn lại tính đến hôm nay; quá hạn chia theo mốc tuổi nợ của công ty (tham số
     * {@code finance.aging-bucket-1..3-days}, như báo cáo tuổi nợ).
     */
    public record Debt(BigDecimal totalAmount, long itemCount, BigDecimal overdueAmount, long overdueCount,
                       List<AgingBucket> overdueBuckets) {
    }

    /** Số ngày quá hạn từ {@code fromDays} đến {@code toDays}; {@code toDays} null: không giới hạn trên. */
    public record AgingBucket(int fromDays, Integer toDays, BigDecimal amount) {
    }

    /** Dòng lô × kho còn tồn: đã quá hạn và sắp hết hạn trong {@code warningDays} ngày. */
    public record Expiry(int warningDays, LocalDate warningUntil, long expiredCount, long expiringCount) {
    }

    /** Đơn chưa giao đủ; {@code overdueCount}: đã quá ngày dự kiến. */
    public record Backlog(long orderCount, BigDecimal totalAmount, long overdueCount) {
    }

    /** Một dòng top: hàng, khách hoặc NCC theo giá trị thuần trong kỳ. */
    public record Ranked(Long id, String code, String name, BigDecimal amount) {
    }

    /** Chứng từ cấu thành doanh thu / giá trị mua; {@code total} bằng {@code Trade.netAmount} khi không lọc. */
    public record TradeDetails(String kind, LocalDate dateFrom, LocalDate dateTo, List<TradeRow> rows,
                               BigDecimal total, Instant generatedAt) {
    }

    /** Số tiền có dấu: trả lại / giảm giá âm, dòng đảo đổi dấu chứng từ gốc. */
    public record TradeRow(LocalDate entryDate, String documentType, Long documentId, String documentNo,
                           boolean reversal, String partnerCode, String partnerName, BigDecimal amount) {
    }

    public record DebtDetails(String debtType, LocalDate today, List<DebtRow> rows, BigDecimal totalAmount,
                              BigDecimal overdueAmount, Instant generatedAt) {
    }

    /** Khoản công nợ còn lại; {@code overdueDays} 0 khi chưa đến hạn. */
    public record DebtRow(Long debtItemId, String sourceType, Long sourceId, String sourceNo, String invoiceNo,
                          String partnerCode, String partnerName, LocalDate documentDate, LocalDate dueDate,
                          BigDecimal originalAmount, BigDecimal settledAmount, BigDecimal remainingAmount,
                          long overdueDays) {
    }

    public record ExpiryDetails(int warningDays, LocalDate today, LocalDate warningUntil, List<ExpiryRow> rows,
                                Instant generatedAt) {
    }

    /** {@code daysLeft} âm: đã quá hạn. Số lượng theo đơn vị cơ sở. */
    public record ExpiryRow(Long productId, String productCode, String productName, String unitName,
                            String warehouseCode, Long lotId, String lotNo, LocalDate expiryDate, long daysLeft,
                            BigDecimal onHand) {
    }

    public record BacklogDetails(String documentType, LocalDate today, List<BacklogRow> rows, BigDecimal totalAmount,
                                 Instant generatedAt) {
    }

    /** Đơn chưa giao đủ; tiền gồm thuế; {@code overdueDays} 0 khi chưa quá ngày dự kiến. */
    public record BacklogRow(Long documentId, String documentNo, LocalDate documentDate, String partnerCode,
                             String partnerName, String status, String deliveryStatus, LocalDate expectedDate,
                             long overdueDays, BigDecimal totalAmount) {
    }
}
