package com.company.erp.common.application;

import org.slf4j.MDC;

/**
 * Thông tin của request hiện tại được {@code RequestIdFilter} đặt vào MDC,
 * để tầng application đọc mà không phụ thuộc Servlet API.
 */
public final class RequestContext {

    public static final String REQUEST_ID = "requestId";
    public static final String CLIENT_IP = "clientIp";

    private RequestContext() {
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static String clientIp() {
        return MDC.get(CLIENT_IP);
    }
}
