package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.identity.api.RegisterRequest;
import br.com.dnafutsal.backend.identity.domain.EmailVerificationToken;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.EmailVerificationTokenRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.application.MailTemplateFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class RegistrationService {

    private final UserAccountRepository users;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNormalizer normalizer;
    private final TokenSupport tokenSupport;
    private final SecurityProperties securityProperties;
    private final AppProperties appProperties;
    private final MailTemplateFactory mailTemplates;
    private final MailOutboxService mailOutbox;
    private final Clock clock;

    public RegistrationService(UserAccountRepository users,
                               EmailVerificationTokenRepository verificationTokens,
                               PasswordEncoder passwordEncoder,
                               IdentityNormalizer normalizer,
                               TokenSupport tokenSupport,
                               SecurityProperties securityProperties,
                               AppProperties appProperties,
                               MailTemplateFactory mailTemplates,
                               MailOutboxService mailOutbox,
                               Clock clock) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.passwordEncoder = passwordEncoder;
        this.normalizer = normalizer;
        this.tokenSupport = tokenSupport;
        this.securityProperties = securityProperties;
        this.appProperties = appProperties;
        this.mailTemplates = mailTemplates;
        this.mailOutbox = mailOutbox;
        this.clock = clock;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizer.email(request.email());
        String phone = normalizer.phone(request.phone());
        if (users.existsByEmail(email)) {
            throw Errors.conflict("EMAIL_ALREADY_REGISTERED", "Este e-mail já está cadastrado.");
        }
        if (users.existsByPhone(phone)) {
            throw Errors.conflict("PHONE_ALREADY_REGISTERED", "Este telefone já está cadastrado.");
        }
        UserAccount user = new UserAccount(request.name().trim(), email, phone,
                passwordEncoder.encode(request.password()), normalizer.instagram(request.childInstagram()),
                normalizer.optionalId(request.categoryId()), normalizer.optionalId(request.divisionId()),
                normalizer.optionalId(request.teamId()));
        users.save(user);
        enqueueVerification(user);
    }

    @Transactional
    @CacheEvict(cacheNames = "user-security", key = "#result", condition = "#result != null")
    public java.util.UUID verifyEmail(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = verificationTokens.findByTokenHash(tokenSupport.hash(rawToken))
                .orElseThrow(() -> Errors.badRequest("INVALID_VERIFICATION_TOKEN", "O link de confirmação é inválido."));
        if (!token.isUsableAt(now)) {
            throw Errors.badRequest("EXPIRED_VERIFICATION_TOKEN", "O link de confirmação expirou ou já foi utilizado.");
        }
        UserAccount user = users.findById(token.getUserId())
                .orElseThrow(() -> Errors.notFound("USER_NOT_FOUND", "Usuário não encontrado."));
        token.use(now);
        user.activate(now);
        return user.getId();
    }

    @Transactional
    public void resendVerification(String login) {
        findByLogin(login)
                .filter(user -> user.getStatus() == br.com.dnafutsal.backend.identity.domain.UserStatus.PENDING_EMAIL_VERIFICATION)
                .ifPresent(user -> {
                    Instant startOfDay = clock.instant().atZone(appProperties.zoneId()).toLocalDate()
                            .atStartOfDay(appProperties.zoneId()).toInstant();
                    if (verificationTokens.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay) < 3) {
                        enqueueVerification(user);
                    }
                });
    }

    public void enqueueVerification(UserAccount user) {
        Instant now = clock.instant();
        String rawToken = tokenSupport.generate();
        verificationTokens.save(new EmailVerificationToken(user.getId(), tokenSupport.hash(rawToken),
                now.plus(securityProperties.emailVerificationTtl()), now));
        mailOutbox.enqueue(mailTemplates.emailVerification(user.getName(), user.getEmail(), rawToken));
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
