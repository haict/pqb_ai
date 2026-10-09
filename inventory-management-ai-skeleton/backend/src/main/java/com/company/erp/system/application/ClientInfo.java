package com.company.erp.system.application;

/** Thông tin máy khách ghi vào nhật ký đăng nhập và phiên. */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 255;

    public ClientInfo {
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
    }
}
