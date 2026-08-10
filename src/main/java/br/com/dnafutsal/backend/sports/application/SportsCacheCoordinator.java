package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.api.MatchCompletedNotification;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SportsCacheCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SportsCacheCoordinator.class);

    private final SportsDataGateway gateway;
    private final CacheManager cacheManager;

    public SportsCacheCoordinator(SportsDataGateway gateway, CacheManager cacheManager) {
        this.gateway = gateway;
        this.cacheManager = cacheManager;
    }

    public void refresh(MatchCompletedNotification event) {
        List<SportsFilter> filters = new ArrayList<>();
        filters.add(new SportsFilter(event.categoryId(), event.divisionId(), null));
        event.teamIds().forEach(teamId ->
                filters.add(new SportsFilter(event.categoryId(), event.divisionId(), teamId)));
        filters.forEach(this::refreshAtomically);
    }

    private void refreshAtomically(SportsFilter filter) {
        try {
            List<MatchView> played = List.copyOf(gateway.playedMatches(filter));
            List<MatchView> upcoming = List.copyOf(gateway.upcomingMatches(filter));
            List<StandingView> standings = List.copyOf(gateway.standings(filter));
            List<TopScorerView> topScorers = List.copyOf(gateway.topScorers(filter));

            put("sports-played", filter.cacheKey(), played);
            put("sports-upcoming", filter.cacheKey(), upcoming);
            put("sports-standings", filter.cacheKey(), standings);
            put("sports-top-scorers", filter.cacheKey(), topScorers);
        } catch (RuntimeException exception) {
            log.error("Sports cache refresh failed for filter {}; previous snapshot was preserved",
                    filter.cacheKey(), exception);
        }
    }

    private void put(String cacheName, String key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + cacheName);
        }
        cache.put(key, value);
    }
}
