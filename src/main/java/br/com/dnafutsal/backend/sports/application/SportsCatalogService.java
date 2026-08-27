package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
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

    public SportsCatalogService(
            SportsDataGateway gateway
    ) {
        this.gateway = gateway;
    }

    @Cacheable(
            cacheNames = "sports-catalog-divisions",
            key = "#season",
            sync = true
    )
    public List<CatalogItemView> divisions(
            int season
    ) {
        return List.copyOf(
                gateway.catalogDivisions(
                        season
                )
        );
    }

    @Cacheable(
            cacheNames = "sports-catalog-categories",
            key = "#season + ':' + #divisionId",
            sync = true
    )
    public List<CatalogCategoryView> categories(
            int season,
            long divisionId
    ) {
        return List.copyOf(
                gateway.catalogCategories(
                        season,
                        divisionId
                )
        );
    }

    @Cacheable(
            cacheNames = "sports-events",
            key = "#search.cacheKey()",
            sync = true
    )
    public List<SportsEventView> search(
            SportsEventSearch search
    ) {
        validate(search);

        return List.copyOf(
                gateway.searchEvents(search)
        );
    }

    @Cacheable(
            cacheNames = "sports-event",
            key = "#eventId",
            sync = true
    )
    public SportsEventView event(
            long eventId
    ) {
        return gateway.event(
                eventId
        );
    }

    @Cacheable(
            cacheNames = "sports-teams",
            key = "#eventId",
            sync = true
    )
    public List<TeamView> teams(
            long eventId
    ) {
        return List.copyOf(
                gateway.teams(eventId)
        );
    }

    private void validate(
            SportsEventSearch search
    ) {
        if (
                search.category() != null &&
                        search.division() == null
        ) {
            throw Errors.badRequest(
                    "SPORTS_FILTER_INVALID",
                    "Informe a divisão antes da categoria."
            );
        }
    }
}