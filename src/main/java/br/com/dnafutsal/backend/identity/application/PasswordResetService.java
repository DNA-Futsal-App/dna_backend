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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
        findByLogin(login).filter(user -> user.getStatus() == UserStatus.ACTIVE).ifPresent(user -> {
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

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "user-profile", key = "#result"),
            @CacheEvict(cacheNames = "user-security", key = "#result")
    })
    public java.util.UUID confirm(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(tokenSupport.hash(rawToken))
                .orElseThrow(() -> Errors.badRequest("INVALID_RESET_TOKEN", "O link de redefinição é inválido."));
        if (!token.isUsableAt(now)) {
            throw Errors.badRequest("EXPIRED_RESET_TOKEN", "O link de redefinição expirou ou já foi utilizado.");
        }
        UserAccount user = users.findById(token.getUserId())
                .orElseThrow(() -> Errors.notFound("USER_NOT_FOUND", "Usuário não encontrado."));
        user.changePassword(passwordEncoder.encode(newPassword));
        resetTokens.invalidateAllByUserId(user.getId(), now);
        refreshSessions.revokeAllByUserId(user.getId(), now);
        return user.getId();
    }

    private Optional<UserAccount> findByLogin(String login) {
        String value = login.trim();
        if (value.contains("@")) {
            return users.findByEmail(normalizer.email(value));
        }
        try {
            return users.findByPhone(normalizer.phone(value));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
