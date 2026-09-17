package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CoachVotingContextResponse(
        UUID editionId,
        String editionSlug,
        String editionName,
        int season,
        String state,
        Instant votingOpensAt,
        Instant votingClosesAt,
        UUID voterId,
        String coachName,
        long eventId,
        long divisionId,
        long categoryId,
        String representedTeamId,
        String representedTeamName,
        boolean submitted,
        List<CoachVotingCategoryResponse> voteCategories,
        List<CoachVotingTeamResponse> teams
) {
}
