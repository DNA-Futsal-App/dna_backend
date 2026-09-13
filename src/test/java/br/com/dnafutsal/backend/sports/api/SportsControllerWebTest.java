package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.RequestIdFilter;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.SecurityConfiguration;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.application.UserSecurityStateService;
import br.com.dnafutsal.backend.sports.application.*;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
        controllers = {
                SportsCatalogController.class,
                SportsController.class
        },
        properties = {
                "app.frontend-base-url=http://localhost:3000",
                "app.cors-allowed-origins[0]=http://localhost:3000",
                "app.timezone=America/Sao_Paulo",

                "app.security.jwt-secret=test-secret-with-at-least-sixty-four-characters-01234567890123456789",
                "app.security.access-token-ttl=PT15M",
                "app.security.refresh-token-ttl=P30D",
                "app.security.email-verification-ttl=PT24H",
                "app.security.password-reset-ttl=PT30M"
        }
)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
@EnableConfigurationProperties({
        SecurityProperties.class,
        AppProperties.class
})
@ExtendWith(OutputCaptureExtension.class)
class SportsControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SportsCatalogService catalog;

    @MockitoBean
    private CurrentUserService currentUser;

    @MockitoBean
    private SportsFilterResolver filters;

    @MockitoBean
    private SportsQueryService sports;

    @MockitoBean
    private UserSecurityStateService securityStates;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private MyTeamService myTeam;

    @MockitoBean
    private SportsHomeService home;

    @Test
    void exposesEventDiscoveryWithoutAuthentication() throws Exception {
        when(catalog.search(any())).thenReturn(List.of(new SportsEventView(
                917, "Paulista", 2026, "Principal", "A1", "https://source.example/events/917")));

        mvc.perform(get("/api/v1/public/events")
                        .param("season", "2026")
                        .param("title", "Paulista"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(917))
                .andExpect(jsonPath("$[0].division").value("A1"));
    }

    @Test
    void reportsMissingSeasonAsBadRequest() throws Exception {
        String requestId = "frontend-request-123";

        mvc.perform(get("/api/v1/public/events").header(RequestIdFilter.HEADER_NAME, requestId))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
                .andExpect(jsonPath("$.code").value("REQUEST_PARAMETER_MISSING"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.fields.season").value("é obrigatório"));
    }

    @Test
    void reportsInvalidSeasonTypeAsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/public/events").param("season", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_PARAMETER_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.fields.season").value("deve ser um número válido"));
    }

    @Test
    void reportsSeasonConstraintAsValidationError() throws Exception {
        mvc.perform(get("/api/v1/public/events").param("season", "2015"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.fields.season").isNotEmpty());
    }

    @Test
    void reportsInvalidDateFormatAsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/matches/played")
                        .with(user("user"))
                        .param("eventId", "917")
                        .param("from", "16-08-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_PARAMETER_INVALID"))
                .andExpect(jsonPath("$.fields.from").value("deve ser uma data no formato AAAA-MM-DD"));
    }

    @Test
    void exposesSafeDependencyProblemAndWritesDiagnosticLog(CapturedOutput output) throws Exception {
        String requestId = "frontend-request-456";
        BusinessException failure = new BusinessException(
                HttpStatus.BAD_GATEWAY,
                "SPORTS_DATA_INVALID",
                "A fonte de dados esportivos retornou uma resposta inválida.",
                new IllegalStateException("technical mapping detail"),
                Map.of("dependency", "sports-scraper", "operation", "search-events", "season", 2026)
        );
        when(catalog.search(any())).thenThrow(failure);

        mvc.perform(get("/api/v1/public/events")
                        .header(RequestIdFilter.HEADER_NAME, requestId)
                        .param("season", "2026"))
                .andExpect(status().isBadGateway())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
                .andExpect(jsonPath("$.code").value("SPORTS_DATA_INVALID"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.diagnostics").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist());

        assertThat(output)
                .contains("requestId=" + requestId)
                .contains("code=SPORTS_DATA_INVALID")
                .contains("operation=search-events")
                .contains("technical mapping detail");
    }

    @Test
    void logsUnexpectedExceptionWithCorrelationId(CapturedOutput output) throws Exception {
        String requestId = "frontend-request-789";
        when(catalog.search(any())).thenThrow(new IllegalStateException("unexpected mapper failure"));

        mvc.perform(get("/api/v1/public/events")
                        .header(RequestIdFilter.HEADER_NAME, requestId)
                        .param("season", "2026"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(output)
                .contains("Unexpected request failure")
                .contains("requestId=" + requestId)
                .contains("exceptionType=java.lang.IllegalStateException")
                .contains("unexpected mapper failure");
    }

    @Test
    void protectsSportsResultsWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/matches/played").param("eventId", "917"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void acceptsAuthenticatedSportsQueryUsingEventId() throws Exception {
        UUID userId = UUID.randomUUID();
        SportsFilter filter = new SportsFilter(917, null);
        when(currentUser.userId()).thenReturn(userId);
        when(filters.resolve(userId, 917L, null)).thenReturn(filter);
        when(sports.playedMatches(filter, null, null, null)).thenReturn(List.of());

        mvc.perform(get("/api/v1/matches/played").with(user("user")).param("eventId", "917"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(filters).resolve(userId, 917L, null);
    }

    @Test
    void doesNotExposeLegacySportsWebhook() throws Exception {
        mvc.perform(post("/api/v1/internal/sports/match-completed").with(user("user")))
                .andExpect(status().isNotFound());
    }
}
