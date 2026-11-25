package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.api.RegisterRequest;
import br.com.dnafutsal.backend.identity.infrastructure.EmailVerificationTokenRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.application.MailTemplateFactory;
import br.com.dnafutsal.backend.sports.application.SportsPreferenceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationCoachInviteTest {
    @Mock UserAccountRepository users;
    @Mock EmailVerificationTokenRepository verificationTokens;
    @Mock PasswordEncoder passwords;
    @Mock IdentityNormalizer normalizer;
    @Mock TokenSupport tokens;
    @Mock MailTemplateFactory templates;
    @Mock MailOutboxService outbox;
    @Mock RegistrationLifecycleHook hooks;
    @Mock SportsPreferenceValidator preferences;

    private RegistrationService registration;

    @BeforeEach
    void setUp() {
        when(normalizer.email("coach@example.com")).thenReturn("coach@example.com");
        when(normalizer.phone("11999999999")).thenReturn("11999999999");
        registration = new RegistrationService(users, verificationTokens, passwords,
                normalizer, tokens, new SecurityProperties("test-secret".repeat(8),
                Duration.ofMinutes(10), Duration.ofDays(30), Duration.ofDays(1),
                Duration.ofHours(1)),
                new AppProperties("https://example.com", List.of("https://example.com"),
                        "America/Sao_Paulo"), templates, outbox, Clock.systemUTC(),
                List.of(hooks), preferences);
    }

    private RegisterRequest request(RegisterRequest.CoachCategoryRegistration invite) {
        return new RegisterRequest("Treinador", "coach@example.com", "11999999999",
                "correct password", null, null, null, null, null, null, null, invite);
    }

    @Test
    void categoryInvitedRegistrationDoesNotSendAnEmailVerification() {
        var input = request(new RegisterRequest.CoachCategoryRegistration(
                "token-para-categoria-com-primeiro-uso", "10", "Treinador", null));
        registration.register(input);
        verify(hooks).afterRegistration(any(), same(input));
        verifyNoInteractions(verificationTokens, outbox, templates);
    }

    @Test
    void ordinaryRegistrationStillEnqueuesEmailVerification() {
        registration.register(request(null));
        verify(verificationTokens).save(any());
        verify(hooks).afterRegistration(any(), any());
    }
}
