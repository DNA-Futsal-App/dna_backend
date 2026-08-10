package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.domain.CatalogItem;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
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

    @Cacheable(cacheNames = "sports-categories", key = "'all'", sync = true)
    public List<CatalogItem> categories() {
        return List.copyOf(gateway.categories());
    }

    @Cacheable(cacheNames = "sports-divisions", key = "#categoryId", sync = true)
    public List<CatalogItem> divisions(String categoryId) {
        return List.copyOf(gateway.divisions(categoryId));
    }

    @Cacheable(cacheNames = "sports-teams", key = "#categoryId + ':' + #divisionId", sync = true)
    public List<TeamView> teams(String categoryId, String divisionId) {
        return List.copyOf(gateway.teams(categoryId, divisionId));
    }
}
