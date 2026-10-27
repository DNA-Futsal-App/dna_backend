package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.domain.EmailVerificationToken;
import br.com.dnafutsal.backend.identity.domain.PasswordResetToken;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.*;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.application.MailTemplateFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AccountDeletionRecoveryGuardTest {

    private final Instant now = Instant.parse("2026-10-12T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final TokenSupport tokens = new TokenSupport();
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final MailOutboxService mail = mock(MailOutboxService.class);
    private final AppProperties app = new AppProperties("http://localhost", List.of("http://localhost"), "UTC");
    private final SecurityProperties security = new SecurityProperties("a".repeat(64), Duration.ofMinutes(15),
            Duration.ofDays(30), Duration.ofHours(24), Duration.ofMinutes(30));

    @Test
    void oldVerificationCannotReactivatePendingDeletion() {
        UserAccount user = pendingAccount();
        var verification = mock(EmailVerificationTokenRepository.class);
        when(verification.findByTokenHash(anyString())).thenReturn(Optional.of(
                new EmailVerificationToken(user.getId(), tokens.hash("old"), now.plusSeconds(60), now)));
        var service = new RegistrationService(users, verification, passwords, mock(IdentityNormalizer.class),
                tokens, security, app,
                mock(MailTemplateFactory.class), mail, clock, List.of(), null);
        assertThatThrownBy(() -> service.verifyEmail("old")).isInstanceOf(BusinessException.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_DELETION);
        verifyNoInteractions(mail);
    }

    @Test
    void oldPasswordResetCannotChangePendingDeletionCredentials() {
        UserAccount user = pendingAccount();
        var resets = mock(PasswordResetTokenRepository.class);
        when(resets.findByTokenHash(anyString())).thenReturn(Optional.of(
                new PasswordResetToken(user.getId(), tokens.hash("old"), now.plusSeconds(60), now)));
        var service = new PasswordResetService(users, resets, mock(RefreshSessionRepository.class),
                mock(IdentityNormalizer.class), tokens, passwords, security,
                app, mock(MailTemplateFactory.class), mail, clock);
        assertThatThrownBy(() -> service.confirm("old", "new-password")).isInstanceOf(BusinessException.class);
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_DELETION);
        verifyNoInteractions(passwords, mail);
    }

    @Test
    void pendingDeletionSuspendsVideoWorkWithoutRemovingSourceFiles() {
        UserAccount user = pendingAccount();
        var registration = mock(br.com.dnafutsal.backend.awards.domain.AwardRegistration.class);
        var registrations = mock(br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository.class);
        var entries = mock(br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository.class);
        var storage = mock(br.com.dnafutsal.backend.awards.application.OracleAwardVideoStorage.class);
        var video = mock(br.com.dnafutsal.backend.awards.application.AwardVideoProcessor.class);
        var finalizer = mock(br.com.dnafutsal.backend.awards.application.AwardVideoProcessingFinalizer.class);
        var tracker = mock(AccountMediaTracker.class);
        var registrationId = java.util.UUID.randomUUID();
        when(registrations.findById(registrationId)).thenReturn(Optional.of(registration));
        when(registration.getRepresentativeUserId()).thenReturn(user.getId());
        var processing = new br.com.dnafutsal.backend.awards.application.AwardVideoProcessingService(
                entries, registrations, mock(br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository.class),
                storage, video, finalizer, users, tracker);
        processing.processAsync(registrationId, java.util.UUID.randomUUID(), "pending-object");
        verifyNoInteractions(entries, storage, video, finalizer, tracker);
    }

    private UserAccount pendingAccount() {
        UserAccount user = new UserAccount("Pessoa", "person@example.com", "+5511999999999", "hash",
                null, null, null, null, null);
        user.activate(now);
        user.scheduleDeletion(now);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        return user;
    }
}
