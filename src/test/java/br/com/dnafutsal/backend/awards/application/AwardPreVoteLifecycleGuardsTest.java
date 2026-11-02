package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteEditionLockRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteFinalistRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.application.MailTemplateFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwardPreVoteLifecycleGuardsTest {
    @Mock AwardEditionRepository editions;
    @Mock AwardPreVoteEditionLockRepository editionLocks;
    @Mock AwardPreVoteFinalistRepository finalists;
    @Mock AwardVoteCategoryRepository voteCategories;
    @Mock AwardCandidateRepository candidates;
    @Mock AwardCoachVoterRepository voters;
    @Mock AwardEdition edition;
    @Mock CurrentUserService currentUser;
    @Mock UserAccountRepository users;
    @Mock AwardRegistrationRepository registrations;
    @Mock AwardRegistrationEntryRepository entries;
    @Mock OracleAwardVideoStorage storage;
    @Mock MailTemplateFactory mailTemplates;
    @Mock MailOutboxService mailOutbox;
    @Mock AwardRegistration registration;
    @Mock AwardRegistrationEntry entry;

    @Test
    void doesNotReopenRegistrationsAfterAnyFinalistsAreConfirmed() {
        UUID editionId = UUID.randomUUID();
        when(editionLocks.lockEdition(editionId)).thenReturn(Optional.of(edition));
        when(finalists.existsByEditionId(editionId)).thenReturn(true);

        var service = new AwardEditionAdminService(editions, editionLocks, finalists,
                voteCategories, candidates, voters, Clock.systemUTC());

        assertThatThrownBy(() -> service.openRegistrations(editionId))
                .hasMessageContaining("Não é possível reabrir inscrições");
        verify(edition, never()).openRegistrations(any());
    }

    @Test
    void doesNotApproveEntriesBelongingToAlreadyConfirmedContext() {
        UUID editionId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(registrations.findById(registrationId)).thenReturn(Optional.of(registration));
        when(registration.getStatus()).thenReturn(AwardRegistrationStatus.SUBMITTED);
        when(registration.getEditionId()).thenReturn(editionId);
        when(registration.getEventId()).thenReturn(908L);
        when(registration.getDivisionId()).thenReturn(3L);
        when(registration.getCategoryId()).thenReturn(12L);
        when(registration.getGender()).thenReturn(AwardRegistrationGender.MALE);
        when(currentUser.userId()).thenReturn(adminId);
        when(editionLocks.lockEdition(editionId)).thenReturn(Optional.of(edition));
        when(entries.findByIdAndRegistrationId(entryId, registrationId)).thenReturn(Optional.of(entry));
        when(entry.getContestCategory()).thenReturn(AwardRegistrationContestCategory.BEST_DRIBBLE);
        when(finalists.existsByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, 908L, 3L, 12L,
                AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEST_DRIBBLE))
                .thenReturn(true);

        var service = new AwardRegistrationReviewService(currentUser, users, editions,
                editionLocks, finalists, registrations, entries, storage, mailTemplates,
                mailOutbox, Clock.systemUTC());

        assertThatThrownBy(() -> service.approve(registrationId, entryId))
                .hasMessageContaining("finalistas já confirmados");
        verify(entries, never()).saveAndFlush(any());
    }
}
