package com.company.erp.sales.application;

import com.company.erp.finance.application.DebtExposureService;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mốc thực tế của đơn bán cho lịch thu tiền (BR-SAL-50, D-54): ngày khách nhận hàng, ngày hóa đơn (số hóa đơn điện
 * tử, chưa có thì ngày ghi sổ), tiền đã thu / bù trừ của khoản công nợ sinh từ đơn.
 */
@Component
class SalesOrderEvents {

    private final DebtExposureService debts;

    SalesOrderEvents(DebtExposureService debts) {
        this.debts = debts;
    }

    LocalDate deliveryDate(SalesOrder order) {
        return order.getReceivedDate();
    }

    LocalDate invoiceDate(SalesOrder order) {
        if (order.getStatus() != SalesDocumentStatus.POSTED) {
            return null;
        }
        return order.getEinvoiceDate() != null ? order.getEinvoiceDate() : order.getPostingDate();
    }

    BigDecimal paidAmount(SalesOrder order) {
        if (order.getStatus() != SalesDocumentStatus.POSTED) {
            return BigDecimal.ZERO;
        }
        return debts.settledBySource(order.getCompanyId(), SalesOrder.TYPE_CODE, List.of(order.getId()))
                .getOrDefault(order.getId(), BigDecimal.ZERO);
    }
}
