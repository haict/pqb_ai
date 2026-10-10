package com.company.erp.system.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.RecordStatus;
import com.company.erp.system.application.dto.ChangePasswordRequest;
import com.company.erp.system.application.dto.LoginRequest;
import com.company.erp.system.application.dto.MeResponse;
import com.company.erp.system.domain.Branch;
import com.company.erp.system.domain.LoginLog.LoginEvent;
import com.company.erp.system.domain.RefreshToken.RevokeReason;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.BranchRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đăng nhập, làm mới phiên, đăng xuất, đổi mật khẩu (BR-SYS-02, BR-SYS-20, BR-SYS-23, D-38). */
@Service
public class AuthService {

    private final AuthSessionTransactions sessions;
    private final CurrentUserLoader currentUserLoader;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginLogWriter loginLog;
    private final AuditLogService auditLog;
    private final LoginRateLimiter rateLimiter;
    private final Clock clock;

    public AuthService(AuthSessionTransactions sessions,
                       CurrentUserLoader currentUserLoader,
                       UserRepository userRepository,
                       BranchRepository branchRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       LoginLogWriter loginLog,
                       AuditLogService auditLog,
                       LoginRateLimiter rateLimiter,
                       Clock clock) {
        this.sessions = sessions;
        this.currentUserLoader = currentUserLoader;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.loginLog = loginLog;
        this.auditLog = auditLog;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Không đặt @Transactional: lần đăng nhập sai phải được commit trước khi trả lỗi. */
    public SessionTokens login(LoginRequest request, ClientInfo client) {
        rateLimiter.checkAllowed(client.ipAddress());
        return sessions.login(request.username().trim(), request.password(), client)
                .orElseThrow(() -> {
                    rateLimiter.recordFailure(client.ipAddress());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });
    }

    public SessionTokens refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        return sessions.refresh(rawRefreshToken, client)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
    }

    public void logout(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            sessions.logout(rawRefreshToken, client);
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        CurrentUser user = currentUserLoader.load(userId);
        return new MeResponse(user.id(), user.username(), user.fullName(), user.companyId(),
                user.defaultBranchId(), user.mustChangePassword(), user.dataScope().ownDataOnly(),
                user.permissions().stream().sorted().toList(),
                branchRepository.findAllById(user.dataScope().branchIds()).stream()
                        .filter(branch -> branch.getStatus() == RecordStatus.ACTIVE)
                        .sorted(Comparator.comparing(Branch::getCode))
                        .map(branch -> new MeResponse.BranchSummary(branch.getId(), branch.getCompanyId(),
                                branch.getCode(), branch.getName()))
                        .toList());
    }

    /**
     * Đổi mật khẩu của chính mình: thu hồi mọi phiên cũ (kể cả trên máy khác) và mở phiên mới cho máy hiện tại.
     */
    @Transactional
    public SessionTokens changePassword(CurrentUser actor, ChangePasswordRequest request, ClientInfo client) {
        User user = userRepository.findById(actor.id()).orElseThrow(BusinessException::notFound);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.invalidField("currentPassword", "Mật khẩu hiện tại không đúng.");
        }
        passwordPolicy.validate("newPassword", request.newPassword(), user.getUsername());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw BusinessException.invalidField("newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        Instant now = Instant.now(clock);
        user.changePassword(passwordEncoder.encode(request.newPassword()), now, false);
        sessions.revokeAllSessions(user.getId(), RevokeReason.PASSWORD_CHANGED);
        loginLog.write(LoginEvent.PASSWORD_CHANGED, user.getUsername(), user.getId(), client);
        auditLog.record(new AuditEntry(actor.id(), AuditActions.CHANGE_PASSWORD, AuditActions.USER, user.getId(),
                null, null, null, null, user.getCompanyId(), null));
        return sessions.startSession(user, client, now);
    }
}
