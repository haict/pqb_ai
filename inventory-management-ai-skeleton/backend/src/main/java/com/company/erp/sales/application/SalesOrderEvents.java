package com.company.erp.sales.application;

import com.company.erp.finance.application.DebtExposureService;
import com.company.erp.sales.domain.SalesInvoice;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.infrastructure.persistence.SalesDeliveryRepository;
import com.company.erp.sales.infrastructure.persistence.SalesInvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Mốc thực tế của đơn bán từ chứng từ đích trong module (khách nhận hàng, hóa đơn bán, tiền đã thu của hóa đơn) cho
 * lịch thu tiền (BR-SAL-50). Hóa đơn gồm dòng của nhiều đơn: tiền đã thu chia cho đơn theo tỷ lệ giá trị dòng của đơn
 * trên tổng hóa đơn `[Giả định]`.
 */
@Component
class SalesOrderEvents {

    private final SalesDeliveryRepository deliveryRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final DebtExposureService debts;

    SalesOrderEvents(SalesDeliveryRepository deliveryRepository, SalesInvoiceRepository invoiceRepository,
                     DebtExposureService debts) {
        this.deliveryRepository = deliveryRepository;
        this.invoiceRepository = invoiceRepository;
        this.debts = debts;
    }

    LocalDate firstDeliveryDate(Long orderId) {
        return deliveryRepository.firstReceivedDate(orderId).orElse(null);
    }

    LocalDate firstInvoiceDate(Long orderId) {
        return invoiceRepository.findPostedByOrder(orderId).stream().map(SalesInvoice::getInvoiceDate)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    BigDecimal paidAmount(SalesOrder order) {
        List<SalesInvoice> invoices = invoiceRepository.findPostedByOrder(order.getId());
        if (invoices.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> settled = debts.settledBySource(order.getCompanyId(), SalesInvoice.TYPE_CODE,
                invoices.stream().map(SalesInvoice::getId).toList());
        Set<Long> orderLines = order.getLines().stream().map(SalesOrderLine::getId).collect(Collectors.toSet());
        BigDecimal paid = BigDecimal.ZERO;
        for (SalesInvoice invoice : invoices) {
            BigDecimal paidOnInvoice = settled.getOrDefault(invoice.getId(), BigDecimal.ZERO);
            if (paidOnInvoice.signum() == 0 || invoice.getTotalAmount().signum() == 0) {
                continue;
            }
            BigDecimal share = invoice.getLines().stream().filter(line -> orderLines.contains(line.getOrderLineId()))
                    .map(line -> line.getAmount().add(line.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            paid = paid.add(paidOnInvoice.multiply(share).divide(invoice.getTotalAmount(), 0, RoundingMode.HALF_UP));
        }
        return paid;
    }
}
