package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardPopularContext;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.identity.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwardPopularReadinessServiceTest {
    @Test
    void listsAllActiveAdminsAndTheExactMissingVideos() {
        var editions = mock(AwardEditionRepository.class);
        var queries = mock(AwardPreVoteQueryRepository.class);
        var scores = mock(AwardPreVoteScoreRepository.class);
        var registrations = mock(AwardRegistrationRepository.class);
        var finalists = mock(AwardPopularVotingFinalistRepository.class);
        var admins = mock(AwardActiveAdministratorRepository.class);
        var service = new AwardPopularReadinessService(editions, queries, scores, registrations, finalists, admins);
        UUID edition = UUID.randomUUID();
        var context = new AwardPopularContext(1, 2, 3, AwardRegistrationGender.FEMALE,
                AwardRegistrationContestCategory.BEST_SAVE);
        when(editions.findById(edition)).thenReturn(java.util.Optional.of(mock(AwardEdition.class)));
        UUID mariaId = UUID.randomUUID();
        UUID joaoId = UUID.randomUUID();
        var maria = mock(UserAccount.class);
        var joao = mock(UserAccount.class);
        when(maria.getId()).thenReturn(mariaId);
        when(maria.getName()).thenReturn("Maria");
        when(maria.getEmail()).thenReturn("maria@exemplo.com");
        when(joao.getId()).thenReturn(joaoId);
        when(joao.getName()).thenReturn("João");
        when(joao.getEmail()).thenReturn("joao@exemplo.com");
        when(admins.findByRoleAndStatusOrderByNameAsc(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(joao, maria));
        var videoA = mock(AwardRegistrationEntry.class);
        var videoB = mock(AwardRegistrationEntry.class);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID ra = UUID.randomUUID(), rb = UUID.randomUUID();
        Instant approval = Instant.parse("2026-11-01T12:00:00Z");
        when(videoA.getId()).thenReturn(a);
        when(videoB.getId()).thenReturn(b);
        when(videoA.getRegistrationId()).thenReturn(ra);
        when(videoB.getRegistrationId()).thenReturn(rb);
        when(videoA.getReviewedAt()).thenReturn(approval);
        when(videoB.getReviewedAt()).thenReturn(approval);
        when(queries.findApproved(edition, 1, 2, 3, context.gender(), context.contestCategory()))
                .thenReturn(List.of(videoA, videoB));
        var registrationA = mock(AwardRegistration.class);
        var registrationB = mock(AwardRegistration.class);
        when(registrationA.getId()).thenReturn(ra);
        when(registrationB.getId()).thenReturn(rb);
        when(registrationA.getAthleteName()).thenReturn("Atleta A");
        when(registrationB.getAthleteName()).thenReturn("Atleta B");
        when(registrationA.getRegistrationNumber()).thenReturn(11L);
        when(registrationB.getRegistrationNumber()).thenReturn(12L);
        when(registrations.findAllById(any())).thenReturn(List.of(registrationA, registrationB));
        var valid = mock(AwardPreVoteScore.class);
        when(valid.getEntryId()).thenReturn(a);
        when(valid.getAdminUserId()).thenReturn(mariaId);
        when(valid.getApprovalAt()).thenReturn(approval);
        when(valid.getScore()).thenReturn(9);
        var outdated = mock(AwardPreVoteScore.class);
        when(outdated.getEntryId()).thenReturn(b);
        when(outdated.getAdminUserId()).thenReturn(joaoId);
        when(outdated.getApprovalAt()).thenReturn(approval.minusSeconds(60));
        when(scores.findByEntryIdIn(any())).thenReturn(List.of(valid, outdated));

        var result = service.assess(edition, context);
        assertFalse(result.ready());
        assertEquals(2, result.missingAdmins().size());
        var missingMaria = result.missingAdmins().stream().filter(v -> v.adminId().equals(mariaId)).findFirst().orElseThrow();
        var missingJoao = result.missingAdmins().stream().filter(v -> v.adminId().equals(joaoId)).findFirst().orElseThrow();
        assertEquals(List.of(b), missingMaria.missingVideos().stream().map(v -> v.entryId()).toList());
        assertEquals(2, missingJoao.missingVideos().size());
        assertTrue(result.blockers().contains("NOTAS_PENDENTES"));
    }
}
