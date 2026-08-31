package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.domain.MatchCalendarView;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamFormResult;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MyTeamServiceTest {

    private final SportsSnapshotService snapshots =
            mock(
                    SportsSnapshotService.class
            );

    private final SportsQueryService sports =
            mock(
                    SportsQueryService.class
            );

    private final MyTeamService service =
            new MyTeamService(
                    snapshots,
                    sports
            );

    @Test
    void returnsUnconfiguredStateWhenUserHasNoTeam() {
        SportsFilter filter =
                new SportsFilter(
                        917,
                        null
                );

        when(
                snapshots.snapshot(917)
        ).thenReturn(
                snapshot(
                        List.of()
                )
        );

        var result =
                service.get(
                        filter
                );

        assertThat(
                result.configured()
        ).isFalse();

        assertThat(
                result.team()
        ).isNull();

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.recentMatches()
        ).isEmpty();

        verifyNoInteractions(
                sports
        );
    }

    @Test
    void buildsPersonalizedTeamSummary() {
        TeamView team =
                team();

        SportsFilter filter =
                new SportsFilter(
                        917,
                        "10"
                );

        MatchView win =
                match(
                        "102",
                        team,
                        4,
                        1
                );

        MatchView loss =
                match(
                        "101",
                        team,
                        1,
                        2
                );

        MatchView next =
                upcoming(
                        "103",
                        team
                );

        StandingView standing =
                new StandingView(
                        "2ª Fase",
                        "Grupo A",
                        3,
                        team,
                        8,
                        5,
                        1,
                        2,
                        24,
                        13,
                        11,
                        16,
                        null,
                        null,
                        null,
                        null
                );

        TopScorerView scorer =
                new TopScorerView(
                        1,
                        "2ª Fase",
                        "Atleta A",
                        null,
                        team,
                        9,
                        false
                );

        when(
                snapshots.snapshot(917)
        ).thenReturn(
                snapshot(
                        List.of(team)
                )
        );

        when(
                sports.matchCalendar(
                        filter,
                        null,
                        null,
                        null
                )
        ).thenReturn(
                new MatchCalendarView(
                        "2ª Fase",
                        List.of(
                                win,
                                loss
                        ),
                        List.of(
                                next
                        )
                )
        );

        when(
                sports.standings(
                        filter,
                        null,
                        null
                )
        ).thenReturn(
                List.of(
                        standing
                )
        );

        when(
                sports.topScorers(
                        filter,
                        null,
                        5
                )
        ).thenReturn(
                List.of(
                        scorer
                )
        );

        var result =
                service.get(
                        filter
                );

        assertThat(
                result.configured()
        ).isTrue();

        assertThat(
                result.team()
        ).isEqualTo(
                team
        );

        assertThat(
                result.currentPhase()
        ).isEqualTo(
                "2ª Fase"
        );

        assertThat(
                result.standing()
        ).isEqualTo(
                standing
        );

        assertThat(
                result.latestMatch()
        ).isEqualTo(
                win
        );

        assertThat(
                result.nextMatch()
        ).isEqualTo(
                next
        );

        assertThat(
                result.recentForm()
        ).containsExactly(
                TeamFormResult.WIN,
                TeamFormResult.LOSS
        );

        assertThat(
                result.topScorers()
        ).containsExactly(
                scorer
        );
    }

    private SportsSnapshot snapshot(
            List<TeamView> teams
    ) {
        return new SportsSnapshot(
                new SportsEventView(
                        917,
                        "Campeonato Paulista",
                        2026,
                        "Principal",
                        "A1",
                        null
                ),
                teams,
                List.of(),
                List.of(),
                List.of(),
                Instant.parse(
                        "2026-08-31T12:00:00Z"
                )
        );
    }

    private TeamView team() {
        return new TeamView(
                "10",
                "Time A",
                null,
                null
        );
    }

    private MatchView match(
            String id,
            TeamView team,
            int teamScore,
            int opponentScore
    ) {
        TeamView opponent =
                new TeamView(
                        "20",
                        "Time B",
                        null,
                        null
                );

        return new MatchView(
                id,
                917,
                "Campeonato Paulista",
                2026,
                "Principal",
                "A1",
                "2ª Fase",
                team,
                opponent,
                teamScore,
                opponentScore,
                Instant.parse(
                        "2026-08-30T20:00:00Z"
                ),
                "FINISHED",
                false,
                null,
                null
        );
    }

    private MatchView upcoming(
            String id,
            TeamView team
    ) {
        TeamView opponent =
                new TeamView(
                        "20",
                        "Time B",
                        null,
                        null
                );

        return new MatchView(
                id,
                917,
                "Campeonato Paulista",
                2026,
                "Principal",
                "A1",
                "2ª Fase",
                team,
                opponent,
                null,
                null,
                Instant.parse(
                        "2026-09-05T20:00:00Z"
                ),
                "SCHEDULED",
                false,
                null,
                null
        );
    }
}