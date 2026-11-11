package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.domain.PasswordResetToken;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.PasswordResetTokenRepository;
import br.com.dnafutsal.backend.identity.infrastructure.RefreshSessionRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.application.MailTemplateFactory;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminManualPasswordResetTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final IdentityNormalizer normalizer = mock(IdentityNormalizer.class);
    private final TokenSupport tokenSupport = mock(TokenSupport.class);
    private final MailTemplateFactory templates = mock(MailTemplateFactory.class);
    private final MailOutboxService outbox = mock(MailOutboxService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T18:00:00Z"), ZoneId.of("UTC"));
    private final UserAccount user = mock(UserAccount.class);


    private final SecurityProperties security = new SecurityProperties(
            "test-only-secret-01234567890123456789012345678901234567890123456789",
            Duration.ofMinutes(15),
            Duration.ofDays(30),
            Duration.ofHours(24),
            Duration.ofMinutes(25)
    );

    private final AppProperties app = new AppProperties(
            "http://localhost:3000",
            java.util.List.of("http://localhost:3000"),
            "America/Sao_Paulo"
    );


    private PasswordResetService service() {
        return new PasswordResetService(users, tokens, mock(RefreshSessionRepository.class), normalizer,
                tokenSupport, mock(PasswordEncoder.class), security, app, templates, outbox, clock);
    }

    private void activeUser(long issuedToday) {
        when(normalizer.email("Pessoa@Exemplo.com")).thenReturn("pessoa@exemplo.com");
        when(users.findByEmailForUpdate("pessoa@exemplo.com")).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getId()).thenReturn(UUID.fromString("0dcafc16-503d-42b5-aed9-6f54131a7677"));
        when(tokens.countByUserIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(issuedToday);
    }

    @Test
    void generatesOnlyManualLinkAndNeverEnqueuesEmail() {
        activeUser(0);
        when(tokenSupport.generate()).thenReturn("secure-token");
        when(tokenSupport.hash("secure-token")).thenReturn("somehash");
        when(templates.passwordResetLink("secure-token"))
                .thenReturn("https://dna.test/redefinir-senha?token=secure-token");

        var result = service().generateForAdministrator("Pessoa@Exemplo.com");

        assertEquals("https://dna.test/redefinir-senha?token=secure-token", result.link());
        assertEquals(Instant.parse("2026-11-06T18:25:00Z"), result.expiresAt());
        verify(tokens).save(any(PasswordResetToken.class));
        verify(outbox, never()).enqueue(any(MailMessage.class));
    }

    @Test
    void refusesWhenDailyLimitIsReachedWithoutCreatingTokenOrEmail() {
        activeUser(3);
        assertThrows(RuntimeException.class,
                () -> service().generateForAdministrator("Pessoa@Exemplo.com"));
        verify(tokens, never()).save(any(PasswordResetToken.class));
        verify(outbox, never()).enqueue(any(MailMessage.class));
    }
}
