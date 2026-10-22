package br.com.dnafutsal.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class CacheConfiguration {

    static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    static final Duration MAX_SPORTS_SNAPSHOT_TTL = Duration.ofMinutes(1);

    private final IntegrationProperties integrations;
    private final CacheProperties properties;

    public CacheConfiguration(IntegrationProperties integrations, CacheProperties properties) {
        this.integrations = integrations;
        this.properties = properties;
    }

    @Bean
    public CacheManager cacheManager() {
        long sportsMaxEntries = properties.sportsMaxEntries();
        Duration configuredSnapshotTtl = integrations.sports().snapshotCacheTtl();
        Duration sportsSnapshotTtl = configuredSnapshotTtl.compareTo(MAX_SPORTS_SNAPSHOT_TTL) > 0
                ? MAX_SPORTS_SNAPSHOT_TTL
                : configuredSnapshotTtl;

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                expiringCache(
                        "sports-catalog-divisions",
                        integrations.sports().eventCacheTtl(),
                        sportsMaxEntries
                ),
                expiringCache(
                        "sports-catalog-categories",
                        integrations.sports().eventCacheTtl(),
                        sportsMaxEntries
                ),
                expiringCache(
                        "sports-events",
                        integrations.sports().eventCacheTtl(),
                        sportsMaxEntries
                ),
                expiringCache(
                        "sports-event",
                        integrations.sports().eventCacheTtl(),
                        sportsMaxEntries
                ),
                expiringCache(
                        "sports-teams",
                        integrations.sports().teamCacheTtl(),
                        sportsMaxEntries
                ),
                expiringCache(
                        "sports-snapshot",
                        sportsSnapshotTtl,
                        sportsMaxEntries
                )
        ));
        return manager;
    }

    @Bean("loginAttemptCache")
    public com.github.benmanes.caffeine.cache.Cache<String, AtomicInteger> loginAttemptCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.loginAttemptMaxEntries())
                .expireAfterWrite(LOGIN_ATTEMPT_WINDOW)
                .recordStats()
                .build();
    }

    private CaffeineCache expiringCache(String name, Duration ttl, long maximumSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build(), false);
    }

}
