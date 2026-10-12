package com.company.erp.sales.application;

import com.company.erp.sales.application.dto.PaymentScheduleResponse;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderPaymentTerm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Lịch thu tiền theo đơn (BR-SAL-50): hạn từng đợt theo mốc (ngày đơn, ngày khách nhận hàng đầu tiên, ngày hóa đơn
 * đầu tiên; nghiệm thu ở Giai đoạn 7), số đã thu phân bổ theo thứ tự đợt từ tiền đã thu của các hóa đơn thuộc đơn.
 */
@Component
class SalesOrderFacts {

    private final SalesOrderEvents events;

    SalesOrderFacts(SalesOrderEvents events) {
        this.events = events;
    }

    List<PaymentScheduleResponse> paymentSchedule(SalesOrder order, LocalDate today) {
        LocalDate delivered = events.firstDeliveryDate(order.getId());
        LocalDate invoiced = events.firstInvoiceDate(order.getId());
        BigDecimal paid = events.paidAmount(order);
        List<PaymentScheduleResponse> rows = new ArrayList<>();
        for (SalesOrderPaymentTerm term : order.getPaymentTerms()) {
            LocalDate base = switch (term.getDueBasis()) {
                case ORDER_DATE -> order.getDocumentDate();
                case DELIVERY -> delivered;
                case INVOICE -> invoiced;
                case ACCEPTANCE -> null;
            };
            LocalDate dueDate = base == null ? null : base.plusDays(term.getDueDays());
            BigDecimal applied = paid.min(term.getAmount()).max(BigDecimal.ZERO);
            paid = paid.subtract(applied);
            BigDecimal remaining = term.getAmount().subtract(applied);
            rows.add(new PaymentScheduleResponse(term.getInstallmentNo(), term.getPercent(), term.getAmount(),
                    term.getDueBasis(), term.getDueDays(), dueDate, applied, remaining,
                    dueDate != null && remaining.signum() > 0 && dueDate.isBefore(today), term.getDescription()));
        }
        return rows;
    }
}
