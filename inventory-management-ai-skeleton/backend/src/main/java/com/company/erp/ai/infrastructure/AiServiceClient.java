package com.company.erp.ai.infrastructure;

import com.company.erp.ai.application.AiProperties;
import com.company.erp.ai.application.AiServiceGateway;
import com.company.erp.ai.application.AiServiceGateway.ChatReply;
import com.company.erp.ai.application.AiServiceGateway.ChatTurn;
import com.company.erp.ai.application.AiServiceGateway.Status;
import com.company.erp.ai.application.ExtractedDocument;
import com.company.erp.ai.domain.OcrDocumentType;
import com.company.erp.common.application.RequestContext;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gọi AI Service qua HTTP nội bộ (docs/14 mục 6.3): header {@code X-AI-Service-Key}, {@code X-Request-Id}. Chỉ ghi log
 * mã trạng thái và mã lỗi, không ghi nội dung file, kết quả nhận dạng, câu hỏi hay câu trả lời (BR-AI-10).
 */
@Component
public class AiServiceClient implements AiServiceGateway {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final String KEY_HEADER = "X-AI-Service-Key";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient ocrClient;
    private final RestClient chatClient;
    private final RestClient healthClient;

    public AiServiceClient(AiProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration connect = properties.service().connectTimeout();
        this.ocrClient = client(builder, connect, properties.ocr().timeout());
        this.chatClient = client(builder, connect, properties.chat().timeout());
        // Kiểm tra sức khỏe phải trả lời nhanh: dùng thời gian chờ ngắn riêng.
        this.healthClient = client(builder, connect, connect.multipliedBy(2));
    }

    private RestClient client(RestClient.Builder builder, Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        RestClient.Builder copy = builder.clone().requestFactory(factory);
        if (properties.service().baseUrl() != null) {
            copy.baseUrl(properties.service().baseUrl().toString());
        }
        return copy.build();
    }

    @Override
    public ExtractedDocument extract(byte[] file, OcrDocumentType documentType) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(file) {
            @Override
            public String getFilename() {
                return "document";
            }
        });
        parts.add("documentType", documentType.name());
        try {
            return ocrClient.post().uri("/internal/v1/ocr/extract")
                    .header(KEY_HEADER, properties.service().apiKey())
                    .header(REQUEST_ID_HEADER, requestId())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toBusinessException(response, AiServiceClient::ocrError);
                    })
                    .body(ExtractedDocument.class);
        } catch (ResourceAccessException ex) {
            throw connectionFailure(ex);
        } catch (RestClientException ex) {
            log.warn("AI Service trả dữ liệu không đọc được: {}", ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public ChatReply chat(List<ChatTurn> history, String question) {
        Map<String, Object> body = Map.of("question", question, "history", history);
        try {
            JsonNode reply = chatClient.post().uri("/internal/v1/chat")
                    .header(KEY_HEADER, properties.service().apiKey())
                    .header(REQUEST_ID_HEADER, requestId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toBusinessException(response, AiServiceClient::chatError);
                    })
                    .body(JsonNode.class);
            if (reply == null || !reply.path("answer").isTextual()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }
            List<String> citations = reply.path("citations").isArray()
                    ? StreamSupport.stream(reply.path("citations").spliterator(), false)
                            .map(JsonNode::asText).toList()
                    : List.of();
            return new ChatReply(reply.path("answer").asText(), citations, reply.path("grounded").asBoolean(false));
        } catch (ResourceAccessException ex) {
            throw connectionFailure(ex);
        } catch (RestClientException ex) {
            log.warn("AI Service trả dữ liệu không đọc được: {}", ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public Status status() {
        if (!properties.enabled()) {
            return Status.DOWN;
        }
        try {
            JsonNode health = healthClient.get().uri("/internal/v1/health")
                    .header(KEY_HEADER, properties.service().apiKey())
                    .retrieve().body(JsonNode.class);
            if (health == null) {
                return Status.DOWN;
            }
            return new Status(health.path("ocr").path("ready").asBoolean(false),
                    health.path("chat").path("ready").asBoolean(false));
        } catch (RestClientException ex) {
            log.debug("AI Service không phản hồi kiểm tra sức khỏe: {}", ex.getClass().getSimpleName());
            return Status.DOWN;
        }
    }

    private BusinessException toBusinessException(ClientHttpResponse response,
                                                  BiFunction<Integer, JsonNode, BusinessException> mapper)
            throws IOException {
        int status = response.getStatusCode().value();
        JsonNode body = readBody(response);
        log.warn("AI Service trả lỗi HTTP {} {}", status, body.path("code").asText(""));
        return mapper.apply(status, body);
    }

    /** Lỗi trợ lý: bận 429, quá giờ 504; còn lại coi như không khả dụng (câu hỏi đã kiểm tra độ dài ở Spring). */
    private static BusinessException chatError(int status, JsonNode body) {
        return switch (status) {
            case 429 -> new BusinessException(ErrorCode.AI_BUSY);
            case 504 -> new BusinessException(ErrorCode.AI_TIMEOUT);
            default -> new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        };
    }

    private static BusinessException ocrError(int status, JsonNode body) {
        String code = body.path("code").asText("");
        return switch (status) {
            case 400 -> new BusinessException(ErrorCode.OCR_FILE_INVALID, "VALIDATION_ERROR".equals(code)
                    ? ErrorCode.OCR_FILE_INVALID.defaultMessage()
                    : body.path("message").asText(ErrorCode.OCR_FILE_INVALID.defaultMessage()));
            case 413 -> new BusinessException(ErrorCode.OCR_FILE_TOO_LARGE);
            case 415 -> new BusinessException(ErrorCode.OCR_UNSUPPORTED_TYPE);
            case 422 -> new BusinessException(ErrorCode.OCR_NO_TEXT);
            case 429 -> new BusinessException(ErrorCode.AI_BUSY);
            default -> new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        };
    }

    private JsonNode readBody(ClientHttpResponse response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (IOException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static BusinessException connectionFailure(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
            log.warn("AI Service xử lý quá thời gian");
            return new BusinessException(ErrorCode.AI_TIMEOUT);
        }
        log.warn("Không kết nối được AI Service: {}", cause == null ? ex.getClass().getSimpleName()
                : cause.getClass().getSimpleName());
        return new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private static String requestId() {
        String requestId = RequestContext.requestId();
        return requestId == null ? "" : requestId;
    }
}
