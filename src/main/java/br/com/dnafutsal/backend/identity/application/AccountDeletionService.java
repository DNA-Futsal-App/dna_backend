package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.identity.api.AccountDeletionResponse;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountDeletionService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final IdentityNormalizer normalizer;
    private final AccountDeletionAttemptService attempts;
    private final MailOutboxService mail;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AppProperties properties;
    private final String dummyHash;

    public AccountDeletionService(UserAccountRepository users, PasswordEncoder passwords,
                                  IdentityNormalizer normalizer, AccountDeletionAttemptService attempts,
                                  MailOutboxService mail, JdbcTemplate jdbc, Clock clock, AppProperties properties) {
        this.users = users;
        this.passwords = passwords;
        this.normalizer = normalizer;
        this.attempts = attempts;
        this.mail = mail;
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AccountDeletionResponse request(UUID userId, String password) {
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(this::invalidCredentials);
        verifyPassword(user, password, userId.toString());
        if (user.getStatus() == UserStatus.PENDING_DELETION) {
            return response(user);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw Errors.conflict("ACCOUNT_UNAVAILABLE", "A conta não está disponível.");
        }
        user.scheduleDeletion(clock.instant());
        invalidateTokens(userId);
        String deadline = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm z", Locale.forLanguageTag("pt-BR"))
                .withZone(properties.zoneId()).format(user.getDeletionScheduledFor());
        mail.enqueue(new MailMessage(user.getEmail(), "Exclusão da conta agendada — DNA Futsal",
                "<p>Recebemos sua solicitação de exclusão de conta.</p>"
                        + "<p>Sua conta será excluída em 10 dias, em " + deadline + ".</p>"
                        + "<p>Seu acesso foi bloqueado. Todas as inscrições e mídias serão excluídas; "
                        + "os votos já registrados serão preservados.</p>"
                        + "<p>Para cancelar antes dessa data, faça login novamente "
                        + "no aplicativo com seu e-mail ou telefone e sua senha atual e confirme o cancelamento antes de entrar.</p>"));
        return response(user);
    }

    @Transactional
    public void cancel(String login, String password) {
        Optional<UserAccount> found = findByLogin(login);
        UserAccount user = found.orElse(null);
        String identifier = user == null ? login.trim().toLowerCase(Locale.ROOT) : user.getId().toString();
        verifyPassword(user, password, identifier);
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        if (user.getStatus() != UserStatus.PENDING_DELETION
                || !clock.instant().isBefore(user.getDeletionScheduledFor())) {
            throw Errors.conflict("ACCOUNT_DELETION_NOT_CANCELLABLE",
                    "O prazo de cancelamento terminou ou a conta não possui exclusão agendada.");
        }
        user.cancelDeletion();
        invalidateTokens(user.getId());
        jdbc.update("""
                DELETE FROM mail_outbox WHERE recipient = ? AND subject = ? AND status IN ('PENDING', 'RETRY')
                """, user.getEmail(), "Exclusão da conta agendada — DNA Futsal");
    }

    private void verifyPassword(UserAccount user, String password, String identifier) {
        attempts.reserve(identifier);
        String hash = user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash();
        if (!passwords.matches(password, hash) || user == null || user.getPasswordHash() == null) {
            throw invalidCredentials();
        }
        attempts.succeeded(identifier);
    }

    private RuntimeException invalidCredentials() {
        return Errors.unauthorized("INVALID_CREDENTIALS", "Credenciais inválidas.");
    }

    private Optional<UserAccount> findByLogin(String login) {
        if (login.contains("@")) {
            return users.findByEmailForUpdate(normalizer.email(login));
        }
        try {
            return users.findByPhoneForUpdate(normalizer.phone(login));
        } catch (br.com.dnafutsal.backend.common.BusinessException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void invalidateTokens(UUID userId) {
        jdbc.update("DELETE FROM refresh_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM email_verification_tokens WHERE user_id = ?", userId);
    }

    private AccountDeletionResponse response(UserAccount user) {
        return new AccountDeletionResponse(user.getStatus().name(), user.getDeletionRequestedAt(),
                user.getDeletionScheduledFor(), "Sua conta será excluída em 10 dias. Enviaremos a confirmação por e-mail.");
    }
}
