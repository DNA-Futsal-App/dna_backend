package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsEventSearch;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SportsCatalogService {

    private final SportsDataGateway gateway;

    public SportsCatalogService(SportsDataGateway gateway) {
        this.gateway = gateway;
    }

    @Cacheable(cacheNames = "sports-events", key = "#search.cacheKey()", sync = true)
    public List<SportsEventView> search(SportsEventSearch search) {
        validate(search);
        return List.copyOf(gateway.searchEvents(search));
    }

    @Cacheable(cacheNames = "sports-event", key = "#eventId", sync = true)
    public SportsEventView event(long eventId) {
        return gateway.event(eventId);
    }

    @Cacheable(cacheNames = "sports-teams", key = "#eventId", sync = true)
    public List<TeamView> teams(long eventId) {
        return List.copyOf(gateway.teams(eventId));
    }

    private void validate(SportsEventSearch search) {
        if (search.division() != null && search.title() == null) {
            throw Errors.badRequest("SPORTS_FILTER_INVALID", "Informe o título antes da divisão.");
        }
        if (search.category() != null && (search.title() == null || search.division() == null)) {
            throw Errors.badRequest("SPORTS_FILTER_INVALID", "Informe título e divisão antes da categoria.");
        }
    }
}
