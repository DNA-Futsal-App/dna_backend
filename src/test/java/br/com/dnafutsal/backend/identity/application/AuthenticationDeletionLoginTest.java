package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.api.LoginRequest;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.RefreshSessionRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationDeletionLoginTest {
    private final Instant now = Instant.parse("2026-10-12T15:00:00Z");
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final IdentityNormalizer normalizer = mock(IdentityNormalizer.class);
    private final JwtTokenService jwt = mock(JwtTokenService.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final UserAccount user = mock(UserAccount.class);
    private final RegistrationService registrationService = mock(RegistrationService.class);
    private AuthenticationService service;



    @BeforeEach
    void setup() {
        when(normalizer.email("person@example.com")).thenReturn("person@example.com");
        when(users.findByEmailForUpdate("person@example.com")).thenReturn(Optional.of(user));
        when(user.getPasswordHash()).thenReturn("hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        service = new AuthenticationService(users, sessions, passwords, normalizer,
                mock(TokenSupport.class), jwt, new SecurityProperties("test-secret".repeat(8), java.time.Duration.ofMinutes(10), java.time.Duration.ofDays(30), java.time.Duration.ofDays(1), java.time.Duration.ofHours(1)), attempts,
                Clock.fixed(now, ZoneOffset.UTC), registrationService);
    }

    private void expectCode(String password, String code) {
        assertThatThrownBy(() -> service.login(new LoginRequest("person@example.com", password),
                new ClientContext("test", "127.0.0.1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
        verifyNoInteractions(sessions, jwt);
        verify(user, never()).cancelDeletion();
    }

    @Test
    void validPasswordBeforeDeadlineRequiresConsentWithoutCancellingOrIssuingTokens() {
        when(user.getStatus()).thenReturn(UserStatus.PENDING_DELETION);
        when(user.getDeletionScheduledFor()).thenReturn(now.plusSeconds(1));
        expectCode("correct", "ACCOUNT_PENDING_DELETION");
    }

    @Test
    void exactDeadlineAndExpiredDeletionCannotOfferCancellation() {
        when(user.getStatus()).thenReturn(UserStatus.PENDING_DELETION);
        when(user.getDeletionScheduledFor()).thenReturn(now);
        expectCode("correct", "ACCOUNT_UNAVAILABLE");
        when(user.getDeletionScheduledFor()).thenReturn(now.minusSeconds(1));
        expectCode("correct", "ACCOUNT_UNAVAILABLE");
    }

    @Test
    void wrongPasswordDoesNotDisclosePendingDeletion() {
        when(user.getStatus()).thenReturn(UserStatus.PENDING_DELETION);
        when(user.getDeletionScheduledFor()).thenReturn(now.plusSeconds(1));
        expectCode("wrong", "INVALID_CREDENTIALS");
        verify(user, never()).getStatus();
    }

    @Test
    void otherUnavailableStatesDoNotOfferCancellation() {
        when(user.getStatus()).thenReturn(UserStatus.DELETING);
        expectCode("correct", "ACCOUNT_UNAVAILABLE");
        when(user.getStatus()).thenReturn(UserStatus.DELETED);
        expectCode("correct", "ACCOUNT_UNAVAILABLE");
    }
}