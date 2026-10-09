package com.company.erp.system.application;

import com.company.erp.system.domain.LoginLog.LoginEvent;
import com.company.erp.system.domain.RefreshToken;
import com.company.erp.system.domain.RefreshToken.RevokeReason;
import com.company.erp.system.domain.User;
import com.company.erp.system.infrastructure.persistence.RefreshTokenRepository;
import com.company.erp.system.infrastructure.persistence.UserRepository;
import com.company.erp.system.infrastructure.security.AccessTokenIssuer;
import com.company.erp.system.infrastructure.security.AccessTokenIssuer.IssuedAccessToken;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các transaction của đăng nhập và làm mới phiên. Thất bại được trả về dưới dạng {@link Optional#empty()}
 * thay vì ném exception, để số lần đăng nhập sai, việc khóa tài khoản, thu hồi phiên và nhật ký
 * vẫn được commit; {@link AuthService} mới ném lỗi cho client.
 */
@Component
class AuthSessionTransactions {

    /** Băm giả để thời gian xử lý tên đăng nhập không tồn tại tương đương mật khẩu sai (chống dò tài khoản). */
    private static final String DUMMY_HASH = "{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenCodec tokenCodec;
    private final LoginLogWriter loginLog;
    private final AuthProperties properties;
    private final Clock clock;

    AuthSessionTransactions(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordEncoder passwordEncoder,
                            AccessTokenIssuer accessTokenIssuer,
                            RefreshTokenCodec tokenCodec,
                            LoginLogWriter loginLog,
                            AuthProperties properties,
                            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenCodec = tokenCodec;
        this.loginLog = loginLog;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<SessionTokens> login(String username, String password, ClientInfo client) {
        Instant now = Instant.now(clock);
        Optional<User> found = userRepository.findByUsername(username);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, DUMMY_HASH);
            loginLog.write(LoginEvent.LOGIN_FAILED, username, null, client);
            return Optional.empty();
        }
        User user = found.get();
        if (!user.isActive() || user.isLockedAt(now)) {
            loginLog.write(LoginEvent.LOGIN_FAILED, username, user.getId(), client);
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            AuthProperties.Lockout lockout = properties.lockout();
            User.FailedLoginState state = user.afterFailedLogin(now, lockout.maxFailedAttempts(),
                    lockout.lockDuration());
            userRepository.recordFailedLogin(user.getId(), state.failedLoginCount(), state.lockedUntil());
            loginLog.write(state.lockedNow() ? LoginEvent.ACCOUNT_LOCKED : LoginEvent.LOGIN_FAILED, username,
                    user.getId(), client);
            return Optional.empty();
        }
        userRepository.recordSuccessfulLogin(user.getId(), now);
        loginLog.write(LoginEvent.LOGIN_SUCCESS, username, user.getId(), client);
        return Optional.of(startSession(user, client, now));
    }

    /**
     * Xoay vòng refresh token. Token đã dùng mà bị gửi lại là dấu hiệu bị đánh cắp: thu hồi toàn bộ phiên.
     */
    @Transactional
    public Optional<SessionTokens> refresh(String rawRefreshToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        Optional<RefreshToken> found = refreshTokenRepository.findForUpdateByTokenHash(tokenCodec.hash(rawRefreshToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        if (token.isReplayed()) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, RevokeReason.REUSE_DETECTED.name());
            loginLog.write(LoginEvent.REFRESH_REUSE_DETECTED, null, token.getUserId(), client);
            return Optional.empty();
        }
        if (!token.isUsableAt(now)) {
            return Optional.empty();
        }
        User user = userRepository.findById(token.getUserId()).filter(User::isActive).orElse(null);
        if (user == null) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, RevokeReason.USER_DEACTIVATED.name());
            return Optional.empty();
        }
        token.markUsed(now);
        return Optional.of(issueTokens(user, token.getFamilyId(), token.getSessionExpiresAt(), client, now));
    }

    @Transactional
    public void logout(String rawRefreshToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        refreshTokenRepository.findForUpdateByTokenHash(tokenCodec.hash(rawRefreshToken)).ifPresent(token -> {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, RevokeReason.LOGOUT.name());
            loginLog.write(LoginEvent.LOGOUT, null, token.getUserId(), client);
        });
    }

    /** Thu hồi mọi phiên của người dùng (đổi/đặt lại mật khẩu, ngừng hoạt động). Gọi trong transaction có sẵn. */
    void revokeAllSessions(Long userId, RevokeReason reason) {
        refreshTokenRepository.revokeAllOfUser(userId, Instant.now(clock), reason.name());
    }

    SessionTokens startSession(User user, ClientInfo client, Instant now) {
        Instant sessionExpiresAt = now.plus(properties.sessionMaxDuration());
        return issueTokens(user, UUID.randomUUID().toString(), sessionExpiresAt, client, now);
    }

    private SessionTokens issueTokens(User user, String familyId, Instant sessionExpiresAt, ClientInfo client,
                                      Instant now) {
        String rawToken = tokenCodec.generate();
        Instant idleExpiresAt = earliest(now.plus(properties.sessionIdleTimeout()), sessionExpiresAt);
        refreshTokenRepository.save(new RefreshToken(user.getId(), familyId, tokenCodec.hash(rawToken), now,
                sessionExpiresAt, idleExpiresAt, client.ipAddress(), client.userAgent()));
        IssuedAccessToken accessToken = accessTokenIssuer.issue(user.getId(), user.getUsername(),
                user.credentialsVersion(), now);
        return new SessionTokens(user.getId(), accessToken.value(), accessToken.expiresAt(), rawToken,
                sessionExpiresAt);
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
