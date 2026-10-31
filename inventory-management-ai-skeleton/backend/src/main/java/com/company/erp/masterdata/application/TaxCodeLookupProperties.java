package com.company.erp.masterdata.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tra cứu thông tin doanh nghiệp theo mã số thuế khi thêm đối tác (D-66).
 *
 * @param enabled        bật / tắt (máy chủ không ra được Internet thì tắt)
 * @param baseUrl        địa chỉ dịch vụ; mã số thuế nối thêm vào cuối đường dẫn
 * @param clientId       {@code client-id} cấp khi đăng ký API Xinvoice; trống thì không gửi
 * @param apiKey         {@code api-key} đi cùng {@code client-id} (secret, không commit); trống thì không gửi
 * @param connectTimeout thời gian chờ kết nối
 * @param readTimeout    thời gian chờ phản hồi
 */
@ConfigurationProperties(prefix = "erp.tax-code-lookup")
public record TaxCodeLookupProperties(boolean enabled, URI baseUrl, String clientId, String apiKey,
                                      Duration connectTimeout, Duration readTimeout) {

    public TaxCodeLookupProperties {
        if (enabled && baseUrl == null) {
            throw new IllegalArgumentException("erp.tax-code-lookup.enabled=true cần erp.tax-code-lookup.base-url");
        }
    }
}
