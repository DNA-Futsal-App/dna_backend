package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountDeletionServiceTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final IdentityNormalizer normalizer = mock(IdentityNormalizer.class);
    private final AccountDeletionAttemptService attempts = mock(AccountDeletionAttemptService.class);
    private final MailOutboxService mail = mock(MailOutboxService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-12T15:00:00Z");
    private AccountDeletionService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        when(passwords.matches("correct", "hash")).thenReturn(true);
        user = new UserAccount("Responsável", "person@example.com", "+5511999999999", "hash",
                "athlete", 1L, "1", "1", "team");
        user.activate(now.minusSeconds(60));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(normalizer.email("person@example.com")).thenReturn("person@example.com");
        when(users.findByEmailForUpdate("person@example.com")).thenReturn(Optional.of(user));
        service = new AccountDeletionService(users, passwords, normalizer, attempts, mail, jdbc, clock,
                new AppProperties("http://localhost", List.of("http://localhost"), "America/Sao_Paulo"));
    }

    @Test
    void schedulesExactlyTenDaysAndInvalidatesAllTokens() {
        var response = service.request(user.getId(), "correct");
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_DELETION);
        assertThat(response.scheduledFor()).isEqualTo(now.plus(Duration.ofDays(10)));
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(jdbc).update("DELETE FROM refresh_sessions WHERE user_id = ?", user.getId());
        verify(jdbc).update("DELETE FROM password_reset_tokens WHERE user_id = ?", user.getId());
        verify(jdbc).update("DELETE FROM email_verification_tokens WHERE user_id = ?", user.getId());
        var message = ArgumentCaptor.forClass(MailMessage.class);
        verify(mail).enqueue(message.capture());
        assertThat(message.getValue().recipient()).isEqualTo("person@example.com");
        assertThat(message.getValue().htmlBody()).contains("22/10/2026", "12:00", "10 dias", "cancelar");
    }

    @Test
    void wrongPasswordDoesNotScheduleOrSendMail() {
        assertThatThrownBy(() -> service.request(user.getId(), "wrong"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(attempts).reserve(user.getId().toString());
        verifyNoInteractions(mail, jdbc);
    }

    @Test
    void repeatedRequestKeepsDeadlineAndDoesNotDuplicateMail() {
        var first = service.request(user.getId(), "correct");
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        var second = service.request(user.getId(), "correct");
        assertThat(second.scheduledFor()).isEqualTo(first.scheduledFor());
        verify(mail, times(1)).enqueue(any());
    }

    @Test
    void cancellationBeforeDeadlineRestoresAccountButNotOldTokens() {
        service.request(user.getId(), "correct");
        when(clock.instant()).thenReturn(now.plus(Duration.ofDays(10)).minusNanos(1));
        service.cancel("person@example.com", "correct");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getDeletionScheduledFor()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(2);
        service.cancel("person@example.com", "correct");
        assertThat(user.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void cancellationAtDeadlineIsRejected() {
        service.request(user.getId(), "correct");
        when(clock.instant()).thenReturn(now.plus(Duration.ofDays(10)));
        assertThatThrownBy(() -> service.cancel("person@example.com", "correct"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_DELETION);
    }

    @Test
    void unknownAccountUsesDummyPasswordAndDoesNotRevealState() {
        when(users.findByEmailForUpdate("person@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel("person@example.com", "correct"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(passwords).matches("correct", "dummy-hash");
    }

    @Test
    void rateLimitPreventsPasswordComparisonAndMutation() {
        doThrow(br.com.dnafutsal.backend.common.Errors.tooManyRequests("LIMIT", "Limite"))
                .when(attempts).reserve(user.getId().toString());
        assertThatThrownBy(() -> service.request(user.getId(), "correct"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(passwords, never()).matches(anyString(), anyString());
        verifyNoInteractions(mail, jdbc);
    }
}
