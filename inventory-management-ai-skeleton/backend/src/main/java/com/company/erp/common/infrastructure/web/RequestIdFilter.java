package com.company.erp.common.infrastructure.web;

import com.company.erp.common.application.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gắn request ID cho mỗi request (header {@code X-Request-Id}, docs/04 mục 1.1) vào MDC và response,
 * để log, audit và phản hồi lỗi truy vết được cùng một request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    // Chỉ nhận request ID an toàn để ghi log; giá trị khác bị thay bằng UUID mới (chống log injection).
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(RequestContext.REQUEST_ID, requestId);
        MDC.put(RequestContext.CLIENT_IP, request.getRemoteAddr());
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID);
            MDC.remove(RequestContext.CLIENT_IP);
        }
    }

    static String resolveRequestId(String headerValue) {
        if (headerValue != null && SAFE_REQUEST_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }
}
