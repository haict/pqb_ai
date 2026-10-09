package com.company.erp.system.application;

import com.company.erp.common.application.RequestContext;
import com.company.erp.system.domain.LoginLog;
import com.company.erp.system.domain.LoginLog.LoginEvent;
import com.company.erp.system.infrastructure.persistence.LoginLogRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Ghi nhật ký đăng nhập trong transaction hiện tại (BR-SYS-20). */
@Component
class LoginLogWriter {

    private final LoginLogRepository repository;
    private final Clock clock;

    LoginLogWriter(LoginLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    void write(LoginEvent event, String username, Long userId, ClientInfo client) {
        repository.save(new LoginLog(Instant.now(clock), event, truncate(username), userId,
                client.ipAddress(), client.userAgent(), RequestContext.requestId()));
    }

    private static String truncate(String username) {
        if (username == null) {
            return null;
        }
        return username.length() > 50 ? username.substring(0, 50) : username;
    }
}
