package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.IntegrationProperties;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsEventSearch;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class HttpSportsDataGateway implements SportsDataGateway {

    private final RestClient client;
    private final SportsScraperMapper mapper;
    private final boolean includePersonalData;
    private static final Logger log = LoggerFactory.getLogger(HttpSportsDataGateway.class);

    @Autowired
    public HttpSportsDataGateway(RestClient.Builder builder, IntegrationProperties properties,
                                 SportsScraperMapper mapper) {
        this(createClient(builder, properties.sports()), mapper, properties.sports().includePersonalData());
    }


    HttpSportsDataGateway(RestClient client, SportsScraperMapper mapper, boolean includePersonalData) {
        this.client = client;
        this.mapper = mapper;
        this.includePersonalData = includePersonalData;
    }

    private static RestClient createClient(
            RestClient.Builder builder,
            IntegrationProperties.SportsEndpoint sports
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                sports.connectTimeout()
        );

        requestFactory.setReadTimeout(
                sports.readTimeout()
        );

        RestClient.Builder clientBuilder = builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(sports.baseUrl())
                .requestInterceptor((request, body, execution) -> {
                    log.info(
                            "Sports scraper request method={} uri={}",
                            request.getMethod(),
                            request.getURI()
                    );

                    var response = execution.execute(request, body);

                    log.info(
                            "Sports scraper response status={} uri={}",
                            response.getStatusCode(),
                            request.getURI()
                    );

                    return response;
                });

        return clientBuilder.build();
    }

    @Override
    public List<CatalogItemView> catalogDivisions(
            int season
    ) {
        Map<String, Object> diagnostics =
                diagnostics(
                        "catalog-divisions",
                        "season",
                        season
                );

        List<ScraperCatalogOption> response =
                get(
                        diagnostics,
                        uri -> uri
                                .path("/api/v1/catalog/divisions")
                                .queryParam(
                                        "season",
                                        season
                                )
                                .build(),
                        new ParameterizedTypeReference<>() {
                        }
                );

        return mapResponse(
                diagnostics,
                () -> response.stream()
                        .map(
                                mapper::catalogItem
                        )
                        .toList()
        );
    }

    @Override
    public List<CatalogCategoryView> catalogCategories(
            int season,
            long divisionId
    ) {
        Map<String, Object> diagnostics =
                diagnostics(
                        "catalog-categories",
                        "season",
                        season,
                        "divisionId",
                        divisionId
                );

        List<ScraperCatalogCategory> response =
                get(
                        diagnostics,
                        uri -> uri
                                .path("/api/v1/catalog/categories")
                                .queryParam(
                                        "season",
                                        season
                                )
                                .queryParam(
                                        "divisionId",
                                        divisionId
                                )
                                .build(),
                        new ParameterizedTypeReference<>() {
                        }
                );

        return mapResponse(
                diagnostics,
                () -> response.stream()
                        .map(
                                mapper::catalogCategory
                        )
                        .toList()
        );
    }

    @Override
    public List<SportsEventView> searchEvents(SportsEventSearch search) {
        Map<String, Object> diagnostics = diagnostics("search-events", "season", search.season());
        List<ScraperEvent> response = get(diagnostics, uri -> {
            var builder = uri.path("/api/v1/events/search").queryParam("season", search.season());
            if (search.title() != null) {
                builder.queryParam("title", search.title());
            }
            if (search.division() != null) {
                builder.queryParam("division", search.division());
            }
            if (search.category() != null) {
                builder.queryParam("category", search.category());
            }
            return builder.build();
        }, new ParameterizedTypeReference<>() {});
        return mapResponse(diagnostics, () -> response.stream().map(mapper::event).toList());
    }

    @Override
    public SportsEventView event(long eventId) {
        Map<String, Object> diagnostics = diagnostics("get-event", "eventId", eventId);
        ScraperEvent response = get(diagnostics,
                uri -> uri.path("/api/v1/events/{eventId}").build(eventId),
                new ParameterizedTypeReference<>() {});
        return mapResponse(diagnostics, () -> {
            validateEventId(eventId, response.eventId(), diagnostics);
            return mapper.event(response);
        });
    }

    @Override
    public List<TeamView> teams(long eventId) {
        Map<String, Object> diagnostics = diagnostics("get-teams", "eventId", eventId);
        List<ScraperTeam> response = get(diagnostics,
                uri -> uri.path("/api/v1/events/{eventId}/teams").build(eventId),
                new ParameterizedTypeReference<>() {});
        return mapResponse(diagnostics, () -> mapper.teams(response));
    }

    @Override
    public SportsSnapshot snapshot(long eventId) {
        Map<String, Object> diagnostics = diagnostics("get-snapshot", "eventId", eventId);
        ScraperSnapshot response = get(diagnostics,
                uri -> uri.path("/api/v1/events/{eventId}/snapshot").build(eventId),
                new ParameterizedTypeReference<>() {});
        mapResponse(diagnostics, () -> {
            if (response.event() == null) {
                throw invalidResponse(diagnostics, null);
            }
            validateEventId(eventId, response.event().eventId(), diagnostics);
            return response;
        });

        List<ScraperScorer> scorers = null;
        if (includePersonalData) {
            Map<String, Object> scorerDiagnostics = diagnostics("get-scorers", "eventId", eventId);
            scorers = get(scorerDiagnostics, uri -> uri.path("/api/v1/events/{eventId}/scorers")
                            .queryParam("limit", 500)
                            .queryParam("includePersonalData", true)
                            .build(eventId),
                    new ParameterizedTypeReference<>() {});
        }
        List<ScraperScorer> scorerResponse = scorers;
        return mapResponse(diagnostics, () -> mapper.snapshot(response, scorerResponse));
    }

    private void validateEventId(long requested, long received, Map<String, Object> diagnostics) {
        if (requested != received) {
            throw invalidResponse(diagnostics, null);
        }
    }

    private BusinessException invalidResponse(Map<String, Object> diagnostics, Throwable cause) {
        Throwable diagnosticCause = cause == null
                ? new IllegalStateException("Sports scraper returned an invalid response")
                : sanitizedCause("Sports scraper response processing failed", cause);
        return Errors.badGateway("SPORTS_DATA_INVALID",
                "A fonte de dados esportivos retornou uma resposta inválida.",
                diagnosticCause, enrich(diagnostics, cause, null));
    }

    private <T> T get(Map<String, Object> diagnostics,
                      Function<org.springframework.web.util.UriBuilder, java.net.URI> uri,
                      ParameterizedTypeReference<T> type) {
        try {
            T response = client.get().uri(uri).retrieve().body(type);
            if (response == null) {
                throw invalidResponse(diagnostics, null);
            }
            return response;
        } catch (HttpClientErrorException.BadRequest exception) {
            throw Errors.badRequest("SPORTS_FILTER_INVALID", "Os filtros esportivos informados são inválidos.",
                    sanitizedCause("Sports scraper rejected the request", exception),
                    enrich(diagnostics, exception, exception.getStatusCode().value()));
        } catch (HttpClientErrorException.NotFound exception) {
            throw Errors.notFound("SPORTS_EVENT_NOT_FOUND", "Competição esportiva não encontrada.",
                    sanitizedCause("Sports scraper resource was not found", exception),
                    enrich(diagnostics, exception, exception.getStatusCode().value()));
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            throw Errors.dependencyUnavailable("SPORTS_API_ACCESS_DENIED",
                    "A fonte de dados esportivos recusou o acesso.",
                    sanitizedCause("Sports scraper refused access", exception),
                    enrich(diagnostics, exception, exception.getStatusCode().value()));
        } catch (HttpClientErrorException.TooManyRequests exception) {
            throw Errors.dependencyUnavailable("SPORTS_API_RATE_LIMITED",
                    "A fonte de dados esportivos atingiu o limite de requisições.",
                    sanitizedCause("Sports scraper rate limit was reached", exception),
                    enrich(diagnostics, exception, exception.getStatusCode().value()));
        } catch (HttpClientErrorException exception) {
            throw Errors.dependencyUnavailable("SPORTS_API_CLIENT_ERROR",
                    "A fonte de dados esportivos recusou a requisição.",
                    sanitizedCause("Sports scraper returned an HTTP client error", exception),
                    enrich(diagnostics, exception, exception.getStatusCode().value()));
        } catch (HttpServerErrorException exception) {
            throw Errors.dependencyUnavailable("SPORTS_DATA_UNAVAILABLE",
                    "Os dados esportivos estão temporariamente indisponíveis.",
                    sanitizedCause("Sports scraper returned an HTTP server error", exception),
                    enrich(diagnostics, exception, exception.getStatusCode().value()));
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw Errors.gatewayTimeout("SPORTS_API_TIMEOUT",
                        "A fonte de dados esportivos excedeu o tempo limite de resposta.",
                        sanitizedCause("Sports scraper request timed out", exception),
                        enrich(diagnostics, exception, null));
            }
            throw Errors.dependencyUnavailable("SPORTS_API_CONNECTION_FAILED",
                    "Não foi possível conectar à fonte de dados esportivos.",
                    sanitizedCause("Sports scraper connection failed", exception),
                    enrich(diagnostics, exception, null));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw invalidResponse(diagnostics, exception);
        }
    }

    private <T> T mapResponse(Map<String, Object> diagnostics, Supplier<T> mapping) {
        try {
            return mapping.get();
        } catch (BusinessException exception) {
            if (!exception.diagnostics().isEmpty()) {
                throw exception;
            }
            throw new BusinessException(exception.status(), exception.code(), exception.getMessage(), exception,
                    enrich(diagnostics, exception, null));
        } catch (RuntimeException exception) {
            throw invalidResponse(diagnostics, exception);
        }
    }

    private Map<String, Object> diagnostics(String operation, Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dependency", "sports-scraper");
        result.put("operation", operation);
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object value = entries[index + 1];
            if (value != null) {
                result.put(String.valueOf(entries[index]), value);
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, Object> enrich(Map<String, Object> diagnostics, Throwable exception,
                                       Integer upstreamStatus) {
        Map<String, Object> result = new LinkedHashMap<>(diagnostics);
        if (upstreamStatus != null) {
            result.put("upstreamStatus", upstreamStatus);
        }
        if (exception != null) {
            result.put("exceptionType", exception.getClass().getName());
            result.put("rootCauseType", rootCause(exception).getClass().getName());
        }
        return Map.copyOf(result);
    }

    private Throwable sanitizedCause(String message, Throwable exception) {
        Throwable root = rootCause(exception);
        IllegalStateException sanitized = new IllegalStateException(message + ": "
                + exception.getClass().getName() + (root == exception ? "" : " -> " + root.getClass().getName()));
        sanitized.setStackTrace(exception.getStackTrace());
        return sanitized;
    }

    private boolean isTimeout(Throwable exception) {
        Throwable root = rootCause(exception);
        return root instanceof SocketTimeoutException
                || root.getClass().getSimpleName().contains("TimeoutException");
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
