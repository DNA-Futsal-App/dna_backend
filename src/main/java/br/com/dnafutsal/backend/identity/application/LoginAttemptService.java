package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 7;

    private final Cache<String, AtomicInteger> attempts;
    private final TokenSupport tokenSupport;

    public LoginAttemptService(@Qualifier("loginAttemptCache") Cache<String, AtomicInteger> attempts,
                               TokenSupport tokenSupport) {
        this.attempts = attempts;
        this.tokenSupport = tokenSupport;
    }

    public void checkAllowed(String identifier, String ipAddress) {
        AtomicInteger counter = attempts.getIfPresent(key(identifier, ipAddress));
        if (counter != null && counter.get() >= MAX_ATTEMPTS) {
            throw Errors.tooManyRequests("LOGIN_RATE_LIMITED",
                    "Muitas tentativas de login. Aguarde alguns minutos.");
        }
    }

    public void failed(String identifier, String ipAddress) {
        attempts.get(key(identifier, ipAddress), ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    public void succeeded(String identifier, String ipAddress) {
        attempts.invalidate(key(identifier, ipAddress));
    }

    private String key(String identifier, String ipAddress) {
        return "auth:login:" + tokenSupport.hash(identifier + "|" + ipAddress);
    }
}
