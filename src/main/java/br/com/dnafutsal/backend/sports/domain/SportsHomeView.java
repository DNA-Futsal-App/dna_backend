package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;
import java.util.List;

public record SportsHomeView(
        SportsHomeMode mode,

        long eventId,
        String competitionName,
        int season,
        String category,
        String division,

        String currentPhase,
        String standingGroup,

        TeamView team,
        StandingView teamStanding,

        MatchView latestMatch,
        MatchView nextMatch,

        int teamCount,
        int upcomingCount,

        List<StandingView> standings,
        List<TopScorerView> topScorers,
        List<MatchView> recentMatches
) implements Serializable {
}