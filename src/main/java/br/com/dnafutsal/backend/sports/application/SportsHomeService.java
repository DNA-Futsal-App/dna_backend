package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.domain.MatchCalendarView;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsHomeMode;
import br.com.dnafutsal.backend.sports.domain.SportsHomeView;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SportsHomeService {

    private static final int HOME_SCORER_LIMIT = 4;
    private static final int HOME_RECENT_MATCH_LIMIT = 2;

    private final SportsSnapshotService snapshots;
    private final SportsQueryService sports;

    public SportsHomeService(
            SportsSnapshotService snapshots,
            SportsQueryService sports
    ) {
        this.snapshots = snapshots;
        this.sports = sports;
    }

    public SportsHomeView get(
            SportsFilter requestedFilter
    ) {
        SportsSnapshot snapshot =
                snapshots.snapshot(
                        requestedFilter.eventId()
                );

        SportsEventView event =
                snapshot.event();

        SportsFilter categoryFilter =
                new SportsFilter(
                        requestedFilter.eventId(),
                        null
                );

        TeamView followedTeam =
                resolveTeam(
                        snapshot,
                        requestedFilter.teamId()
                );

        boolean teamMode =
                followedTeam != null;

        SportsFilter effectiveFilter =
                teamMode
                        ? requestedFilter
                        : categoryFilter;

        MatchCalendarView calendar =
                sports.matchCalendar(
                        effectiveFilter,
                        null,
                        null,
                        null
                );

        List<StandingView> standings =
                sports.standings(
                        effectiveFilter,
                        null,
                        null
                );

        StandingView teamStanding =
                followedTeam == null
                        ? null
                        : standings.stream()
                        .filter(row ->
                                followedTeam.id()
                                        .equals(
                                                row.team().id()
                                        )
                        )
                        .findFirst()
                        .orElse(null);

        String standingGroup =
                teamStanding != null
                        && hasText(
                        teamStanding.group()
                )
                        ? teamStanding.group()
                        : firstGroup(
                        standings
                );

        List<StandingView> standingsPreview =
                hasText(standingGroup)
                        ? standings.stream()
                        .filter(row ->
                                sameGroup(
                                        row.group(),
                                        standingGroup
                                )
                        )
                        .toList()
                        : standings;

        List<TopScorerView> topScorers =
                sports.topScorers(
                        categoryFilter,
                        null,
                        HOME_SCORER_LIMIT
                );

        List<MatchView> recentMatches =
                calendar.played()
                        .stream()
                        .limit(
                                HOME_RECENT_MATCH_LIMIT
                        )
                        .toList();

        return new SportsHomeView(
                teamMode
                        ? SportsHomeMode.TEAM
                        : SportsHomeMode.CATEGORY,

                event.eventId(),
                event.title(),
                event.season(),
                event.category(),
                event.division(),
                calendar.scheduleState(),

                calendar.currentPhase(),
                standingGroup,

                followedTeam,
                teamStanding,

                first(
                        calendar.played()
                ),

                first(
                        calendar.upcoming()
                ),

                snapshot.teams().size(),
                calendar.upcoming().size(),

                List.copyOf(
                        standingsPreview
                ),

                List.copyOf(
                        topScorers
                ),

                List.copyOf(
                        recentMatches
                )
        );
    }

    private TeamView resolveTeam(
            SportsSnapshot snapshot,
            String teamId
    ) {
        if (!hasText(teamId)) {
            return null;
        }

        return snapshot.teams()
                .stream()
                .filter(team ->
                        teamId.equals(
                                team.id()
                        )
                )
                .findFirst()
                .orElse(null);
    }

    private String firstGroup(
            List<StandingView> standings
    ) {
        return standings.stream()
                .map(
                        StandingView::group
                )
                .filter(
                        this::hasText
                )
                .findFirst()
                .orElse(null);
    }

    private boolean sameGroup(
            String first,
            String second
    ) {
        if (
                first == null ||
                        second == null
        ) {
            return false;
        }

        return first.trim()
                .equalsIgnoreCase(
                        second.trim()
                );
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