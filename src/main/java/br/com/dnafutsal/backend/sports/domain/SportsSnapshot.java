package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record SportsSnapshot(
        SportsEventView event,
        List<TeamView> teams,
        List<MatchView> matches,
        List<StandingView> standings,
        List<TopScorerView> topScorers,
        Instant collectedAt
) implements Serializable {

    public SportsSnapshot {
        teams = immutable(teams);
        matches = immutable(matches);
        standings = immutable(standings);
        topScorers = immutable(topScorers);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
