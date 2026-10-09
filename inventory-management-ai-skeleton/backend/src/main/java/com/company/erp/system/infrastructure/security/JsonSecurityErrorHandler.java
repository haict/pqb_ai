package com.company.erp.system.infrastructure.security;

import com.company.erp.common.api.ErrorResponse;
import com.company.erp.common.application.RequestContext;
import com.company.erp.common.domain.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Trả 401/403 theo định dạng lỗi thống nhất; không tiết lộ lý do chi tiết (security.md mục 7). */
@Component
class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Frontend dựa vào TOKEN_EXPIRED để tự làm mới access token rồi gửi lại request.
        String message = authException.getMessage();
        boolean expired = message != null && message.toLowerCase().contains("expired");
        write(response, expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(code.name(), code.defaultMessage(), List.of(), RequestContext.requestId());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
