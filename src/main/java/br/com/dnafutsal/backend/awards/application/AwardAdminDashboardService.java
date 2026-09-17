package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardAdminCoachResponse;
import br.com.dnafutsal.backend.awards.api.AwardAdminOverviewResponse;
import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInvite;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInviteStatus;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachInviteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AwardAdminDashboardService {

    private final AwardEditionRepository editions;
    private final AwardCandidateRepository candidates;
    private final AwardCoachInviteRepository invites;
    private final AwardCoachVoterRepository voters;
    private final AwardBallotRepository ballots;
    private final UserAccountRepository users;
    private final Clock clock;

    public AwardAdminDashboardService(
            AwardEditionRepository editions,
            AwardCandidateRepository candidates,
            AwardCoachInviteRepository invites,
            AwardCoachVoterRepository voters,
            AwardBallotRepository ballots,
            UserAccountRepository users,
            Clock clock
    ) {
        this.editions = editions;
        this.candidates = candidates;
        this.invites = invites;
        this.voters = voters;
        this.ballots = ballots;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AwardAdminOverviewResponse overview(
            UUID editionId
    ) {
        AwardEdition edition = edition(editionId);

        List<AwardCandidate> active =
                candidates.findByEditionIdOrderByTeamNameAscNameAsc(
                                editionId
                        )
                        .stream()
                        .filter(AwardCandidate::isActive)
                        .toList();

        List<AwardAdminCoachResponse> coaches =
                coaches(editionId);

        int athleteCount =
                (int) active.stream()
                        .filter(candidate ->
                                candidate.getCandidateType()
                                        == AwardCandidateType.ATHLETE
                        )
                        .count();

        int coachCount =
                (int) active.stream()
                        .filter(candidate ->
                                candidate.getCandidateType()
                                        == AwardCandidateType.COACH
                        )
                        .count();

        int positionsPending =
                (int) active.stream()
                        .filter(candidate ->
                                candidate.getCandidateType()
                                        == AwardCandidateType.ATHLETE
                        )
                        .filter(candidate ->
                                candidate.getPositionCode() == null
                                        || candidate.getPositionCode().isBlank()
                        )
                        .count();

        int teamCount =
                (int) active.stream()
                        .map(AwardCandidate::getTeamId)
                        .distinct()
                        .count();

        List<AwardCoachVoter> editionVoters =
                voters.findByEditionId(editionId);

        List<AwardBallot> editionBallots =
                ballots.findByEditionId(editionId);

        return new AwardAdminOverviewResponse(
                edition.getId(),
                edition.getName(),
                edition.getSeason(),
                edition.getStatus().name(),
                edition.getVotingOpensAt(),
                edition.getVotingClosesAt(),
                active.size(),
                athleteCount,
                coachCount,
                teamCount,
                positionsPending,
                countState(coaches, "NOT_INVITED"),
                countState(coaches, "INVITED"),
                countState(coaches, "RESERVED")
                        + countState(coaches, "RESERVATION_EXPIRED"),
                countState(coaches, "REGISTERED"),
                countState(coaches, "VOTED"),
                editionVoters.size(),
                editionBallots.size(),
                Math.max(
                        0,
                        editionVoters.size() - editionBallots.size()
                )
        );
    }

    @Transactional(readOnly = true)
    public List<AwardAdminCoachResponse> coaches(
            UUID editionId
    ) {
        edition(editionId);

        Instant now = clock.instant();

        List<AwardCandidate> coachCandidates =
                candidates.findByEditionIdOrderByTeamNameAscNameAsc(
                                editionId
                        )
                        .stream()
                        .filter(candidate ->
                                candidate.getCandidateType()
                                        == AwardCandidateType.COACH
                        )
                        .toList();

        Map<UUID, AwardCoachInvite> latestInviteByCoach =
                new LinkedHashMap<>();

        invites.findByEditionIdOrderByCreatedAtDesc(editionId)
                .forEach(invite ->
                        latestInviteByCoach.putIfAbsent(
                                invite.getCoachCandidateId(),
                                invite
                        )
                );

        Map<UUID, AwardCoachVoter> voterByCoach =
                voters.findByEditionId(editionId)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        AwardCoachVoter::getSelfCoachCandidateId,
                                        Function.identity(),
                                        (first, second) -> first
                                )
                        );

        Map<UUID, AwardBallot> ballotByUser =
                ballots.findByEditionId(editionId)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        AwardBallot::getVoterUserId,
                                        Function.identity(),
                                        (first, second) -> first
                                )
                        );

        Set<UUID> userIds =
                voterByCoach.values()
                        .stream()
                        .map(AwardCoachVoter::getUserId)
                        .collect(Collectors.toSet());

        Map<UUID, UserAccount> userById =
                users.findAllById(userIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        UserAccount::getId,
                                        Function.identity()
                                )
                        );

        return coachCandidates.stream()
                .map(coach -> {
                    AwardCoachInvite invite =
                            latestInviteByCoach.get(coach.getId());

                    AwardCoachVoter voter =
                            voterByCoach.get(coach.getId());

                    UserAccount user =
                            voter == null
                                    ? null
                                    : userById.get(voter.getUserId());

                    AwardBallot ballot =
                            voter == null
                                    ? null
                                    : ballotByUser.get(voter.getUserId());

                    return new AwardAdminCoachResponse(
                            coach.getId(),
                            coach.getName(),
                            coach.getTeamId(),
                            coach.getTeamName(),
                            coach.getTeamLogoUrl(),
                            coach.getEventId(),
                            coach.getDivisionId(),
                            coach.getCategoryId(),
                            coach.isActive(),
                            state(
                                    coach,
                                    invite,
                                    voter,
                                    ballot,
                                    now
                            ),
                            invite == null ? null : invite.getId(),
                            invite == null ? null : invite.getExpiresAt(),
                            invite == null ? null : invite.getReservedAt(),
                            invite == null ? null : invite.getReservationExpiresAt(),
                            invite == null ? null : invite.getClaimedAt(),
                            voter == null ? null : voter.getUserId(),
                            user == null ? null : user.getName(),
                            user == null ? null : user.getEmail(),
                            voter == null ? null : voter.getCreatedAt(),
                            ballot == null ? null : ballot.getSubmittedAt()
                    );
                })
                .sorted(
                        Comparator
                                .comparing(
                                        AwardAdminCoachResponse::teamName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        AwardAdminCoachResponse::coachName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .toList();
    }

    private String state(
            AwardCandidate coach,
            AwardCoachInvite invite,
            AwardCoachVoter voter,
            AwardBallot ballot,
            Instant now
    ) {
        if (!coach.isActive()) {
            return "INACTIVE";
        }

        if (ballot != null) {
            return "VOTED";
        }

        if (voter != null) {
            return "REGISTERED";
        }

        if (invite == null) {
            return "NOT_INVITED";
        }

        if (invite.getStatus() == AwardCoachInviteStatus.CLAIMED) {
            return "CLAIMED";
        }

        if (invite.getStatus() == AwardCoachInviteStatus.REVOKED) {
            return "REVOKED";
        }

        if (invite.hasActiveReservationAt(now)) {
            return "RESERVED";
        }

        if (invite.hasExpiredReservationAt(now)) {
            return "RESERVATION_EXPIRED";
        }

        if (invite.isExpiredAt(now)) {
            return "EXPIRED";
        }

        return "INVITED";
    }

    private int countState(
            List<AwardAdminCoachResponse> coaches,
            String state
    ) {
        return (int) coaches.stream()
                .filter(coach ->
                        state.equals(coach.accessState())
                )
                .count();
    }

    private AwardEdition edition(
            UUID editionId
    ) {
        return editions.findById(editionId)
                .orElseThrow(() -> Errors.notFound(
                        "AWARD_EDITION_NOT_FOUND",
                        "Edição do prêmio não encontrada."
                ));
    }
}
