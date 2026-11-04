package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.identity.domain.*;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwardPreVoteAssessmentServiceTest {
    private final AwardEditionRepository editions = mock(AwardEditionRepository.class);
    private final AwardPreVoteQueryRepository queries = mock(AwardPreVoteQueryRepository.class);
    private final AwardPreVoteScoreRepository scores = mock(AwardPreVoteScoreRepository.class);
    private final AwardActiveAdministratorRepository admins = mock(AwardActiveAdministratorRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AwardPreVoteAssessmentService service = new AwardPreVoteAssessmentService(
            editions, queries, scores, admins, users);

    private static final AwardPreVoteContextRequest CONTEXT = new AwardPreVoteContextRequest(
            908, 3, 1, AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEAUTIFUL_GOAL);
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Test
    void keepsPartiallyScoredAndUnscoredEntriesAndSortsByCurrentAverage() {
        UUID editionId = UUID.randomUUID();
        when(editions.existsById(editionId)).thenReturn(true);
        UserAccount admin1 = account("Admin 1");
        UserAccount admin2 = account("Admin 2");
        UserAccount responsible = account("Responsável");
        when(admins.findByRoleAndStatusOrderByNameAsc(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin1, admin2));
        when(users.findAllById(any())).thenReturn(List.of(responsible));

        AwardRegistration first = registration(editionId, responsible.getId(), "Atleta sem notas");
        AwardRegistration second = registration(editionId, responsible.getId(), "Atleta com nota");
        AwardRegistrationEntry firstEntry = approved(first.getId(), admin1.getId());
        AwardRegistrationEntry secondEntry = approved(second.getId(), admin1.getId());
        when(queries.findAssessmentEntries(editionId, 908, 3, 1,
                AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEAUTIFUL_GOAL))
                .thenReturn(List.of(new Object[]{first, firstEntry}, new Object[]{second, secondEntry}));
        when(scores.findByEntryIdIn(any())).thenReturn(List.of(
                new AwardPreVoteScore(secondEntry.getId(), admin1.getId(), secondEntry.getReviewedAt(), 9),
                new AwardPreVoteScore(secondEntry.getId(), admin2.getId(), NOW.minusSeconds(5), 3)));

        AwardPreVoteAssessmentReportResponse report = service.report(editionId, CONTEXT);
        assertEquals(2, report.totalEntries());
        assertEquals("Atleta com nota", report.entries().get(0).athleteName());
        assertEquals("9.0000", report.entries().get(0).averageScore().toPlainString());
        assertEquals(1, report.entries().get(0).scoredAdministrators());
        assertEquals(2, report.entries().get(0).activeAdministrators());
        assertFalse(report.entries().get(0).allAdministratorsScored());
        assertNull(report.entries().get(1).averageScore());
        assertEquals("Responsável", report.entries().get(0).representativeName());
    }

    @Test
    void marksCompleteOnlyWhenEveryActiveAdministratorScoredCurrentApproval() {
        UUID editionId = UUID.randomUUID();
        when(editions.existsById(editionId)).thenReturn(true);
        UserAccount admin1 = account("Admin 1");
        UserAccount admin2 = account("Admin 2");
        UserAccount responsible = account("Responsável");
        when(admins.findByRoleAndStatusOrderByNameAsc(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin1, admin2));
        when(users.findAllById(any())).thenReturn(List.of(responsible));
        AwardRegistration registration = registration(editionId, responsible.getId(), "Atleta");
        AwardRegistrationEntry entry = approved(registration.getId(), admin1.getId());
        when(queries.findAssessmentEntries(editionId, 908, 3, 1,
                AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEAUTIFUL_GOAL))
                .thenReturn(List.<Object[]>of(new Object[]{registration, entry}));
        when(scores.findByEntryIdIn(any())).thenReturn(List.of(
                new AwardPreVoteScore(entry.getId(), admin1.getId(), entry.getReviewedAt(), 9),
                new AwardPreVoteScore(entry.getId(), admin2.getId(), entry.getReviewedAt(), 7)));

        AwardPreVoteAssessmentEntryResponse athlete = service.report(editionId, CONTEXT).entries().get(0);
        assertEquals("8.0000", athlete.averageScore().toPlainString());
        assertTrue(athlete.allAdministratorsScored());
        assertEquals(2, athlete.scoredAdministrators());
    }

    @Test
    void includesSubmittedEntryAwaitingApprovalWithoutUsingHistoricalScores() {
        UUID editionId = UUID.randomUUID();
        when(editions.existsById(editionId)).thenReturn(true);
        UserAccount admin = account("Admin");
        UserAccount responsible = account("Responsável");
        when(admins.findByRoleAndStatusOrderByNameAsc(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));
        when(users.findAllById(any())).thenReturn(List.of(responsible));
        AwardRegistration registration = registration(editionId, responsible.getId(), "Atleta pendente");
        AwardRegistrationEntry entry = new AwardRegistrationEntry(registration.getId(),
                AwardRegistrationContestCategory.BEAUTIFUL_GOAL,
                AwardRegistrationMediaSource.LINK, "https://example.org/video");
        when(queries.findAssessmentEntries(editionId, 908, 3, 1,
                AwardRegistrationGender.MALE, AwardRegistrationContestCategory.BEAUTIFUL_GOAL))
                .thenReturn(List.<Object[]>of(new Object[]{registration, entry}));
        when(scores.findByEntryIdIn(any())).thenReturn(List.of(
                new AwardPreVoteScore(entry.getId(), admin.getId(), NOW, 10)));

        AwardPreVoteAssessmentEntryResponse pending = service.report(editionId, CONTEXT).entries().get(0);
        assertEquals(AwardRegistrationReviewStatus.PENDING_REVIEW, pending.reviewStatus());
        assertNull(pending.averageScore());
        assertFalse(pending.allAdministratorsScored());
    }

    private static UserAccount account(String name) {
        return new UserAccount(name, "teste@example.com", "11999999999", "hash", null,
                null, null, null, null);
    }

    private static AwardRegistration registration(UUID edition, UUID responsible, String athlete) {
        AwardRegistration registration = new AwardRegistration(101, edition, responsible, "encrypted",
                athlete, "@atleta", "atleta", 908, 3, "A1", 1, "Sub-8",
                "1", "Time", AwardRegistrationGender.MALE);
        registration.submit(NOW);
        return registration;
    }

    private static AwardRegistrationEntry approved(UUID registrationId, UUID reviewerId) {
        AwardRegistrationEntry entry = new AwardRegistrationEntry(registrationId,
                AwardRegistrationContestCategory.BEAUTIFUL_GOAL,
                AwardRegistrationMediaSource.LINK, "https://example.org/video");
        entry.approveReview(reviewerId, NOW);
        return entry;
    }
}
