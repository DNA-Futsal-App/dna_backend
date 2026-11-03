package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardPopularContext;
import br.com.dnafutsal.backend.awards.api.AwardPopularVoteRequest;
import br.com.dnafutsal.backend.awards.domain.AwardPopularVotingWindow;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AwardPopularVotingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void mustRejectVoteBeforeWindowOpensWithoutTouchingAccounts() {
        var windows = mock(AwardPopularVotingWindowRepository.class);
        var users = mock(CurrentUserService.class);
        var service = new AwardPopularVotingService(
                mock(AwardEditionRepository.class), mock(AwardPreVoteEditionLockRepository.class), windows,
                mock(AwardPopularVotingFinalistRepository.class), mock(AwardPopularVoteRepository.class),
                mock(AwardRegistrationEntryRepository.class), mock(AwardRegistrationRepository.class),
                users, mock(OracleAwardVideoStorage.class), Clock.fixed(NOW, ZoneOffset.UTC));
        UUID editionId = UUID.randomUUID();
        when(windows.lockForVoting(editionId)).thenReturn(Optional.of(
                new AwardPopularVotingWindow(editionId, NOW.plusSeconds(60), NOW.plusSeconds(3600))));
        var request = new AwardPopularVoteRequest(
                new AwardPopularContext(1, 2, 3, AwardRegistrationGender.MALE,
                        AwardRegistrationContestCategory.BEAUTIFUL_GOAL), UUID.randomUUID());
        assertThrows(BusinessException.class, () -> service.vote(editionId, request));
        verifyNoInteractions(users);
    }
    @Test
    void duplicateVoteFromRepositoryIsRejectedAndReapprovedVideoCannotBeVoted() {
        var windows = mock(AwardPopularVotingWindowRepository.class);
        var finalists = mock(AwardPopularVotingFinalistRepository.class);
        var voteRepository = mock(AwardPopularVoteRepository.class);
        var entryRepository = mock(AwardRegistrationEntryRepository.class);
        var registrationRepository = mock(AwardRegistrationRepository.class);
        var user = mock(CurrentUserService.class);
        var service = new AwardPopularVotingService(
                mock(AwardEditionRepository.class), mock(AwardPreVoteEditionLockRepository.class), windows, finalists, voteRepository,
                entryRepository, registrationRepository, user,
                mock(OracleAwardVideoStorage.class), Clock.fixed(NOW, ZoneOffset.UTC));
        UUID editionId = UUID.randomUUID();
        UUID voterId = UUID.randomUUID();
        var context = new AwardPopularContext(1, 2, 3, AwardRegistrationGender.MALE,
                AwardRegistrationContestCategory.BEAUTIFUL_GOAL);
        var window = new AwardPopularVotingWindow(editionId, NOW.minusSeconds(10), NOW.plusSeconds(3600));
        when(windows.lockForVoting(editionId)).thenReturn(Optional.of(window));

        var listedFinalists = new java.util.ArrayList<br.com.dnafutsal.backend.awards.domain.AwardPreVoteFinalist>();
        var listedEntries = new java.util.ArrayList<br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry>();
        var listedRegistrations = new java.util.ArrayList<br.com.dnafutsal.backend.awards.domain.AwardRegistration>();
        for (int i = 1; i <= 3; i++) {
            UUID entryId = UUID.randomUUID();
            UUID registrationId = UUID.randomUUID();
            var finalist = mock(br.com.dnafutsal.backend.awards.domain.AwardPreVoteFinalist.class);
            when(finalist.getEntryId()).thenReturn(entryId);
            when(finalist.getRank()).thenReturn((short) i);
            var approval = NOW.minusSeconds(3600);
            when(finalist.getApprovedAt()).thenReturn(approval);
            listedFinalists.add(finalist);

            var entry = mock(br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry.class);
            when(entry.getId()).thenReturn(entryId);
            when(entry.getRegistrationId()).thenReturn(registrationId);
            when(entry.getReviewStatus()).thenReturn(br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus.APPROVED);
            when(entry.getReviewedAt()).thenReturn(NOW.minusSeconds(3600));
            when(entry.getMediaStatus()).thenReturn(br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus.READY);
            when(entry.getContestCategory()).thenReturn(context.contestCategory());
            listedEntries.add(entry);

            var registration = mock(br.com.dnafutsal.backend.awards.domain.AwardRegistration.class);
            when(registration.getId()).thenReturn(registrationId);
            when(registration.getEditionId()).thenReturn(editionId);
            when(registration.getEventId()).thenReturn(context.eventId());
            when(registration.getDivisionId()).thenReturn(context.divisionId());
            when(registration.getCategoryId()).thenReturn(context.categoryId());
            when(registration.getGender()).thenReturn(context.gender());
            when(registration.getStatus()).thenReturn(br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus.SUBMITTED);
            listedRegistrations.add(registration);
        }
        when(finalists.finalists(editionId, 1, 2, 3, context.gender(), context.contestCategory()))
                .thenReturn(listedFinalists);
        when(entryRepository.findAllById(any())).thenReturn(listedEntries);
        when(registrationRepository.findAllById(any())).thenReturn(listedRegistrations);
        when(user.userId()).thenReturn(voterId);
        var candidateId = listedFinalists.get(0).getEntryId();
        when(voteRepository.insertIfAbsent(any(), any(), anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.vote(editionId,
                new AwardPopularVoteRequest(context, candidateId)));
        verify(voteRepository, times(1)).insertIfAbsent(any(), any(), anyLong(), anyLong(),
                anyLong(), anyString(), anyString(), any(), any());

        // Reaprovação do vídeo após a pré-votação não pode aproveitar a classificação antiga.
        clearInvocations(voteRepository);
        when(listedFinalists.get(0).getApprovedAt()).thenReturn(NOW.minusSeconds(7200));
        assertThrows(BusinessException.class, () -> service.vote(editionId,
                new AwardPopularVoteRequest(context, candidateId)));
        verifyNoInteractions(voteRepository);
    }

}
