package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwardPopularVotingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final AwardPopularContext CONTEXT = new AwardPopularContext(1, 2, 3,
            AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEAUTIFUL_GOAL);

    private final AwardPopularContextWindowRepository windows = mock(AwardPopularContextWindowRepository.class);
    private final AwardPopularReadinessService readiness = mock(AwardPopularReadinessService.class);
    private final AwardPreVoteService preVote = mock(AwardPreVoteService.class);
    private final AwardPreVoteEditionLockRepository locks = mock(AwardPreVoteEditionLockRepository.class);
    private final AwardPopularVotingFinalistRepository finalists = mock(AwardPopularVotingFinalistRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);

    private AwardPopularVotingService service() {
        return new AwardPopularVotingService(mock(AwardEditionRepository.class), locks,
                windows, finalists, mock(AwardPreVoteQueryRepository.class), readiness, preVote,
                mock(AwardPopularVoteRepository.class), mock(AwardRegistrationEntryRepository.class),
                mock(AwardRegistrationRepository.class), currentUser,
                mock(OracleAwardVideoStorage.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsVoteBeforeTheSelectedContextOpens() {
        UUID edition = UUID.randomUUID();
        when(windows.lockForVote(edition, 1, 2, 3, CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(Optional.of(new AwardPopularContextWindow(edition, CONTEXT,
                        NOW.plusSeconds(60), NOW.plusSeconds(3600))));
        assertThrows(BusinessException.class, () -> service().vote(edition,
                new AwardPopularVoteRequest(CONTEXT, UUID.randomUUID())));
        verifyNoInteractions(currentUser);
    }

    @Test
    void refusesScheduleAndReturnsEveryMissingAdministrator() {
        UUID edition = UUID.randomUUID();
        when(locks.lockEdition(edition)).thenReturn(Optional.of(mock(AwardEdition.class)));
        var missing = List.of(
                new AwardMissingAdminResponse(UUID.randomUUID(), "Maria", "maria@exemplo.com",
                        List.of(new AwardMissingVideoResponse(UUID.randomUUID(), "Atleta 1", 101))),
                new AwardMissingAdminResponse(UUID.randomUUID(), "João", "joao@exemplo.com",
                        List.of(new AwardMissingVideoResponse(UUID.randomUUID(), "Atleta 2", 102))));
        when(readiness.assess(edition, CONTEXT)).thenReturn(new AwardPopularReadinessResponse(
                CONTEXT, 4, 0L, 3, false, false, false, List.of("NOTAS_PENDENTES"), missing));
        var request = new AwardPopularContextWindowRequest(CONTEXT, NOW.plusSeconds(60), NOW.plusSeconds(3600));
        BusinessException ex = assertThrows(BusinessException.class, () -> service().schedule(edition, request));
        assertEquals("AWARD_POPULAR_NOT_READY", ex.code());
        assertEquals(missing, ex.diagnostics().get("missingAdmins"));
        verifyNoInteractions(preVote);
        verify(windows, never()).saveAndFlush(any());
    }

    @Test
    void refusesSchedulingTheSameContextTwice() {
        UUID edition = UUID.randomUUID();
        when(locks.lockEdition(edition)).thenReturn(Optional.of(mock(AwardEdition.class)));
        when(windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                edition, 1, 2, 3, CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(Optional.of(new AwardPopularContextWindow(edition, CONTEXT,
                        NOW.plusSeconds(60), NOW.plusSeconds(3600))));
        var req = new AwardPopularContextWindowRequest(CONTEXT, NOW.plusSeconds(120), NOW.plusSeconds(7200));
        BusinessException ex = assertThrows(BusinessException.class, () -> service().schedule(edition, req));
        assertEquals("AWARD_POPULAR_ALREADY_SCHEDULED", ex.code());
        verifyNoInteractions(preVote, readiness);
    }
}
