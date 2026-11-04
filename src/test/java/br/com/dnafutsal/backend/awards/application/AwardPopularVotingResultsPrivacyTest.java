package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardPopularBoardResponse;
import br.com.dnafutsal.backend.awards.api.AwardPopularContext;
import br.com.dnafutsal.backend.awards.api.AwardPopularFinalistResponse;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwardPopularVotingResultsPrivacyTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final AwardPopularContext CONTEXT = new AwardPopularContext(908, 3, 1,
            AwardRegistrationGender.MALE, AwardRegistrationContestCategory.FREE_KICK_GOAL);

    private final AwardEditionRepository editions = mock(AwardEditionRepository.class);
    private final AwardPreVoteEditionLockRepository editionLocks = mock(AwardPreVoteEditionLockRepository.class);
    private final AwardPopularContextWindowRepository windows = mock(AwardPopularContextWindowRepository.class);
    private final AwardPopularVotingFinalistRepository finalistQueries = mock(AwardPopularVotingFinalistRepository.class);
    private final AwardPreVoteQueryRepository preVoteQueries = mock(AwardPreVoteQueryRepository.class);
    private final AwardPopularReadinessService readiness = mock(AwardPopularReadinessService.class);
    private final AwardPreVoteService preVote = mock(AwardPreVoteService.class);
    private final AwardPopularVoteRepository votes = mock(AwardPopularVoteRepository.class);
    private final AwardRegistrationEntryRepository entries = mock(AwardRegistrationEntryRepository.class);
    private final AwardRegistrationRepository registrations = mock(AwardRegistrationRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final OracleAwardVideoStorage storage = mock(OracleAwardVideoStorage.class);

    private AwardPopularVotingService service() {
        return new AwardPopularVotingService(editions, editionLocks, windows, finalistQueries,
                preVoteQueries, readiness, preVote, votes, entries, registrations,
                currentUser, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UUID prepareBoard(boolean closed, boolean fourAllowed) {
        UUID editionId = UUID.randomUUID();
        Instant opensAt = NOW.minusSeconds(3600);
        Instant closesAt = closed ? NOW.minusSeconds(60) : NOW.plusSeconds(3600);
        when(windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, 908, 3, 1, CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(java.util.Optional.of(new AwardPopularContextWindow(
                        editionId, CONTEXT, opensAt, closesAt)));

        List<AwardPreVoteFinalist> published = new ArrayList<>();
        List<AwardRegistrationEntry> approved = new ArrayList<>();
        List<AwardRegistration> submitted = new ArrayList<>();
        for (int i = 1; i <= (fourAllowed ? 4 : 3); i++) {
            UUID entryId = UUID.randomUUID();
            UUID registrationId = UUID.randomUUID();
            Instant reviewedAt = NOW.minusSeconds(7200);
            AwardPreVoteFinalist finalist = mock(AwardPreVoteFinalist.class);
            when(finalist.getRank()).thenReturn((short) i);
            when(finalist.getEntryId()).thenReturn(entryId);
            when(finalist.getApprovedAt()).thenReturn(reviewedAt);
            published.add(finalist);

            AwardRegistrationEntry entry = mock(AwardRegistrationEntry.class);
            when(entry.getId()).thenReturn(entryId);
            when(entry.getRegistrationId()).thenReturn(registrationId);
            when(entry.getContestCategory()).thenReturn(AwardRegistrationContestCategory.FREE_KICK_GOAL);
            when(entry.getReviewStatus()).thenReturn(AwardRegistrationReviewStatus.APPROVED);
            when(entry.getMediaStatus()).thenReturn(AwardRegistrationMediaStatus.READY);
            when(entry.getReviewedAt()).thenReturn(reviewedAt);
            when(entry.getSourceType()).thenReturn(AwardRegistrationMediaSource.LINK);
            when(entry.getExternalUrl()).thenReturn("https://example.org/video/" + i);
            approved.add(entry);

            AwardRegistration registration = mock(AwardRegistration.class);
            when(registration.getId()).thenReturn(registrationId);
            when(registration.getEditionId()).thenReturn(editionId);
            when(registration.getEventId()).thenReturn(908L);
            when(registration.getDivisionId()).thenReturn(3L);
            when(registration.getCategoryId()).thenReturn(1L);
            when(registration.getGender()).thenReturn(AwardRegistrationGender.MALE);
            when(registration.getStatus()).thenReturn(AwardRegistrationStatus.SUBMITTED);
            when(registration.getAthleteName()).thenReturn("Atleta " + i);
            when(registration.getTeamName()).thenReturn("Time " + i);
            submitted.add(registration);
        }
        when(finalistQueries.finalists(editionId, 908, 3, 1, CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(published);
        when(finalistQueries.allowsFourFinalists(editionId, 908, 3, 1, "MALE", "FREE_KICK_GOAL"))
                .thenReturn(fourAllowed);
        when(entries.findAllById(any())).thenReturn(approved);
        when(registrations.findAllById(any())).thenReturn(submitted);
        when(votes.tally(editionId, 908, 3, 1, CONTEXT.gender(), CONTEXT.contestCategory()))
                .thenReturn(List.of(new Object[] { published.get(0).getEntryId(), 5L },
                        new Object[] { published.get(1).getEntryId(), 2L }));
        return editionId;
    }

    @Test
    void publicBoardNeverExposesVotesAfterClosing() {
        UUID editionId = prepareBoard(true, true);
        AwardPopularBoardResponse response = service().board(editionId, CONTEXT);
        assertEquals("CLOSED", response.status());
        assertEquals(4, response.finalists().size());
        assertNull(response.totalVotes());
        response.finalists().forEach(finalist -> assertNull(finalist.votes()));
        verify(votes, never()).tally(any(), anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void administrativeBoardShowsFinalCountsOnlyAfterClosing() {
        UUID editionId = prepareBoard(true, true);
        AwardPopularBoardResponse response = service().adminResults(editionId, CONTEXT);
        assertEquals("CLOSED", response.status());
        assertEquals(7L, response.totalVotes());
        assertEquals(List.of(5L, 2L, 0L, 0L),
                response.finalists().stream().map(AwardPopularFinalistResponse::votes).toList());
        verify(votes).tally(editionId, 908, 3, 1, CONTEXT.gender(), CONTEXT.contestCategory());
    }

    @Test
    void noCountsAreReturnedWhileVotingIsOpenEvenForAdministrators() {
        UUID editionId = prepareBoard(false, false);
        AwardPopularBoardResponse publicBoard = service().board(editionId, CONTEXT);
        AwardPopularBoardResponse adminBoard = service().adminResults(editionId, CONTEXT);
        assertEquals("OPEN", publicBoard.status());
        assertEquals("OPEN", adminBoard.status());
        assertNull(publicBoard.totalVotes());
        assertNull(adminBoard.totalVotes());
        adminBoard.finalists().forEach(finalist -> assertNull(finalist.votes()));
        verify(votes, never()).tally(any(), anyLong(), anyLong(), anyLong(), any(), any());
    }
}
