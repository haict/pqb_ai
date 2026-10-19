package com.company.erp.finance.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng công nợ đầu kỳ đọc từ file Excel để điền vào form lập / sửa chứng từ; chưa lưu gì. Hạn thanh toán trống thì
 * server tính khi lưu (ngày chứng từ + số ngày nợ của đối tượng).
 */
public record DebtOpeningLineImportResponse(int created, List<Line> lines) {

    public record Line(Long partnerId, String partnerCode, String partnerName, LocalDate dueDate, BigDecimal amount,
                       String note) {
    }
}
