package com.company.erp.system.application;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Nhận diện thương hiệu theo khách hàng (D-72). {@code directory}: thư mục chứa file logo trên máy chủ (ngoài thư mục
 * web); tên file đặt ở {@code sys_app_settings.branding.logo-file}. {@code logoMaxSize}: file lớn hơn bị bỏ qua.
 */
@ConfigurationProperties(prefix = "erp.branding")
public record BrandingProperties(Path directory, DataSize logoMaxSize) {
}
