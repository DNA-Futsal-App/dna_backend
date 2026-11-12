package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.CoachBallotResponse;
import br.com.dnafutsal.backend.awards.api.SubmitCoachBallotV2Request;
import br.com.dnafutsal.backend.awards.domain.CoachVotingState;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CoachCategoryVotingServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final CoachVotingV2Service engine = mock(CoachVotingV2Service.class);
    private final CoachCategoryVotingService service = new CoachCategoryVotingService(
            jdbc, currentUser, engine,
            Clock.fixed(Instant.parse("2026-11-12T18:00:00Z"), ZoneOffset.UTC));

    private final UUID userId = UUID.randomUUID();
    private final UUID contextId = UUID.randomUUID();

    /** The service locks the voter row before attempting to write the ballot. */
    private void mockExistingVotingContext() {
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any(), eq(contextId)))
                .thenReturn(List.of(contextId));
    }

    @Test
    void pendingCoachCannotReadCandidatesOrSubmitViaNewEndpoint() {
        when(currentUser.userId()).thenReturn(userId);
        mockExistingVotingContext();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(contextId), eq(userId)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.context(contextId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("aprovada");
        assertThatThrownBy(() -> service.candidates(contextId, UUID.randomUUID(), "123"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.submit(
                new SubmitCoachBallotV2Request(contextId, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("aprovada");
        verifyNoInteractions(engine);
    }

    @Test
    void approvedCoachUsesExistingValidatedVotingEngine() {
        when(currentUser.userId()).thenReturn(userId);
        mockExistingVotingContext();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(contextId), eq(userId)))
                .thenReturn(true);
        CoachBallotResponse receipt = new CoachBallotResponse(
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-11-12T18:01:00Z"), List.of());
        var request = new SubmitCoachBallotV2Request(contextId, List.of());
        when(engine.submit(request)).thenReturn(receipt);

        assertThat(service.submit(request)).isSameAs(receipt);
        verify(engine).submit(request);
        verify(engine, never()).context(contextId);
    }

    @Test
    void aMissingContextCannotRegisterVotes() {
        // A missing row cannot be locked, so voting is refused before authorization.
        assertThatThrownBy(() -> service.submit(
                new SubmitCoachBallotV2Request(contextId, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Categoria não disponível.");
        verifyNoInteractions(engine);
    }

    @Test
    void closedAndScheduledWindowsCannotBeShownAsOpen() {
        Instant now = Instant.parse("2026-11-12T18:00:00Z");
        assertThat(CoachCategoryVotingService.state("DRAFT", null, null, false, now))
                .isEqualTo(CoachVotingState.DRAFT);
        assertThat(CoachCategoryVotingService.state("OPEN", now.plusSeconds(60), null, false, now))
                .isEqualTo(CoachVotingState.SCHEDULED);
        assertThat(CoachCategoryVotingService.state("OPEN", null, now, false, now))
                .isEqualTo(CoachVotingState.CLOSED);
        assertThat(CoachCategoryVotingService.state("OPEN", null, now.plusSeconds(60), false, now))
                .isEqualTo(CoachVotingState.OPEN);
        assertThat(CoachCategoryVotingService.state("CLOSED", null, null, true, now))
                .isEqualTo(CoachVotingState.SUBMITTED);
    }
}
