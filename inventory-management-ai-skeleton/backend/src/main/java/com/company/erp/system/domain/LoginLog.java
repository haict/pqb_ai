package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Nhật ký đăng nhập (BR-SYS-20); chỉ INSERT. */
@Entity
@Immutable
@Table(name = "sys_login_logs")
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 30)
    private LoginEvent event;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "request_id", length = 64)
    private String requestId;

    protected LoginLog() {
    }

    public LoginLog(Instant occurredAt, LoginEvent event, String username, Long userId, String ipAddress,
                    String userAgent, String requestId) {
        this.occurredAt = occurredAt;
        this.event = event;
        this.username = username;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestId = requestId;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LoginEvent getEvent() {
        return event;
    }

    public String getUsername() {
        return username;
    }

    public Long getUserId() {
        return userId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public enum LoginEvent {
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        ACCOUNT_LOCKED,
        LOGOUT,
        REFRESH_REUSE_DETECTED,
        PASSWORD_CHANGED
    }
}
