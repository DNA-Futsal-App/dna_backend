package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.api.MatchCompletedNotification;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsCacheCoordinatorTest {

    @Test
    void preservesPreviousSnapshotWhenRefreshFails() {
        SportsDataGateway gateway = mock(SportsDataGateway.class);
        ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager(
                "sports-played", "sports-upcoming", "sports-standings", "sports-top-scorers");
        SportsFilter general = new SportsFilter("sub-13", "especial", null);
        caches.getCache("sports-played").put(general.cacheKey(), List.of("previous"));
        when(gateway.playedMatches(any())).thenThrow(new IllegalStateException("scraper unavailable"));

        new SportsCacheCoordinator(gateway, caches).refresh(new MatchCompletedNotification(
                UUID.randomUUID(), "sub-13", "especial", List.of("team-a", "team-b"), Instant.now()));

        assertThat(caches.getCache("sports-played").get(general.cacheKey(), List.class))
                .containsExactly("previous");
    }
}
