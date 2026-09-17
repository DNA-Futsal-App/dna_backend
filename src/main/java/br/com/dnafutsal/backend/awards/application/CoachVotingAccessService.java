package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.CoachVotingState;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CoachVotingAccessService {

    private final CurrentUserService currentUser;
    private final AwardCoachVoterRepository voters;
    private final AwardEditionRepository editions;
    private final AwardCandidateRepository candidates;
    private final AwardBallotRepository ballots;
    private final Clock clock;

    public CoachVotingAccessService(
            CurrentUserService currentUser,
            AwardCoachVoterRepository voters,
            AwardEditionRepository editions,
            AwardCandidateRepository candidates,
            AwardBallotRepository ballots,
            Clock clock
    ) {
        this.currentUser = currentUser;
        this.voters = voters;
        this.editions = editions;
        this.candidates = candidates;
        this.ballots = ballots;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Access current() {
        UUID userId =
                currentUser.userId();

        List<AwardCoachVoter> bindings =
                voters.findByUserIdOrderByCreatedAtDesc(
                        userId
                );

        if (bindings.isEmpty()) {
            throw Errors.forbidden(
                    "COACH_VOTER_REQUIRED",
                    "Esta conta não possui acesso à votação de treinadores."
            );
        }

        Instant now =
                clock.instant();

        AwardCoachVoter voter =
                selectBinding(
                        bindings,
                        now
                );

        AwardEdition edition =
                edition(
                        voter.getEditionId()
                );

        AwardCandidate coach =
                candidates.findByIdAndEditionId(
                                voter.getSelfCoachCandidateId(),
                                edition.getId()
                        )
                        .orElseThrow(() -> Errors.conflict(
                                "COACH_VOTER_INCONSISTENT",
                                "O treinador vinculado a esta conta não foi encontrado."
                        ));

        boolean submitted =
                ballots.existsByEditionIdAndVoterUserId(
                        edition.getId(),
                        userId
                );

        return new Access(
                userId,
                voter,
                edition,
                coach,
                submitted,
                state(
                        edition,
                        submitted,
                        now
                ),
                now
        );
    }

    @Transactional(readOnly = true)
    public Access requireOpenForVoting() {
        Access access =
                current();

        if (access.submitted()) {
            throw Errors.conflict(
                    "AWARD_BALLOT_ALREADY_SUBMITTED",
                    "Seu voto já foi registrado e não pode ser alterado."
            );
        }

        if (!access.edition()
                .isVotingOpenAt(
                        access.now()
                )) {
            throw Errors.conflict(
                    "AWARD_VOTING_NOT_OPEN",
                    "A votação não está aberta neste momento."
            );
        }

        return access;
    }

    private AwardCoachVoter selectBinding(
            List<AwardCoachVoter> bindings,
            Instant now
    ) {
        for (AwardCoachVoter voter : bindings) {
            AwardEdition edition =
                    edition(
                            voter.getEditionId()
                    );

            if (edition.isVotingOpenAt(
                    now
            )) {
                return voter;
            }
        }

        for (AwardCoachVoter voter : bindings) {
            AwardEdition edition =
                    edition(
                            voter.getEditionId()
                    );

            if (edition.getStatus()
                    != AwardEditionStatus.CLOSED) {
                return voter;
            }
        }

        return bindings.get(0);
    }

    private AwardEdition edition(
            UUID editionId
    ) {
        return editions.findById(
                        editionId
                )
                .orElseThrow(() -> Errors.conflict(
                        "COACH_VOTER_INCONSISTENT",
                        "A edição vinculada a esta conta não foi encontrada."
                ));
    }

    private CoachVotingState state(
            AwardEdition edition,
            boolean submitted,
            Instant now
    ) {
        if (submitted) {
            return CoachVotingState.SUBMITTED;
        }

        if (edition.getStatus()
                == AwardEditionStatus.DRAFT) {
            return CoachVotingState.DRAFT;
        }

        if (edition.getStatus()
                == AwardEditionStatus.CLOSED) {
            return CoachVotingState.CLOSED;
        }

        if (edition.getVotingOpensAt() != null
                && now.isBefore(
                        edition.getVotingOpensAt()
                )) {
            return CoachVotingState.SCHEDULED;
        }

        if (edition.getVotingClosesAt() != null
                && !now.isBefore(
                        edition.getVotingClosesAt()
                )) {
            return CoachVotingState.CLOSED;
        }

        return CoachVotingState.OPEN;
    }

    public record Access(
            UUID userId,
            AwardCoachVoter voter,
            AwardEdition edition,
            AwardCandidate coach,
            boolean submitted,
            CoachVotingState state,
            Instant now
    ) {
    }
}
