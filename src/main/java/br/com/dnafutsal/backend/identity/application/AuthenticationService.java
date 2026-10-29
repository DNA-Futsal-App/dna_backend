package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.api.AuthResponse;
import br.com.dnafutsal.backend.identity.api.LoginRequest;
import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.domain.RefreshSession;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.RefreshSessionRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuthenticationService {

    private final UserAccountRepository users;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNormalizer normalizer;
    private final TokenSupport tokenSupport;
    private final JwtTokenService jwtTokens;
    private final SecurityProperties properties;
    private final LoginAttemptService attempts;
    private final Clock clock;
    private final String dummyPasswordHash;
    private final RegistrationService registration;

    public AuthenticationService(UserAccountRepository users,
                                 RefreshSessionRepository refreshSessions,
                                 PasswordEncoder passwordEncoder,
                                 IdentityNormalizer normalizer,
                                 TokenSupport tokenSupport,
                                 JwtTokenService jwtTokens,
                                 SecurityProperties properties,
                                 LoginAttemptService attempts,
                                 Clock clock,
                                 RegistrationService registration
    ) {
        this.users = users;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.normalizer = normalizer;
        this.tokenSupport = tokenSupport;
        this.jwtTokens = jwtTokens;
        this.properties = properties;
        this.attempts = attempts;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(tokenSupport.generate());
        this.registration = registration;
    }

    @Transactional(
            noRollbackFor = EmailVerificationRequiredException.class
    )
    public AuthResponse login(LoginRequest request, ClientContext context) {
        String loginKey = request.login().trim().toLowerCase();
        attempts.checkAllowed(loginKey, context.ipAddress());
        Optional<UserAccount> found = Optional.of(findByLogin(request.login()).orElseThrow(
                () -> {
                    attempts.failed(loginKey, context.ipAddress());
                    return Errors.badRequest("INVALID_CREDENTIALS", "Email/telefone ainda não cadastrado em nossa base de dados");
                }
        ));
        String hash = found.map(UserAccount::getPasswordHash).orElse(dummyPasswordHash);
        if (!passwordEncoder.matches(request.password(), hash)) {
            attempts.failed(loginKey, context.ipAddress());
            throw Errors.badRequest("INVALID_CREDENTIALS", "Senha inválida.");
        }
        UserAccount user = found.get();
        if (user.getStatus() == UserStatus.PENDING_DELETION
                && user.getDeletionScheduledFor() != null
                && clock.instant().isBefore(user.getDeletionScheduledFor())) {
            throw Errors.forbidden("ACCOUNT_PENDING_DELETION",
                    "Sua conta tem uma exclusão agendada. Confirme se deseja cancelar a exclusão e entrar.");
        }
        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            attempts.failed(loginKey, context.ipAddress());
            registration.resendVerification(request.login());
            throw new EmailVerificationRequiredException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw Errors.forbidden("ACCOUNT_UNAVAILABLE", "A conta não está disponível.");
        }
        attempts.succeeded(loginKey, context.ipAddress());
        user.onLogin();
        users.save(user);
        return issue(user, context);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, ClientContext context) {
        Instant now = clock.instant();
        RefreshSession session = refreshSessions.findByTokenHash(tokenSupport.hash(rawRefreshToken))
                .orElseThrow(() -> Errors.unauthorized("INVALID_REFRESH_TOKEN", "A sessão é inválida."));
        if (!session.isUsableAt(now)) {
            throw Errors.unauthorized("EXPIRED_REFRESH_TOKEN", "A sessão expirou. Faça login novamente.");
        }
        UserAccount user = users.findByIdForUpdate(session.getUserId())
                .orElseThrow(() -> Errors.unauthorized("INVALID_REFRESH_TOKEN", "A sessão é inválida."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw Errors.forbidden("ACCOUNT_UNAVAILABLE", "A conta não está disponível.");
        }
        return issue(user, context);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshSessions.findByTokenHash(tokenSupport.hash(rawRefreshToken))
                .filter(session -> session.isUsableAt(clock.instant()))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    private AuthResponse issue(UserAccount user, ClientContext context) {
        Instant now = clock.instant();
        String rawRefreshToken = tokenSupport.generate();
        refreshSessions.save(new RefreshSession(user.getId(), tokenSupport.hash(rawRefreshToken),
                now.plus(properties.refreshTokenTtl()), now, context.userAgent(), context.ipAddress()));
        return new AuthResponse("Bearer", jwtTokens.createAccessToken(user), jwtTokens.expiresInSeconds(),
                rawRefreshToken, UserProfileResponse.from(user));
    }

    private Optional<UserAccount> findByLogin(String login) {
        String value = login.trim();
        if (value.contains("@")) {
            return users.findByEmailForUpdate(normalizer.email(value));
        }
        return users.findByPhoneForUpdate(normalizer.phone(value));

    }
}
