package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int DAILY_LIMIT = 3;

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshSessionRepository refreshSessions;
    private final IdentityNormalizer normalizer;
    private final TokenSupport tokenSupport;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final AppProperties appProperties;
    private final MailTemplateFactory mailTemplates;
    private final MailOutboxService mailOutbox;
    private final Clock clock;

    public PasswordResetService(UserAccountRepository users,
                                PasswordResetTokenRepository resetTokens,
                                RefreshSessionRepository refreshSessions,
                                IdentityNormalizer normalizer,
                                TokenSupport tokenSupport,
                                PasswordEncoder passwordEncoder,
                                SecurityProperties securityProperties,
                                AppProperties appProperties,
                                MailTemplateFactory mailTemplates,
                                MailOutboxService mailOutbox,
                                Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.refreshSessions = refreshSessions;
        this.normalizer = normalizer;
        this.tokenSupport = tokenSupport;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.appProperties = appProperties;
        this.mailTemplates = mailTemplates;
        this.mailOutbox = mailOutbox;
        this.clock = clock;
    }

    @Transactional
    public void request(String login) {
        Optional<UserAccount> findUser = Optional.of(findByLogin(login).orElseThrow(() ->
                Errors.notFound("NOT_FOUND", "E-mail não cadastrado, verifique o endereço digitado e tente novamente")
        ));
        findUser.filter(user -> user.getStatus() == UserStatus.ACTIVE).ifPresent(user -> {
            Instant now = clock.instant();
            Instant startOfDay = now.atZone(appProperties.zoneId()).toLocalDate()
                    .atStartOfDay(appProperties.zoneId()).toInstant();
            if (resetTokens.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay) >= DAILY_LIMIT) {
                return;
            }
            String rawToken = tokenSupport.generate();
            resetTokens.save(new PasswordResetToken(user.getId(), tokenSupport.hash(rawToken),
                    now.plus(securityProperties.passwordResetTtl()), now));
            mailOutbox.enqueue(mailTemplates.passwordReset(user.getName(), user.getEmail(), rawToken));
        });
    }

    /**
     * Gera um link para compartilhamento manual pelo administrador. Nunca enfileira e-mail.
     * O token segue as mesmas regras de prazo, limite diario e consumo do fluxo publico.
     */
    @Transactional
    public AdminResetLink generateForAdministrator(String email) {
        UserAccount user = users.findByEmailForUpdate(normalizer.email(email))
                .orElseThrow(() -> Errors.notFound("PASSWORD_RESET_USER_NOT_FOUND",
                        "Nao encontramos uma conta com este e-mail."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw Errors.conflict("PASSWORD_RESET_ACCOUNT_INACTIVE",
                    "A redefinicao nao esta disponivel para esta conta.");
        }

        Instant now = clock.instant();
        Instant startOfDay = now.atZone(appProperties.zoneId()).toLocalDate()
                .atStartOfDay(appProperties.zoneId()).toInstant();
        if (resetTokens.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay) >= DAILY_LIMIT) {
            throw Errors.conflict("PASSWORD_RESET_DAILY_LIMIT",
                    "O limite de tres links de redefinicao por dia foi atingido para esta conta.");
        }

        String rawToken = tokenSupport.generate();
        Instant expiresAt = now.plus(securityProperties.passwordResetTtl());
        resetTokens.save(new PasswordResetToken(user.getId(), tokenSupport.hash(rawToken), expiresAt, now));

        return new AdminResetLink(mailTemplates.passwordResetLink(rawToken), expiresAt, user.getId());
    }

    /** userId serve somente para auditoria; nao e exposto pela API. */
    public record AdminResetLink(String link, Instant expiresAt, java.util.UUID userId) { }

    @Transactional
    public java.util.UUID confirm(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(tokenSupport.hash(rawToken))
                .orElseThrow(() -> Errors.badRequest("INVALID_RESET_TOKEN", "O link de redefinição é inválido."));
        if (!token.isUsableAt(now)) {
            throw Errors.badRequest("EXPIRED_RESET_TOKEN", "O link de redefinição expirou ou já foi utilizado.");
        }
        UserAccount user = users.findByIdForUpdate(token.getUserId())
                .orElseThrow(() -> Errors.notFound("USER_NOT_FOUND", "Usuário não encontrado."));
        if (user.getStatus() != UserStatus.ACTIVE
                || resetTokens.findByTokenHash(tokenSupport.hash(rawToken)).isEmpty()) {
            throw Errors.badRequest("INVALID_RESET_TOKEN", "O link de redefinição é inválido.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        resetTokens.invalidateAllByUserId(user.getId(), now);
        refreshSessions.revokeAllByUserId(user.getId(), now);
        return user.getId();
    }

    private Optional<UserAccount> findByLogin(String login) {
        String value = login.trim();
        if (value.contains("@")) {
            return users.findByEmailForUpdate(normalizer.email(value));
        }
        try {
            return users.findByPhoneForUpdate(normalizer.phone(value));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
