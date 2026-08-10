package br.com.dnafutsal.backend.identity.security;

import br.com.dnafutsal.backend.identity.application.UserSecurityState;
import br.com.dnafutsal.backend.identity.application.UserSecurityStateService;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID = new OAuth2Error("invalid_token",
            "The token has been revoked", null);

    private final UserSecurityStateService states;

    public TokenVersionValidator(UserSecurityStateService states) {
        this.states = states;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            UUID userId = UUID.fromString(token.getSubject());
            Number tokenVersion = token.getClaim("ver");
            UserSecurityState state = states.find(userId);
            if (state == null || state.status() != UserStatus.ACTIVE || tokenVersion == null
                    || state.tokenVersion() != tokenVersion.longValue()) {
                return OAuth2TokenValidatorResult.failure(INVALID);
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
    }
}
