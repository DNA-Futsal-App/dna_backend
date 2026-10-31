package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardPreVoteContextRequest;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardPreVoteFinalist;
import br.com.dnafutsal.backend.awards.domain.AwardPreVoteScore;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteEditionLockRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteFinalistRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteQueryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteScoreRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwardPreVoteServiceTest {
    @Mock CurrentUserService currentUser;
    @Mock AwardEditionRepository editions;
    @Mock AwardPreVoteEditionLockRepository editionLocks;
    @Mock AwardRegistrationRepository registrations;
    @Mock AwardRegistrationEntryRepository entries;
    @Mock AwardPreVoteQueryRepository queries;
    @Mock AwardPreVoteScoreRepository scores;
    @Mock AwardPreVoteFinalistRepository finalists;
    @Mock AwardEdition edition;

    private AwardPreVoteService service;
    private final UUID editionId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final AwardPreVoteContextRequest context = new AwardPreVoteContextRequest(
            908, 3, 12, AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEST_DRIBBLE);

    @BeforeEach
    void prepare() {
        service = new AwardPreVoteService(currentUser, editions, editionLocks, registrations, entries,
                queries, scores, finalists, Clock.systemUTC());
    }

    private List<AwardRegistrationEntry> prepareEntries(int... grades) {
        List<AwardRegistrationEntry> approved = new ArrayList<>();
        List<AwardPreVoteScore> evaluations = new ArrayList<>();
        for (int grade : grades) {
            AwardRegistrationEntry entry = new AwardRegistrationEntry(UUID.randomUUID(),
                    AwardRegistrationContestCategory.BEST_DRIBBLE,
                    AwardRegistrationMediaSource.LINK, "https://example.com/video");
            entry.approveReview(adminId, Instant.now());
            approved.add(entry);
            evaluations.add(new AwardPreVoteScore(entry.getId(), adminId, entry.getReviewedAt(), grade));
        }
        when(editionLocks.lockEdition(editionId)).thenReturn(Optional.of(edition));
        when(queries.findApproved(editionId, 908, 3, 12,
                AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEST_DRIBBLE))
                .thenReturn(approved);
        lenient().when(scores.findByEntryIdIn(anyCollection())).thenReturn(evaluations);
        when(finalists.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategoryOrderByRankAsc(
                editionId, 908, 3, 12, AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEST_DRIBBLE))
                .thenReturn(List.of());
        return approved;
    }

    @Test
    void rejectsOutOfRangeScore() {
        assertThatThrownBy(() -> service.score(editionId, UUID.randomUUID(), 0))
                .hasMessageContaining("entre 1 e 10");
        verifyNoInteractions(editionLocks, scores);
    }

    @Test
    void rejectsTieAtThirdPlace() {
        prepareEntries(10, 9, 8, 8);
        assertThatThrownBy(() -> service.finalizeContext(editionId, context))
                .hasMessageContaining("empate");
        verify(finalists, never()).save(any());
    }

    @Test
    void publishesOnlyThreeBestCandidatesWithAverageSnapshot() {
        List<AwardRegistrationEntry> approved = prepareEntries(8, 10, 7, 9);
        when(currentUser.userId()).thenReturn(adminId);
        when(edition.getId()).thenReturn(editionId);
        List<AwardRegistration> registrationMocks = new ArrayList<>();
        for (int index = 0; index < approved.size(); index++) {
            AwardRegistration registration = mock(AwardRegistration.class);
            when(registration.getId()).thenReturn(approved.get(index).getRegistrationId());
            when(registration.getRegistrationNumber()).thenReturn(index + 1L);
            lenient().when(registration.getAthleteName()).thenReturn("Atleta " + index);
            registrationMocks.add(registration);
        }
        when(registrations.findAllById(any())).thenReturn(registrationMocks);
        service.finalizeContext(editionId, context);

        ArgumentCaptor<AwardPreVoteFinalist> capture = ArgumentCaptor.forClass(AwardPreVoteFinalist.class);
        verify(finalists, times(3)).save(capture.capture());
        List<AwardPreVoteFinalist> saved = capture.getAllValues();
        assertThat(saved).extracting(AwardPreVoteFinalist::getEntryId).containsExactly(
                approved.get(1).getId(), approved.get(3).getId(), approved.get(0).getId());
        assertThat(saved).extracting(AwardPreVoteFinalist::getRank).containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    void rejectsWhenOneVideoDoesNotHaveSameReviewPanel() {
        List<AwardRegistrationEntry> approved = prepareEntries(10, 9, 8);
        List<AwardPreVoteScore> uneven = List.of(
                new AwardPreVoteScore(approved.get(0).getId(), adminId, approved.get(0).getReviewedAt(), 10),
                new AwardPreVoteScore(approved.get(1).getId(), UUID.randomUUID(), approved.get(1).getReviewedAt(), 9),
                new AwardPreVoteScore(approved.get(2).getId(), adminId, approved.get(2).getReviewedAt(), 8));
        when(scores.findByEntryIdIn(anyCollection())).thenReturn(uneven);
        assertThatThrownBy(() -> service.finalizeContext(editionId, context))
                .hasMessageContaining("mesmo conjunto");
        verify(finalists, never()).save(any());
    }
}
