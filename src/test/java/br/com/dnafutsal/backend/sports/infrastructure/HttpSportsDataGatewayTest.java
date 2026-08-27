package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.IntegrationProperties;
import br.com.dnafutsal.backend.sports.domain.SportsEventSearch;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpSportsDataGatewayTest {

    private static final String BASE_URL = "https://scraper.example";

    private final SportsScraperMapper mapper = new SportsScraperMapper(
            new AppProperties("http://localhost:3000", List.of("http://localhost:3000"),
                    "America/Sao_Paulo"),
            Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void configuresDedicatedClient() {
        RestClient.Builder root = mock(RestClient.Builder.class);
        RestClient.Builder configured = mock(RestClient.Builder.class);
        when(root.clone()).thenReturn(configured);
        when(configured.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(configured);
        when(configured.baseUrl(BASE_URL)).thenReturn(configured);
        when(configured.build()).thenReturn(mock(RestClient.class));
        when(
                configured.requestInterceptor(
                        any()
                )
        ).thenReturn(configured);

        new HttpSportsDataGateway(root, properties(true), mapper);

        verify(configured).baseUrl(BASE_URL);
    }

    @Test
    void searchesEventsUsingRealScraperContract() {
        Fixture fixture = fixture(false);
        fixture.server().expect(requestTo(BASE_URL
                        + "/api/v1/events/search?season=2026&title=Paulista&division=A1&category=Principal"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("X-API-Key"))
                .andRespond(withSuccess("""
                        [{
                          "eventId": 917,
                          "title": "Paulista",
                          "season": 2026,
                          "category": "Principal",
                          "division": "A1",
                          "sourceUrl": "https://source.example/events/917"
                        }]
                        """, MediaType.APPLICATION_JSON));

        var result = fixture.gateway().searchEvents(
                new SportsEventSearch(2026, "Paulista", "A1", "Principal"));

        assertThat(result).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(917);
            assertThat(event.category()).isEqualTo("Principal");
        });
        fixture.server().verify();
    }

    @Test
    void loadsSnapshotAndReplacesSuppressedScorersWhenEnabled() {
        Fixture fixture = fixture(true);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/events/917/snapshot"))
                .andRespond(withSuccess("""
                        {
                          "event": {
                            "eventId": 917,
                            "title": "Paulista",
                            "season": 2026,
                            "category": "Principal",
                            "division": "A1",
                            "sourceUrl": "https://source.example/events/917"
                          },
                          "standings": [],
                          "games": [{
                            "gameId": 100,
                            "phase": "1ª Fase",
                            "date": "2026-04-10",
                            "time": "19:30:00",
                            "homeTeam": "Time A",
                            "homeScore": 3,
                            "awayTeam": "Time B",
                            "awayScore": 2,
                            "walkover": false
                          }],
                          "teams": [
                            {"teamId": 10, "name": "Time A"},
                            {"teamId": 20, "name": "Time B"}
                          ],
                          "scorers": [{
                            "phase": "1ª Fase",
                            "player": null,
                            "team": "Time A",
                            "goals": 5,
                            "personalDataSuppressed": true
                          }],
                          "collectedAt": "2026-08-15T10:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo(BASE_URL
                        + "/api/v1/events/917/scorers?limit=500&includePersonalData=true"))
                .andRespond(withSuccess("""
                        [{
                          "phase": "1ª Fase",
                          "player": "Atleta A",
                          "playerImageUrl": "https://img.example/a.png",
                          "team": "Time A",
                          "goals": 5,
                          "personalDataSuppressed": false
                        }]
                        """, MediaType.APPLICATION_JSON));

        SportsSnapshot result = fixture.gateway().snapshot(917);

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.id()).isEqualTo("100");
            assertThat(match.homeTeam().id()).isEqualTo("10");
            assertThat(match.status()).isEqualTo("FINISHED");
        });
        assertThat(result.topScorers()).singleElement().satisfies(scorer -> {
            assertThat(scorer.athleteName()).isEqualTo("Atleta A");
            assertThat(scorer.personalDataSuppressed()).isFalse();
        });
        fixture.server().verify();
    }

    @Test
    void mapsMissingEventToDomainNotFoundError() {
        Fixture fixture = fixture(false);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/events/999"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> fixture.gateway().event(999))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(404);
                    assertThat(exception.code()).isEqualTo("SPORTS_EVENT_NOT_FOUND");
                    assertThat(exception.diagnostics())
                            .containsEntry("operation", "get-event")
                            .containsEntry("eventId", 999L)
                            .containsEntry("upstreamStatus", 404);
                });
        fixture.server().verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400, 400, SPORTS_FILTER_INVALID",
            "401, 503, SPORTS_API_ACCESS_DENIED",
            "403, 503, SPORTS_API_ACCESS_DENIED",
            "429, 503, SPORTS_API_RATE_LIMITED",
            "500, 503, SPORTS_DATA_UNAVAILABLE"
    })
    void mapsExternalHttpFailuresWithSafeDiagnostics(int upstreamStatus, int expectedStatus,
                                                     String expectedCode) {
        Fixture fixture = fixture(false);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/events/917"))
                .andRespond(withStatus(HttpStatus.valueOf(upstreamStatus))
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("sensitive-upstream-body"));

        assertThatThrownBy(() -> fixture.gateway().event(917))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(expectedStatus);
                    assertThat(exception.code()).isEqualTo(expectedCode);
                    assertThat(exception.diagnostics())
                            .containsEntry("dependency", "sports-scraper")
                            .containsEntry("operation", "get-event")
                            .containsEntry("upstreamStatus", upstreamStatus)
                            .containsKey("exceptionType");
                    assertThat(exception.getCause()).hasMessageNotContaining("sensitive-upstream-body");
                });
        fixture.server().verify();
    }

    @Test
    void mapsMalformedJsonToBadGateway() {
        Fixture fixture = fixture(false);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/events/search?season=2026"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.gateway().searchEvents(new SportsEventSearch(2026, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.code()).isEqualTo("SPORTS_DATA_INVALID");
                    assertThat(exception.diagnostics())
                            .containsEntry("operation", "search-events")
                            .containsEntry("season", 2026)
                            .containsKey("rootCauseType");
                });
        fixture.server().verify();
    }

    @Test
    void mapsInvalidListElementToBadGateway() {
        Fixture fixture = fixture(false);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/events/search?season=2026"))
                .andRespond(withSuccess("[null]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.gateway().searchEvents(new SportsEventSearch(2026, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.code()).isEqualTo("SPORTS_DATA_INVALID");
                    assertThat(exception.diagnostics()).containsEntry("operation", "search-events");
                    assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
                });
        fixture.server().verify();
    }

    @Test
    void mapsReadTimeoutToGatewayTimeout() {
        Fixture fixture = fixture(false);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/events/917"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("upstream timed out");
                });

        assertThatThrownBy(() -> fixture.gateway().event(917))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(exception.code()).isEqualTo("SPORTS_API_TIMEOUT");
                    assertThat(exception.diagnostics())
                            .containsEntry("operation", "get-event")
                            .containsEntry("rootCauseType", SocketTimeoutException.class.getName());
                });
        fixture.server().verify();
    }

    @Test
    void mapsConnectionFailureToServiceUnavailable() {
        Fixture fixture = fixture(false);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/events/917"))
                .andRespond(request -> {
                    throw new ConnectException("connection refused");
                });

        assertThatThrownBy(() -> fixture.gateway().event(917))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("SPORTS_API_CONNECTION_FAILED");
                    assertThat(exception.diagnostics())
                            .containsEntry("operation", "get-event")
                            .containsEntry("rootCauseType", ConnectException.class.getName());
                });
        fixture.server().verify();
    }

    private Fixture fixture(boolean includePersonalData) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new HttpSportsDataGateway(builder.build(), mapper, includePersonalData), server);
    }

    private IntegrationProperties properties(boolean includePersonalData) {
        return new IntegrationProperties(
                new IntegrationProperties.SportsEndpoint(
                        BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(60),
                        Duration.ofHours(1), Duration.ofHours(1), Duration.ofMinutes(10), includePersonalData),
                new IntegrationProperties.Endpoint("https://news.example")
        );
    }

    private record Fixture(HttpSportsDataGateway gateway, MockRestServiceServer server) {
    }
}
