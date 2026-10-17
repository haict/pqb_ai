package com.company.erp.system.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình bản quyền (D-59). {@code importLocation}: file license nạp tự động khi khởi động nếu mới hơn license đang
 * dùng (ví dụ {@code file:/opt/erp/license.lic}); trống thì chỉ nạp qua màn hình. {@code refreshInterval}: chu kỳ đánh
 * giá lại (hết hạn, đồng hồ, công ty mới). Không có cấu hình nào tắt được việc kiểm tra license.
 */
@ConfigurationProperties(prefix = "erp.license")
public record LicenseProperties(String importLocation, Duration refreshInterval) {
}
