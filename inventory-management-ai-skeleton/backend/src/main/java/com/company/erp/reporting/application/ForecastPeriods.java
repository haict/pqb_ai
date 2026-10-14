package com.company.erp.reporting.application;

import com.company.erp.common.domain.BusinessException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Kỳ của dòng tiền dự kiến tính từ hôm nay: theo ngày; theo tuần (khối 7 ngày bắt đầu hôm nay); theo tháng (kỳ đầu từ
 * hôm nay đến cuối tháng, sau đó tháng dương lịch).
 */
final class ForecastPeriods {

    enum Granularity {
        DAY(92), WEEK(53), MONTH(24);

        final int maxPeriods;

        Granularity(int maxPeriods) {
            this.maxPeriods = maxPeriods;
        }
    }

    record Period(LocalDate from, LocalDate to) {

        boolean contains(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }

    private ForecastPeriods() {
    }

    static Granularity granularity(String value) {
        if (value == null) {
            return Granularity.WEEK;
        }
        try {
            return Granularity.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.invalidField("granularity", "Chọn DAY, WEEK hoặc MONTH.");
        }
    }

    static List<Period> of(Granularity granularity, LocalDate today, int count) {
        if (count < 1 || count > granularity.maxPeriods) {
            throw BusinessException.invalidField("periods", "Số kỳ từ 1 đến " + granularity.maxPeriods + ".");
        }
        List<Period> periods = new ArrayList<>(count);
        LocalDate from = today;
        for (int i = 0; i < count; i++) {
            LocalDate to = switch (granularity) {
                case DAY -> from;
                case WEEK -> from.plusDays(6);
                case MONTH -> from.with(TemporalAdjusters.lastDayOfMonth());
            };
            periods.add(new Period(from, to));
            from = to.plusDays(1);
        }
        return periods;
    }
}
