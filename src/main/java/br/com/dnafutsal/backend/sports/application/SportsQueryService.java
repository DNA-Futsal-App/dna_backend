package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SportsQueryService {

    private final SportsDataGateway gateway;

    public SportsQueryService(SportsDataGateway gateway) {
        this.gateway = gateway;
    }

    @Cacheable(cacheNames = "sports-played", key = "#filter.cacheKey()", sync = true)
    public List<MatchView> playedMatches(SportsFilter filter) {
        return List.copyOf(gateway.playedMatches(filter));
    }

    @Cacheable(cacheNames = "sports-upcoming", key = "#filter.cacheKey()", sync = true)
    public List<MatchView> upcomingMatches(SportsFilter filter) {
        return List.copyOf(gateway.upcomingMatches(filter));
    }

    @Cacheable(cacheNames = "sports-standings", key = "#filter.cacheKey()", sync = true)
    public List<StandingView> standings(SportsFilter filter) {
        return List.copyOf(gateway.standings(filter));
    }

    @Cacheable(cacheNames = "sports-top-scorers", key = "#filter.cacheKey()", sync = true)
    public List<TopScorerView> topScorers(SportsFilter filter) {
        return List.copyOf(gateway.topScorers(filter));
    }
}
