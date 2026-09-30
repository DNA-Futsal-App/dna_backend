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

    public AuthenticationService(UserAccountRepository users,
                                 RefreshSessionRepository refreshSessions,
                                 PasswordEncoder passwordEncoder,
                                 IdentityNormalizer normalizer,
                                 TokenSupport tokenSupport,
                                 JwtTokenService jwtTokens,
                                 SecurityProperties properties,
                                 LoginAttemptService attempts,
                                 Clock clock) {
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
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientContext context) {
        String loginKey = request.login().trim().toLowerCase();
        attempts.checkAllowed(loginKey, context.ipAddress());
        Optional<UserAccount> found = findByLogin(request.login());
        String hash = found.map(UserAccount::getPasswordHash).orElse(dummyPasswordHash);
        if (!passwordEncoder.matches(request.password(), hash) || found.isEmpty()) {
            attempts.failed(loginKey, context.ipAddress());
            throw Errors.unauthorized("INVALID_CREDENTIALS", "E-mail/telefone ou senha inválidos.");
        }
        UserAccount user = found.get();
        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            throw Errors.forbidden("EMAIL_NOT_VERIFIED", "Confirme seu e-mail antes de entrar.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw Errors.forbidden("ACCOUNT_UNAVAILABLE", "A conta não está disponível.");
        }
        attempts.succeeded(loginKey, context.ipAddress());
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
        UserAccount user = users.findById(session.getUserId())
                .orElseThrow(() -> Errors.unauthorized("INVALID_REFRESH_TOKEN", "A sessão é inválida."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw Errors.forbidden("ACCOUNT_UNAVAILABLE", "A conta não está disponível.");
        }
        session.revoke(now);
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
            return users.findByEmail(normalizer.email(value));
        }
        try {
            return users.findByPhone(normalizer.phone(value));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
