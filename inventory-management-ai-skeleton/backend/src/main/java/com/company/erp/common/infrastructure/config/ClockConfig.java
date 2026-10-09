package com.company.erp.common.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Đồng hồ dùng chung, lưu UTC (D-15). Test thay bằng {@code Clock.fixed} để kết quả xác định. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
