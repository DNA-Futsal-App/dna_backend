package br.com.dnafutsal.backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigurationTest {

    @Test
    void createsBoundedCachesWithTheConfiguredSportsTtls() {
        CacheConfiguration configuration = configuration();
        SimpleCacheManager manager = (SimpleCacheManager) configuration.cacheManager();
        manager.initializeCaches();



        assertThat(manager.getCacheNames()).containsExactlyInAnyOrder(
                "sports-catalog-divisions","sports-catalog-categories","sports-events", "sports-event", "sports-teams", "sports-snapshot",
                "user-profile", "user-security");
        assertPolicy(manager, "sports-events", 2_000, Duration.ofHours(1));
        assertPolicy(manager, "sports-event", 2_000, Duration.ofHours(1));
        assertPolicy(manager, "sports-teams", 2_000, Duration.ofHours(2));
        assertPolicy(manager, "sports-snapshot", 2_000, Duration.ofMinutes(10));
        assertPolicy(manager, "user-profile", 10_000, null);
        assertPolicy(manager, "user-security", 10_000, null);
    }

    @Test
    void reusesAValueWithoutInvokingTheLoaderAgain() {
        SimpleCacheManager manager = (SimpleCacheManager) configuration().cacheManager();
        manager.initializeCaches();
        org.springframework.cache.Cache cache = manager.getCache("sports-events");
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get("season-2026", () -> {
            loads.incrementAndGet();
            return "events";
        });
        String second = cache.get("season-2026", () -> {
            loads.incrementAndGet();
            return "other-events";
        });

        assertThat(first).isEqualTo("events");
        assertThat(second).isEqualTo("events");
        assertThat(loads).hasValue(1);
    }

    @Test
    void createsABoundedLoginAttemptCacheWithAFifteenMinuteWindow() {
        Cache<String, AtomicInteger> cache = configuration().loginAttemptCache();

        assertThat(cache.policy().eviction()).isPresent();
        assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(50_000);
        assertThat(cache.policy().expireAfterWrite()).isPresent();
        assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofMinutes(15));
    }

    private void assertPolicy(SimpleCacheManager manager, String cacheName, long maximumSize, Duration ttl) {
        CaffeineCache springCache = (CaffeineCache) manager.getCache(cacheName);
        Cache<Object, Object> cache = springCache.getNativeCache();

        assertThat(cache.policy().eviction()).isPresent();
        assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(maximumSize);
        if (ttl == null) {
            assertThat(cache.policy().expireAfterWrite()).isEmpty();
        } else {
            assertThat(cache.policy().expireAfterWrite()).isPresent();
            assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(ttl);
        }
    }

    private CacheConfiguration configuration() {
        return new CacheConfiguration(properties(), new CacheProperties(2_000, 10_000, 50_000));
    }

    private IntegrationProperties properties() {
        return new IntegrationProperties(
                new IntegrationProperties.SportsEndpoint(
                        "https://scraper.example",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        Duration.ofHours(1),
                        Duration.ofHours(2),
                        Duration.ofMinutes(10),
                        false
                ),
                new IntegrationProperties.Endpoint("https://news.example")
        );
    }
}
