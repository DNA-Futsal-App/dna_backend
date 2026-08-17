package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.TokenSupport;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class LoginAttemptServiceTest {

    private static final String IDENTIFIER = "user@example.com";
    private static final String IP_ADDRESS = "203.0.113.10";

    private MutableTicker ticker;
    private Cache<String, AtomicInteger> cache;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        ticker = new MutableTicker();
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(100)
                .ticker(ticker)
                .build();
        service = new LoginAttemptService(cache, new TokenSupport());
    }

    @Test
    void blocksLoginAfterSevenFailures() {
        failLogin(6);
        assertThatCode(() -> service.checkAllowed(IDENTIFIER, IP_ADDRESS)).doesNotThrowAnyException();

        service.failed(IDENTIFIER, IP_ADDRESS);

        BusinessException exception = catchThrowableOfType(
                BusinessException.class, () -> service.checkAllowed(IDENTIFIER, IP_ADDRESS));
        assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exception.code()).isEqualTo("LOGIN_RATE_LIMITED");
    }

    @Test
    void clearsFailuresAfterSuccessfulLogin() {
        failLogin(7);

        service.succeeded(IDENTIFIER, IP_ADDRESS);

        assertThatCode(() -> service.checkAllowed(IDENTIFIER, IP_ADDRESS)).doesNotThrowAnyException();
        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    void expiresFailuresFifteenMinutesAfterTheFirstFailure() {
        failLogin(7);

        ticker.advance(Duration.ofMinutes(15));

        assertThatCode(() -> service.checkAllowed(IDENTIFIER, IP_ADDRESS)).doesNotThrowAnyException();
    }

    @Test
    void doesNotStoreLoginIdentifiersInPlainText() {
        service.failed(IDENTIFIER, IP_ADDRESS);

        assertThat(cache.asMap().keySet())
                .allMatch(key -> !key.contains(IDENTIFIER) && !key.contains(IP_ADDRESS));
    }

    private void failLogin(int failures) {
        for (int index = 0; index < failures; index++) {
            service.failed(IDENTIFIER, IP_ADDRESS);
        }
    }

    private static final class MutableTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
