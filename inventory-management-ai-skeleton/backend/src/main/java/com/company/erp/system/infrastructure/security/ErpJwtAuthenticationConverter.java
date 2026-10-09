package com.company.erp.system.infrastructure.security;

import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.CurrentUserLoader;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Chuyển JWT hợp lệ thành {@link ErpAuthenticationToken}. Từ chối token của người dùng đã ngừng hoạt động
 * hoặc phát hành trước lần đổi/đặt lại mật khẩu gần nhất.
 */
@Component
class ErpJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final CurrentUserLoader currentUserLoader;

    ErpJwtAuthenticationConverter(CurrentUserLoader currentUserLoader) {
        this.currentUserLoader = currentUserLoader;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseUserId(jwt);
        Object credentialsVersion = jwt.getClaim(AccessTokenIssuer.CREDENTIALS_VERSION_CLAIM);
        if (!(credentialsVersion instanceof Number version)) {
            throw new InvalidBearerTokenException("Token không hợp lệ");
        }
        CurrentUser user = currentUserLoader.loadActive(userId, version.longValue())
                .orElseThrow(() -> new InvalidBearerTokenException("Token không còn hiệu lực"));
        List<SimpleGrantedAuthority> authorities = user.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new ErpAuthenticationToken(user, jwt, authorities);
    }

    private static Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new InvalidBearerTokenException("Token không hợp lệ");
        }
    }
}
