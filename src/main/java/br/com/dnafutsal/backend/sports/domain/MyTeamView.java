package br.com.dnafutsal.backend.sports.domain;

import br.com.dnafutsal.backend.sports.domain.*;

import java.io.Serializable;
import java.util.List;

public record MyTeamView(
        boolean configured,

        MyTeamState state,

        TeamView team,

        long eventId,
        String competitionName,
        int season,
        String category,
        String division,
        String currentPhase,

        SportsScheduleState scheduleState,

        StandingView standing,

        MatchView latestMatch,
        MatchView nextMatch,

        List<MatchView> recentMatches,
        List<MatchView> upcomingMatches,
        List<MatchView> pendingResults,

        List<TopScorerView> topScorers,

        List<TeamFormResult> recentForm
) implements Serializable {
}