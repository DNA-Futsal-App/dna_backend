package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserService {

    private final UserAccountRepository users;

    public CurrentUserService(UserAccountRepository users) {
        this.users = users;
    }

    public UUID userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw Errors.unauthorized("AUTHENTICATION_REQUIRED", "Faça login para continuar.");
        }
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                var user = users.findByIdForUpdate(userId).orElseThrow(() ->
                        Errors.unauthorized("ACCOUNT_UNAVAILABLE", "A conta não está disponível."));
                Number tokenVersion = jwt.getClaim("ver");
                if (user.getStatus() != UserStatus.ACTIVE
                        || tokenVersion == null || tokenVersion.longValue() != user.getTokenVersion()) {
                    throw Errors.unauthorized("ACCOUNT_UNAVAILABLE", "A conta não está disponível.");
                }
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw Errors.unauthorized("INVALID_ACCESS_TOKEN", "O token de acesso é inválido.");
        }
    }
}
