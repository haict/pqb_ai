package com.company.erp.sales.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chuyển báo giá quá hạn hiệu lực sang EXPIRED (BR-SAL-05). Chạy lại nhiều lần không gây tác dụng phụ. */
@Component
class SalesQuoteExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SalesQuoteExpiryJob.class);

    private final SalesQuoteService quotes;

    SalesQuoteExpiryJob(SalesQuoteService quotes) {
        this.quotes = quotes;
    }

    @Scheduled(cron = "${erp.sales.quote-expiry-cron:0 5 0 * * *}")
    public void expire() {
        int expired = quotes.expireDue();
        if (expired > 0) {
            log.info("Đã chuyển {} báo giá sang hết hạn", expired);
        }
    }
}
