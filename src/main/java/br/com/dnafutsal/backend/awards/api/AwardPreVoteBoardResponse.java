package br.com.dnafutsal.backend.awards.api;

import java.util.List;

public record AwardPreVoteBoardResponse(
        List<AwardPreVoteEntryResponse> entries,
        long pendingReview,
        int approvedCount,
        int fullyScoredCount,
        int votersCount,
        boolean registrationsOpen,
        boolean published,
        boolean readyToFinalize
) {}
