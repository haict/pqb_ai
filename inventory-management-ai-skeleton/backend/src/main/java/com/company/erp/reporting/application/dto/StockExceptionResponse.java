package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Hàng cần xử lý (BR-RPT-23): tồn lâu, ngoại lệ (hết hạn, không đạt, cách ly, thu hồi, chờ kiểm, lô / serial khóa),
 * thiếu hồ sơ bắt buộc. Mỗi danh sách là một báo cáo riêng; {@code generatedAt}: thời điểm lấy số liệu (BR-RPT-13).
 */
public final class StockExceptionResponse {

    private StockExceptionResponse() {
    }

    /** {@code idleDays}: số ngày từ lần xuất (chưa xuất: lần nhập) gần nhất đến hôm nay; null khi không có phát sinh. */
    public record SlowMoving(int thresholdDays, LocalDate cutoffDate, List<SlowRow> rows, Instant generatedAt) {
    }

    public record SlowRow(Long productId, String productCode, String productName, String unitName,
                          Long warehouseId, String warehouseCode, BigDecimal onHand, LocalDate lastInDate,
                          LocalDate lastOutDate, Long idleDays) {
    }

    /** {@code reasons}: EXPIRED (lô quá hạn dùng), QUALITY (trạng thái khác ĐẠT), BLOCKED (lô / serial bị khóa). */
    public record Exceptions(LocalDate asOf, List<ExceptionRow> rows, Instant generatedAt) {
    }

    public record ExceptionRow(Long productId, String productCode, String productName, String unitName,
                               String warehouseCode, String lotNo, LocalDate expiryDate, String serialNo,
                               String qualityStatus, List<String> reasons, BigDecimal onHand) {
    }

    /** {@code expiredDocumentNo}: có hồ sơ nhưng đã hết hạn ngày {@code expiredOn}; null: chưa có hồ sơ. */
    public record MissingDocuments(LocalDate asOf, List<MissingRow> rows, Instant generatedAt) {
    }

    public record MissingRow(Long productId, String productCode, String productName, String productType,
                             String documentTypeCode, String documentTypeName, String expiredDocumentNo,
                             LocalDate expiredOn, BigDecimal onHand) {
    }
}
