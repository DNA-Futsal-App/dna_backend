package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.*;;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MyTeamService {

    private static final int RECENT_MATCH_LIMIT = 5;
    private static final int UPCOMING_MATCH_LIMIT = 3;
    private static final int TOP_SCORER_LIMIT = 5;

    private final SportsSnapshotService snapshots;
    private final SportsQueryService sports;

    public MyTeamService(
            SportsSnapshotService snapshots,
            SportsQueryService sports
    ) {
        this.snapshots = snapshots;
        this.sports = sports;
    }

    public MyTeamView get(
            SportsFilter filter
    ) {
        SportsSnapshot snapshot =
                snapshots.snapshot(
                        filter.eventId()
                );

        SportsEventView event =
                snapshot.event();

        if (!hasText(filter.teamId())) {
            return unconfigured(
                    event
            );
        }

        TeamView team =
                snapshot.teams()
                        .stream()
                        .filter(candidate ->
                                filter.teamId()
                                        .equals(
                                                candidate.id()
                                        )
                        )
                        .findFirst()
                        .orElse(null);

        if (team == null) {
            return unavailable(
                    event
            );
        }

        MatchCalendarView calendar =
                sports.matchCalendar(
                        filter,
                        null,
                        null,
                        null
                );

        List<MatchView> recentMatches =
                calendar.played()
                        .stream()
                        .limit(
                                RECENT_MATCH_LIMIT
                        )
                        .toList();

        List<MatchView> upcomingMatches =
                calendar.upcoming()
                        .stream()
                        .limit(
                                UPCOMING_MATCH_LIMIT
                        )
                        .toList();

        StandingView standing =
                sports.standings(
                                filter,
                                null,
                                null
                        )
                        .stream()
                        .filter(row ->
                                team.id()
                                        .equals(
                                                row.team().id()
                                        )
                        )
                        .findFirst()
                        .orElse(null);

        List<TopScorerView> topScorers =
                sports.topScorers(
                        filter,
                        null,
                        TOP_SCORER_LIMIT
                );

        List<TeamFormResult> recentForm =
                recentMatches.stream()
                        .map(match ->
                                resultFor(
                                        match,
                                        team.id()
                                )
                        )
                        .toList();

        return new MyTeamView(
                true,
                MyTeamState.READY,

                team,

                event.eventId(),
                event.title(),
                event.season(),
                event.category(),
                event.division(),

                calendar.currentPhase(),
                calendar.scheduleState(),

                standing,

                first(
                        recentMatches
                ),

                first(
                        upcomingMatches

                ),

                recentMatches,
                upcomingMatches,
                calendar.pendingResults(),

                topScorers,

                recentForm
        );
    }

    private MyTeamView unconfigured(
            SportsEventView event
    ) {
        return new MyTeamView(
                false,

                MyTeamState.NOT_CONFIGURED,

                null,

                event.eventId(),
                event.title(),
                event.season(),
                event.category(),
                event.division(),

                null,

                null,

                null,
                null,

                null,
                List.of(),
                List.of(),
                List.of(),

                List.of(),

                List.of()
        );
    }

    private MyTeamView unavailable(
            SportsEventView event
    ) {
        return new MyTeamView(
                false,
                MyTeamState.TEAM_UNAVAILABLE,
                null,

                event.eventId(),
                event.title(),
                event.season(),
                event.category(),
                event.division(),

                null,

                SportsScheduleState.NO_GAMES,

                null,

                null,
                null,

                List.of(),
                List.of(),
                List.of(),

                List.of(),
                List.of()
        );
    }

    private TeamFormResult resultFor(
            MatchView match,
            String teamId
    ) {
        if (
                match.homeScore() == null ||
                        match.awayScore() == null
        ) {
            return TeamFormResult.UNKNOWN;
        }

        boolean home =
                teamId.equals(
                        match.homeTeam().id()
                );

        boolean away =
                teamId.equals(
                        match.awayTeam().id()
                );

        if (!home && !away) {
            return TeamFormResult.UNKNOWN;
        }

        int teamScore =
                home
                        ? match.homeScore()
                        : match.awayScore();

        int opponentScore =
                home
                        ? match.awayScore()
                        : match.homeScore();

        if (teamScore > opponentScore) {
            return TeamFormResult.WIN;
        }

        if (teamScore < opponentScore) {
            return TeamFormResult.LOSS;
        }

        return TeamFormResult.DRAW;
    }

    private MatchView first(
            List<MatchView> matches
    ) {
        return matches.isEmpty()
                ? null
                : matches.get(0);
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}