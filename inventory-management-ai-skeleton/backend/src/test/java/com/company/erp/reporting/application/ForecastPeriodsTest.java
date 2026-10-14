package com.company.erp.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.ForecastPeriods.Granularity;
import com.company.erp.reporting.application.ForecastPeriods.Period;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForecastPeriodsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    @Test
    @DisplayName("BR-RPT-30: kỳ tuần là khối 7 ngày từ hôm nay; kỳ tháng đầu tiên đến cuối tháng")
    void periods() {
        assertThat(ForecastPeriods.of(Granularity.WEEK, TODAY, 2)).containsExactly(
                new Period(TODAY, LocalDate.of(2026, 10, 20)),
                new Period(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 27)));
        assertThat(ForecastPeriods.of(Granularity.MONTH, TODAY, 3)).containsExactly(
                new Period(TODAY, LocalDate.of(2026, 10, 31)),
                new Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                new Period(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)));
        assertThat(ForecastPeriods.of(Granularity.DAY, TODAY, 2)).extracting(Period::to)
                .containsExactly(TODAY, TODAY.plusDays(1));
        assertThat(ForecastPeriods.granularity(null)).isEqualTo(Granularity.WEEK);
    }

    @Test
    @DisplayName("BR-RPT-30: số kỳ ngoài giới hạn, đơn vị kỳ sai: lỗi trên đúng trường")
    void invalid() {
        assertThatThrownBy(() -> ForecastPeriods.of(Granularity.WEEK, TODAY, 0)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số kỳ");
        assertThatThrownBy(() -> ForecastPeriods.of(Granularity.MONTH, TODAY, 25))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ForecastPeriods.granularity("YEAR")).isInstanceOf(BusinessException.class);
    }
}
