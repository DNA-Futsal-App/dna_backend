package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardEditionAdminResponse;
import br.com.dnafutsal.backend.awards.api.AwardVoteCategoryResponse;
import br.com.dnafutsal.backend.awards.api.OpenAwardEditionRequest;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
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
    private final AwardVoteCategoryRepository voteCategories;
    private final AwardCandidateRepository candidates;
    private final AwardCoachVoterRepository voters;
    private final Clock clock;

    public AwardEditionAdminService(
            AwardEditionRepository editions,
            AwardVoteCategoryRepository voteCategories,
            AwardCandidateRepository candidates,
            AwardCoachVoterRepository voters,
            Clock clock
    ) {
        this.editions = editions;
        this.voteCategories = voteCategories;
        this.candidates = candidates;
        this.voters = voters;
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

        List<AwardCandidate> activeCoaches =
                candidates.findByEditionIdOrderByTeamNameAscNameAsc(
                                editionId
                        )
                        .stream()
                        .filter(AwardCandidate::isActive)
                        .filter(candidate ->
                                candidate.getCandidateType()
                                        == AwardCandidateType.COACH
                        )
                        .toList();

        if (activeCoaches.isEmpty()) {
            throw Errors.conflict(
                    "AWARD_COACHES_EMPTY",
                    "Sincronize os treinadores da divisão e categoria antes de abrir a votação."
            );
        }

        validateCoachBindings(
                editionId,
                activeCoaches
        );

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

    private void validateCoachBindings(
            UUID editionId,
            List<AwardCandidate> activeCandidates
    ) {
        java.util.Map<UUID, AwardCandidate> activeCoaches =
                activeCandidates.stream()
                        .filter(candidate ->
                                candidate.getCandidateType()
                                        == AwardCandidateType.COACH
                        )
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AwardCandidate::getId,
                                        candidate -> candidate
                                )
                        );

        for (AwardCoachVoter voter
                : voters.findByEditionId(
                        editionId
                )) {
            AwardCandidate coach =
                    activeCoaches.get(
                            voter.getSelfCoachCandidateId()
                    );

            boolean consistent =
                    coach != null
                            && coach.getEventId()
                            == voter.getEventId()
                            && coach.getDivisionId()
                            == voter.getDivisionId()
                            && coach.getCategoryId()
                            == voter.getCategoryId()
                            && coach.getTeamId()
                            .equals(
                                    voter.getTeamId()
                            );

            if (!consistent) {
                throw Errors.conflict(
                        "AWARD_COACH_VOTER_INCONSISTENT",
                        "Existe treinador habilitado para votar que não corresponde mais ao snapshot."
                );
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
