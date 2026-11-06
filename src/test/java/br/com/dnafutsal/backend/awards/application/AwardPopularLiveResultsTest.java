package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardPopularContext;
import br.com.dnafutsal.backend.awards.api.AwardPopularBoardResponse;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwardPopularLiveResultsTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant REVIEWED = NOW.minusSeconds(7200);
    private static final AwardPopularContext CONTEXT = new AwardPopularContext(101, 2, 3,
            AwardRegistrationGender.FEMALE, AwardRegistrationContestCategory.BEST_SAVE);

    private final UUID editionId = UUID.randomUUID();
    private final AwardPopularContextWindowRepository windows = mock(AwardPopularContextWindowRepository.class);
    private final AwardPopularVotingFinalistRepository finalists = mock(AwardPopularVotingFinalistRepository.class);
    private final AwardPopularVoteRepository votes = mock(AwardPopularVoteRepository.class);
    private final AwardRegistrationEntryRepository entries = mock(AwardRegistrationEntryRepository.class);
    private final AwardRegistrationRepository registrations = mock(AwardRegistrationRepository.class);
    private final List<UUID> entryIds = new ArrayList<>();

    private AwardPopularVotingService service() {
        return new AwardPopularVotingService(mock(AwardEditionRepository.class),
                mock(AwardPreVoteEditionLockRepository.class), windows, finalists,
                mock(AwardPreVoteQueryRepository.class), mock(AwardPopularReadinessService.class),
                mock(AwardPreVoteService.class), votes, entries, registrations,
                mock(CurrentUserService.class), mock(OracleAwardVideoStorage.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void stubContext(Instant opening, Instant closing) {
        when(windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, CONTEXT.eventId(), CONTEXT.divisionId(), CONTEXT.categoryId(),
                CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(Optional.of(new AwardPopularContextWindow(editionId, CONTEXT, opening, closing)));

        List<AwardPreVoteFinalist> published = new ArrayList<>();
        List<AwardRegistrationEntry> media = new ArrayList<>();
        List<AwardRegistration> athletes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID entryId = UUID.randomUUID();
            UUID registrationId = UUID.randomUUID();
            entryIds.add(entryId);

            AwardPreVoteFinalist finalist = mock(AwardPreVoteFinalist.class);
            when(finalist.getEntryId()).thenReturn(entryId);
            when(finalist.getRank()).thenReturn((short) (i + 1));
            when(finalist.getApprovedAt()).thenReturn(REVIEWED);
            published.add(finalist);

            AwardRegistrationEntry entry = mock(AwardRegistrationEntry.class);
            when(entry.getId()).thenReturn(entryId);
            when(entry.getRegistrationId()).thenReturn(registrationId);
            when(entry.getContestCategory()).thenReturn(CONTEXT.contestCategory());
            when(entry.getReviewStatus()).thenReturn(AwardRegistrationReviewStatus.APPROVED);
            when(entry.getMediaStatus()).thenReturn(AwardRegistrationMediaStatus.READY);
            when(entry.getReviewedAt()).thenReturn(REVIEWED);
            when(entry.getSourceType()).thenReturn(AwardRegistrationMediaSource.UPLOAD);
            media.add(entry);

            AwardRegistration athlete = mock(AwardRegistration.class);
            when(athlete.getId()).thenReturn(registrationId);
            when(athlete.getEditionId()).thenReturn(editionId);
            when(athlete.getEventId()).thenReturn(CONTEXT.eventId());
            when(athlete.getDivisionId()).thenReturn(CONTEXT.divisionId());
            when(athlete.getCategoryId()).thenReturn(CONTEXT.categoryId());
            when(athlete.getGender()).thenReturn(CONTEXT.gender());
            when(athlete.getStatus()).thenReturn(AwardRegistrationStatus.SUBMITTED);
            when(athlete.getAthleteName()).thenReturn("Atleta " + (i + 1));
            when(athlete.getTeamName()).thenReturn("Equipe " + (i + 1));
            athletes.add(athlete);
        }
        when(finalists.finalists(editionId, CONTEXT.eventId(), CONTEXT.divisionId(),
                CONTEXT.categoryId(), CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(published);
        when(entries.findAllById(any())).thenReturn(media);
        when(registrations.findAllById(any())).thenReturn(athletes);
    }

    private void stubVotes() {
        when(votes.tally(editionId, CONTEXT.eventId(), CONTEXT.divisionId(),
                CONTEXT.categoryId(), CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{entryIds.get(0), 7L},
                        new Object[]{entryIds.get(1), 4L}));
    }

    @Test
    void adminsCanSeePartialTotalsWhileVotingIsOpen() {
        stubContext(NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        stubVotes();
        AwardPopularBoardResponse board = service().adminResults(editionId, CONTEXT);
        assertEquals("OPEN", board.status());
        assertEquals(11L, board.totalVotes());
        assertEquals(List.of(7L, 4L, 0L), board.finalists().stream().map(f -> f.votes()).toList());
        verify(votes).tally(editionId, CONTEXT.eventId(), CONTEXT.divisionId(),
                CONTEXT.categoryId(), CONTEXT.gender(), CONTEXT.contestCategory());
    }

    @Test
    void publicBoardNeverIncludesThePartialCount() {
        stubContext(NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        AwardPopularBoardResponse board = service().board(editionId, CONTEXT);
        assertEquals("OPEN", board.status());
        assertNull(board.totalVotes());
        assertTrue(board.finalists().stream().allMatch(f -> f.votes() == null));
        verify(votes, never()).tally(any(), anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void adminsCanSeeClosedTotals() {
        stubContext(NOW.minusSeconds(3600), NOW.minusSeconds(60));
        stubVotes();
        AwardPopularBoardResponse board = service().adminResults(editionId, CONTEXT);
        assertEquals("CLOSED", board.status());
        assertEquals(11L, board.totalVotes());
    }

    @Test
    void scheduledVotingStillDoesNotExposeTotals() {
        when(windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, CONTEXT.eventId(), CONTEXT.divisionId(), CONTEXT.categoryId(),
                CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(Optional.of(new AwardPopularContextWindow(editionId, CONTEXT,
                        NOW.plusSeconds(60), NOW.plusSeconds(3600))));
        assertThrows(BusinessException.class, () -> service().adminResults(editionId, CONTEXT));
        verifyNoInteractions(votes);
    }
}
