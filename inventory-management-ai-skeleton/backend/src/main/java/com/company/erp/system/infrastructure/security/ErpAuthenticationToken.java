package com.company.erp.system.infrastructure.security;

import com.company.erp.system.application.CurrentUser;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authentication của request: principal là {@link CurrentUser}, authority là mã quyền. */
public class ErpAuthenticationToken extends AbstractAuthenticationToken {

    private final transient CurrentUser principal;
    private final transient Jwt token;

    public ErpAuthenticationToken(CurrentUser principal, Jwt token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public CurrentUser getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return principal.username();
    }
}
