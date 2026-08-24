package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

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

    public List<CatalogItemView> categories(int season) {
        return toCatalogItems(
                search(
                        new SportsEventSearch(
                                season,
                                null,
                                null,
                                null
                        )
                ).stream()
                        .map(SportsEventView::category)
                        .toList()
        );
    }

    public List<CatalogItemView> divisions(
            int season,
            String category
    ) {
        return toCatalogItems(
                search(
                        new SportsEventSearch(
                                season,
                                null,
                                null,
                                category
                        )
                ).stream()
                        .map(SportsEventView::division)
                        .toList()
        );
    }

    public List<SportsEventView> events(
            int season,
            String category,
            String division
    ) {
        return search(
                new SportsEventSearch(
                        season,
                        null,
                        division,
                        category
                )
        );
    }

    private List<CatalogItemView> toCatalogItems(
            List<String> values
    ) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(value -> new CatalogItemView(value, value))
                .toList();
    }
}
