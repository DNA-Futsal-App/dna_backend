package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardEditionAdminResponse;
import br.com.dnafutsal.backend.awards.api.AwardVoteCategoryResponse;
import br.com.dnafutsal.backend.awards.api.OpenAwardEditionRequest;
import br.com.dnafutsal.backend.awards.api.ResetAwardVotingResponse;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteEditionLockRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteFinalistRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AwardEditionAdminService {

    private final AwardEditionRepository editions;
    private final AwardPreVoteEditionLockRepository editionLocks;
    private final AwardPreVoteFinalistRepository finalists;
    private final AwardVoteCategoryRepository voteCategories;
    private final AwardCandidateRepository candidates;
    private final AwardCoachVoterRepository voters;
    private final AwardVotingResetService votingReset;
    private final Clock clock;

    public AwardEditionAdminService(
            AwardEditionRepository editions,
            AwardPreVoteEditionLockRepository editionLocks,
            AwardPreVoteFinalistRepository finalists,
            AwardVoteCategoryRepository voteCategories,
            AwardCandidateRepository candidates,
            AwardCoachVoterRepository voters,
            AwardVotingResetService votingReset,
            Clock clock
    ) {
        this.editions = editions;
        this.editionLocks = editionLocks;
        this.finalists = finalists;
        this.voteCategories = voteCategories;
        this.candidates = candidates;
        this.voters = voters;
        this.votingReset = votingReset;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AwardEditionAdminResponse> editions() {
        return editions
                .findAllByOrderBySeasonDescNameAsc()
                .stream()
                .map(
                        AwardEditionAdminResponse::from
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AwardVoteCategoryResponse> voteCategories(
            UUID editionId
    ) {
        if (!editions.existsById(
                editionId
        )) {
            throw Errors.notFound(
                    "AWARD_EDITION_NOT_FOUND",
                    "Edição do prêmio não encontrada."
            );
        }

        return voteCategories
                .findByEditionIdOrderByDisplayOrderAsc(
                        editionId
                )
                .stream()
                .map(
                        AwardVoteCategoryResponse::from
                )
                .toList();
    }

    @Transactional
    public AwardEditionAdminResponse open(
            UUID editionId,
            OpenAwardEditionRequest request
    ) {
        AwardEdition edition =
                edition(
                        editionId
                );

        if (edition.getStatus()
                != AwardEditionStatus.DRAFT) {
            throw Errors.conflict(
                    "AWARD_EDITION_NOT_DRAFT",
                    "Somente uma edição em rascunho pode ser aberta para votação."
            );
        }

        Instant now =
                clock.instant();

        if (!request.votingOpensAt()
                .isBefore(
                        request.votingClosesAt()
                )) {
            throw Errors.badRequest(
                    "AWARD_VOTING_WINDOW_INVALID",
                    "A abertura da votação deve ocorrer antes do encerramento."
            );
        }

        if (!request.votingClosesAt()
                .isAfter(
                        now
                )) {
            throw Errors.badRequest(
                    "AWARD_VOTING_WINDOW_INVALID",
                    "O encerramento da votação deve estar no futuro."
            );
        }

        List<AwardVoteCategory> categories =
                voteCategories.findByEditionIdOrderByDisplayOrderAsc(
                        editionId
                );

        if (categories.isEmpty()) {
            throw Errors.conflict(
                    "AWARD_VOTE_CATEGORIES_EMPTY",
                    "Cadastre as categorias de votação antes de abrir a edição."
            );
        }

        // Synthetic team candidates may have been deactivated by older syncs.
        // Restore them only when an active voter still references the same context.
        List<AwardCandidate> coachCandidates =
                candidates.findByEditionIdOrderByTeamNameAscNameAsc(editionId)
                        .stream()
                        .filter(candidate -> candidate.getCandidateType() == AwardCandidateType.COACH)
                        .toList();

        validateCoachBindings(editionId, coachCandidates);

        if (coachCandidates.stream().noneMatch(AwardCandidate::isActive)) {
            throw Errors.conflict(
                    "AWARD_COACHES_EMPTY",
                    "Não há candidatos técnicos ativos para esta edição."
            );
        }

        edition.openVoting(
                request.votingOpensAt(),
                request.votingClosesAt()
        );

        return AwardEditionAdminResponse.from(
                editions.saveAndFlush(
                        edition
                )
        );
    }

    /**
     * Reopens a finished voting window and discards ballots from its previous run.
     * Reset and subsequent opening share a single transaction. Any validation
     * failure rolls back the deletions and the edition state change.
     */
    @Transactional
    public ResetAwardVotingResponse reopen(UUID editionId, OpenAwardEditionRequest request) {
        AwardEdition edition = editionLocks.lockEdition(editionId)
                .orElseThrow(() -> Errors.notFound(
                        "AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
        Instant now = clock.instant();
        if (edition.getStatus() == AwardEditionStatus.DRAFT) {
            throw Errors.conflict("AWARD_EDITION_NOT_CLOSED",
                    "A edição está em rascunho. Utilize Abrir votação.");
        }
        if (edition.getStatus() == AwardEditionStatus.OPEN
                && (edition.getVotingClosesAt() == null
                    || now.isBefore(edition.getVotingClosesAt()))) {
            throw Errors.conflict("AWARD_VOTING_STILL_ACTIVE",
                    "Encerre a votação atual antes de reabri-la.");
        }
        if (!request.votingOpensAt().isBefore(request.votingClosesAt())
                || !request.votingClosesAt().isAfter(now)) {
            throw Errors.badRequest("AWARD_VOTING_WINDOW_INVALID",
                    "Informe um período válido, com encerramento no futuro.");
        }

        ResetAwardVotingResponse cleared = votingReset.reset(editionId);
        // Reuses existing snapshot and context validation; rollback is atomic.
        AwardEditionAdminResponse opened = open(editionId, request);
        return new ResetAwardVotingResponse(
                editionId, cleared.ballotsDeleted(), cleared.votesDeleted(), opened);
    }

    @Transactional
    public AwardEditionAdminResponse close(
            UUID editionId
    ) {
        AwardEdition edition =
                edition(
                        editionId
                );

        if (edition.getStatus()
                == AwardEditionStatus.CLOSED) {
            return AwardEditionAdminResponse.from(
                    edition
            );
        }

        if (edition.getStatus()
                != AwardEditionStatus.OPEN) {
            throw Errors.conflict(
                    "AWARD_EDITION_NOT_OPEN",
                    "Somente uma edição aberta pode ser encerrada."
            );
        }

        edition.closeVoting(
                clock.instant()
        );

        return AwardEditionAdminResponse.from(
                editions.saveAndFlush(
                        edition
                )
        );
    }

    @Transactional
    public AwardEditionAdminResponse openRegistrations(
            UUID editionId
    ) {
        AwardEdition edition = editionLocks.lockEdition(editionId)
                .orElseThrow(() -> Errors.notFound(
                        "AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));

        // Não reabrir inscrições depois que qualquer grupo já possui finalistas.
        if (finalists.existsByEditionId(editionId)) {
            throw Errors.conflict("AWARD_PRE_VOTE_ALREADY_FINALIZED",
                    "Não é possível reabrir inscrições após a confirmação de finalistas.");
        }

        if (edition.isRegistrationsOpen()) {
            return AwardEditionAdminResponse.from(
                    edition
            );
        }

        edition.openRegistrations(
                clock.instant()
        );

        return AwardEditionAdminResponse.from(
                editions.saveAndFlush(
                        edition
                )
        );
    }

    @Transactional
    public AwardEditionAdminResponse closeRegistrations(
            UUID editionId
    ) {
        AwardEdition edition =
                edition(
                        editionId
                );

        if (!edition.isRegistrationsOpen()) {
            return AwardEditionAdminResponse.from(
                    edition
            );
        }

        edition.closeRegistrations(
                clock.instant()
        );

        return AwardEditionAdminResponse.from(
                editions.saveAndFlush(
                        edition
                )
        );
    }

    private void validateCoachBindings(UUID editionId, List<AwardCandidate> coachCandidates) {
        java.util.Map<UUID, AwardCandidate> byId = coachCandidates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AwardCandidate::getId, candidate -> candidate));

        for (AwardCoachVoter voter : voters.findByEditionId(editionId)) {
            // Revoked contexts remain in the database for audit purposes.
            if (!voter.isActive()) {
                continue;
            }
            AwardCandidate coach = byId.get(voter.getSelfCoachCandidateId());
            boolean consistent = coach != null
                    && (coach.isActive() || coach.isTeamCoachVoteCandidate())
                    && coach.getEventId() == voter.getEventId()
                    && coach.getDivisionId() == voter.getDivisionId()
                    && coach.getCategoryId() == voter.getCategoryId()
                    && coach.getTeamId().equals(voter.getTeamId());

            if (!consistent) {
                throw Errors.conflict(
                        "AWARD_COACH_VOTER_INCONSISTENT",
                        "Existe treinador habilitado para votar que não corresponde mais ao snapshot."
                );
            }
            // Older syncs could deactivate TEAM_VOTE rows; restore only these.
            if (!coach.isActive()) {
                coach.reactivate();
            }
        }
    }

    private AwardEdition edition(
            UUID editionId
    ) {
        return editions.findById(
                        editionId
                )
                .orElseThrow(() -> Errors.notFound(
                        "AWARD_EDITION_NOT_FOUND",
                        "Edição do prêmio não encontrada."
                ));
    }
}
