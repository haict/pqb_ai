package com.company.erp.system.infrastructure.security;

import com.company.erp.system.application.AuthProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Phát hành access token ngắn hạn. Token chỉ chứa định danh; quyền và phạm vi được nạp từ database
 * ở mỗi request để thay đổi quyền, khóa tài khoản có hiệu lực ngay.
 */
@Component
public class AccessTokenIssuer {

    /** Claim chứa phiên bản thông tin đăng nhập (mốc đổi mật khẩu, mili-giây). */
    public static final String CREDENTIALS_VERSION_CLAIM = "cv";

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public AccessTokenIssuer(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedAccessToken issue(Long userId, String username, long credentialsVersion, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(userId.toString())
                .claim("username", username)
                .claim(CREDENTIALS_VERSION_CLAIM, credentialsVersion)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
