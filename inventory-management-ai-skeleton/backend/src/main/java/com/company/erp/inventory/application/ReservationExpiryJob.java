package com.company.erp.inventory.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nhả giữ chỗ quá thời hạn giữ (BR-SAL-13, D-37). Chạy lại nhiều lần không gây tác dụng phụ. */
@Component
class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final InventoryReservationService reservations;

    ReservationExpiryJob(InventoryReservationService reservations) {
        this.reservations = reservations;
    }

    @Scheduled(cron = "${erp.inventory.reservation-expiry-cron:0 */15 * * * *}")
    public void releaseExpired() {
        int released = reservations.releaseExpired();
        if (released > 0) {
            log.info("Đã nhả {} giữ chỗ quá hạn", released);
        }
    }
}
