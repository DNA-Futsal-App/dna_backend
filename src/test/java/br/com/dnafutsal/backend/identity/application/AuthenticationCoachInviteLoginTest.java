package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.awards.application.CoachCategoryApplicationService;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.api.LoginRequest;
import br.com.dnafutsal.backend.identity.api.RegisterRequest;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserRole;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.RefreshSessionRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationCoachInviteLoginTest {
    @Mock UserAccountRepository users;
    @Mock RefreshSessionRepository sessions;
    @Mock PasswordEncoder passwords;
    @Mock IdentityNormalizer normalizer;
    @Mock TokenSupport tokens;
    @Mock JwtTokenService jwt;
    @Mock LoginAttemptService attempts;
    @Mock RegistrationService registration;
    @Mock CoachCategoryApplicationService coachInvites;

    private AuthenticationService auth;
    private UserAccount user;
    private final String rawInvite = "token-para-categoria-com-primeiro-uso";

    @BeforeEach
    void setUp() {
        user = new UserAccount("Treinador", "coach@example.com", "11999999999",
                "stored-hash", null, null, null, null, null);
        when(normalizer.email("coach@example.com")).thenReturn("coach@example.com");
        when(users.findByEmailForUpdate("coach@example.com")).thenReturn(Optional.of(user));
        auth = new AuthenticationService(users, sessions, passwords, normalizer,
                tokens, jwt, new SecurityProperties("test-secret".repeat(8),
                Duration.ofMinutes(10), Duration.ofDays(30), Duration.ofDays(1),
                Duration.ofHours(1)), attempts,
                Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC),
                registration, coachInvites);
    }

    private LoginRequest inviteLogin(String password) {
        return new LoginRequest("coach@example.com", password,
                new RegisterRequest.CoachCategoryRegistration(
                        rawInvite, "10", "Treinador", null));
    }

    @Test
    void aValidPasswordRedeemsInvitationBeforeJwtAndDoesNotClaimEmailIsVerified() {
        user.requireEmailVerification();
        when(passwords.matches("correct", "stored-hash")).thenReturn(true);
        doAnswer(invocation -> {
            UserAccount managed = invocation.getArgument(0);
            managed.activateViaCoachInvite();
            managed.promoteToCoach();
            return null;
        }).when(coachInvites).applyForInvitedLogin(same(user), eq(rawInvite), eq("10"), eq("Treinador"));

        var response = auth.login(inviteLogin("correct"), new ClientContext("test", "127.0.0.1"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(UserRole.COACH);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(response.user().emailVerified()).isFalse();
        var order = inOrder(coachInvites, jwt);
        order.verify(coachInvites).applyForInvitedLogin(user, rawInvite, "10", "Treinador");
        order.verify(jwt).createAccessToken(user);
    }

    @Test
    void anIncorrectPasswordCannotClaimAnyInvitation() {
        when(passwords.matches("wrong", "stored-hash")).thenReturn(false);
        assertThatThrownBy(() -> auth.login(inviteLogin("wrong"),
                new ClientContext("test", "127.0.0.1")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(coachInvites, jwt, sessions);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }
}
