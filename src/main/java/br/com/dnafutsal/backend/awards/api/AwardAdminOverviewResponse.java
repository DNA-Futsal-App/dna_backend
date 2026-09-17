package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.UUID;

public record AwardAdminOverviewResponse(
        UUID editionId,
        String editionName,
        int season,
        String status,
        Instant votingOpensAt,
        Instant votingClosesAt,
        int activeCandidates,
        int athletes,
        int coaches,
        int teams,
        int positionsPending,
        int coachesNotInvited,
        int coachesInvited,
        int coachesReserved,
        int coachesRegistered,
        int coachesVoted,
        int votersRegistered,
        int ballotsSubmitted,
        int ballotsPending
) {
}
