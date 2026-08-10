package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.IntegrationProperties;
import br.com.dnafutsal.backend.sports.domain.CatalogItem;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.function.Function;

@Component
public class HttpSportsDataGateway implements SportsDataGateway {

    private final RestClient client;

    public HttpSportsDataGateway(RestClient.Builder builder, IntegrationProperties properties) {
        RestClient.Builder configured = builder.baseUrl(properties.sports().baseUrl());
        if (properties.sports().apiKey() != null && !properties.sports().apiKey().isBlank()) {
            configured.defaultHeader("X-Api-Key", properties.sports().apiKey());
        }
        this.client = configured.build();
    }

    @Override
    public List<CatalogItem> categories() {
        return get(uri -> uri.path("/api/v1/catalog/categories").build(), new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<CatalogItem> divisions(String categoryId) {
        return get(uri -> uri.path("/api/v1/catalog/divisions").queryParam("categoryId", categoryId).build(),
                new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<TeamView> teams(String categoryId, String divisionId) {
        return get(uri -> uri.path("/api/v1/catalog/teams")
                        .queryParam("categoryId", categoryId).queryParam("divisionId", divisionId).build(),
                new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<MatchView> playedMatches(SportsFilter filter) {
        return sportsList("/api/v1/matches/played", filter, new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<MatchView> upcomingMatches(SportsFilter filter) {
        return sportsList("/api/v1/matches/upcoming", filter, new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<StandingView> standings(SportsFilter filter) {
        return sportsList("/api/v1/standings", filter, new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<TopScorerView> topScorers(SportsFilter filter) {
        return sportsList("/api/v1/top-scorers", filter, new ParameterizedTypeReference<>() {});
    }

    private <T> List<T> sportsList(String path, SportsFilter filter, ParameterizedTypeReference<List<T>> type) {
        return get(uri -> {
            var builder = uri.path(path)
                    .queryParam("categoryId", filter.categoryId())
                    .queryParam("divisionId", filter.divisionId());
            if (filter.teamId() != null) {
                builder.queryParam("teamId", filter.teamId());
            }
            return builder.build();
        }, type);
    }

    private <T> T get(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                      ParameterizedTypeReference<T> type) {
        try {
            T response = client.get().uri(uri).retrieve().body(type);
            if (response == null) {
                throw Errors.dependencyUnavailable("SPORTS_DATA_UNAVAILABLE",
                        "A fonte de dados esportivos retornou uma resposta vazia.");
            }
            return response;
        } catch (br.com.dnafutsal.backend.common.BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw Errors.dependencyUnavailable("SPORTS_DATA_UNAVAILABLE",
                    "Os dados esportivos estão temporariamente indisponíveis.");
        }
    }
}
