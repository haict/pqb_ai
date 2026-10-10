package com.company.erp.common.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ngày theo lịch nghiệp vụ Việt Nam (D-15). Ngày chứng từ, ngày hạch toán, chu kỳ số chứng từ dùng ngày này,
 * không dùng ngày UTC (sau 17:00 UTC đã là ngày hôm sau ở UTC+7).
 */
@Component
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, @Value("${erp.calendar.time-zone}") ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }
}
