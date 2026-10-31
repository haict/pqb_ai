package com.company.erp.masterdata.infrastructure.taxcode;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.TaxCodeDirectory;
import com.company.erp.masterdata.application.TaxCodeLookupProperties;
import com.company.erp.masterdata.application.dto.TaxCodeLookupResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Tra cứu mã số thuế qua API Xinvoice ({@code GET <base-url>/<mst>}, header {@code client-id}, {@code api-key} khi
 * có cấu hình; D-66). Thành công: HTTP 200 kèm {@code taxID, name, address, status}; không tìm thấy: HTTP 404.
 */
@Component
public class XinvoiceTaxCodeClient implements TaxCodeDirectory {

    private static final Logger log = LoggerFactory.getLogger(XinvoiceTaxCodeClient.class);
    private static final String SOURCE = "Xinvoice (dữ liệu Cục Thuế)";

    private final TaxCodeLookupProperties properties;
    private final RestClient client;

    public XinvoiceTaxCodeClient(TaxCodeLookupProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
        factory.setReadTimeout(properties.readTimeout());
        this.client = builder.clone().requestFactory(factory).build();
    }

    @Override
    public TaxCodeLookupResponse lookup(String taxCode) {
        URI uri = UriComponentsBuilder.fromUri(properties.baseUrl()).pathSegment(taxCode).build().encode().toUri();
        JsonNode body;
        try {
            body = client.get().uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (StringUtils.hasText(properties.clientId())) {
                            headers.set("client-id", properties.clientId());
                        }
                        if (StringUtils.hasText(properties.apiKey())) {
                            headers.set("api-key", properties.apiKey());
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toBusinessException(response.getStatusCode().value());
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException ex) {
            throw connectionFailure(ex);
        } catch (RestClientException ex) {
            log.warn("Tra cứu mã số thuế trả dữ liệu không đọc được: {}", ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.TAX_CODE_LOOKUP_UNAVAILABLE);
        }
        if (body == null || !body.isObject() || text(body, "name") == null) {
            log.warn("Tra cứu mã số thuế trả dữ liệu thiếu tên doanh nghiệp");
            throw new BusinessException(ErrorCode.TAX_CODE_LOOKUP_UNAVAILABLE);
        }
        String id = text(body, "taxID");
        return new TaxCodeLookupResponse(id == null ? taxCode : id, text(body, "name"), null, null,
                text(body, "address"), null, text(body, "status"), SOURCE);
    }

    private static BusinessException toBusinessException(int status) {
        return switch (status) {
            case 404 -> new BusinessException(ErrorCode.TAX_CODE_NOT_FOUND);
            case 429 -> {
                log.warn("Tra cứu mã số thuế bị giới hạn tần suất (HTTP 429)");
                yield new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "Tra cứu mã số thuế quá nhiều lần, vui lòng thử lại sau ít phút.");
            }
            default -> {
                // 401 / 403: client-id / api-key sai hoặc hết hạn; quản trị kiểm tra cấu hình erp.tax-code-lookup.
                log.warn("Tra cứu mã số thuế trả lỗi HTTP {}", status);
                yield new BusinessException(ErrorCode.TAX_CODE_LOOKUP_UNAVAILABLE);
            }
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private static BusinessException connectionFailure(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
            log.warn("Tra cứu mã số thuế quá thời gian chờ");
            return new BusinessException(ErrorCode.TAX_CODE_LOOKUP_TIMEOUT);
        }
        log.warn("Không kết nối được dịch vụ tra cứu mã số thuế: {}",
                cause == null ? ex.getClass().getSimpleName() : cause.getClass().getSimpleName());
        return new BusinessException(ErrorCode.TAX_CODE_LOOKUP_UNAVAILABLE);
    }
}
