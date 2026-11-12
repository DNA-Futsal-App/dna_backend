package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.identity.api.RegisterRequest;
import br.com.dnafutsal.backend.identity.application.RegistrationLifecycleHook;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Component
public class CoachAccessRegistrationHook implements RegistrationLifecycleHook {
    private static final Logger log = LoggerFactory.getLogger(CoachAccessRegistrationHook.class);
    private final CoachSharedAccessService access;
    private final CoachCategoryApplicationService applications;

    public CoachAccessRegistrationHook(CoachSharedAccessService access,
                                       CoachCategoryApplicationService applications) {
        this.access = access;
        this.applications = applications;
    }

    @Override
    public void afterRegistration(UserAccount user, RegisterRequest request) {
        String token = request.coachAccessToken();
        if (request.coachCategoryRegistration() != null) {
            if (token != null && !token.isBlank()) {
                throw br.com.dnafutsal.backend.common.Errors.badRequest(
                        "COACH_INVITE_MIXED", "Utilize somente um tipo de convite por cadastro.");
            }
            var coach = request.coachCategoryRegistration();
            applications.applyForNewUser(user, coach.token(), coach.teamId(),
                    coach.role(), coach.evidenceUrl());
        } else if (token != null && !token.isBlank()) {
            access.claimForUser(user.getId(), token);
        }
    }

    @Override
    public void afterEmailVerified(UserAccount user) {
        UUID userId = user.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    activateSafely(userId);
                }
            });
        } else {
            activateSafely(userId);
        }
    }

    private void activateSafely(UUID userId) {
        try {
            applications.activateVerifiedUser(userId);
        } catch (RuntimeException ex) {
            // The account is already verified. The next authenticated /me call
            // retries this activation; do not make email verification fail.
            log.warn("Coach category auto-activation delayed for account {}", userId, ex);
        }
    }
}
