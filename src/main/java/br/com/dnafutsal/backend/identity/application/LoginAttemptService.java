package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int MAX_ATTEMPTS = 7;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final TokenSupport tokenSupport;

    public LoginAttemptService(StringRedisTemplate redis, TokenSupport tokenSupport) {
        this.redis = redis;
        this.tokenSupport = tokenSupport;
    }

    public void checkAllowed(String identifier, String ipAddress) {
        try {
            String value = redis.opsForValue().get(key(identifier, ipAddress));
            if (value != null && Long.parseLong(value) >= MAX_ATTEMPTS) {
                throw Errors.tooManyRequests("LOGIN_RATE_LIMITED",
                        "Muitas tentativas de login. Aguarde alguns minutos.");
            }
        } catch (NumberFormatException exception) {
            redis.delete(key(identifier, ipAddress));
        } catch (DataAccessException exception) {
            log.warn("Redis unavailable while checking login rate limit");
        }
    }

    public void failed(String identifier, String ipAddress) {
        try {
            String key = key(identifier, ipAddress);
            Long attempts = redis.opsForValue().increment(key);
            if (attempts != null && attempts == 1) {
                redis.expire(key, WINDOW);
            }
        } catch (DataAccessException exception) {
            log.warn("Redis unavailable while recording login failure");
        }
    }

    public void succeeded(String identifier, String ipAddress) {
        try {
            redis.delete(key(identifier, ipAddress));
        } catch (DataAccessException exception) {
            log.warn("Redis unavailable while clearing login rate limit");
        }
    }

    private String key(String identifier, String ipAddress) {
        return "auth:login:" + tokenSupport.hash(identifier + "|" + ipAddress);
    }
}
