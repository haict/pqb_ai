package com.company.erp.system.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình bảo mật theo môi trường.
 *
 * @param corsAllowedOrigins  domain frontend được gọi API; rỗng là không cho phép cross-origin
 * @param publicApiDocs       mở Swagger UI / OpenAPI không cần đăng nhập (chỉ bật ở dev)
 */
@ConfigurationProperties(prefix = "erp.security")
public record SecurityProperties(List<String> corsAllowedOrigins, boolean publicApiDocs) {

    public SecurityProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }
}
