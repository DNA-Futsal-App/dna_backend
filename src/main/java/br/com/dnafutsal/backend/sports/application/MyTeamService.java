package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.MatchCalendarView;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.MyTeamView;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamFormResult;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
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
                        .orElseThrow(() ->
                                Errors.notFound(
                                        "SPORTS_TEAM_NOT_FOUND",
                                        "O time selecionado não participa desta competição."
                                )
                        );

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

                team,

                event.eventId(),
                event.title(),
                event.season(),
                event.category(),
                event.division(),

                calendar.currentPhase(),

                standing,

                first(
                        recentMatches
                ),

                first(
                        upcomingMatches
                ),

                recentMatches,
                upcomingMatches,

                topScorers,

                recentForm
        );
    }

    private MyTeamView unconfigured(
            SportsEventView event
    ) {
        return new MyTeamView(
                false,

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