package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.domain.MatchCalendarView;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsHomeMode;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsHomeServiceTest {

    private final SportsSnapshotService snapshots =
            mock(SportsSnapshotService.class);

    private final SportsQueryService sports =
            mock(SportsQueryService.class);

    private final SportsHomeService service =
            new SportsHomeService(
                    snapshots,
                    sports
            );

    @Test
    void buildsTeamHomeWhenFollowedTeamExists() {
        TeamView teamA =
                team(
                        "10",
                        "Time A"
                );

        TeamView teamB =
                team(
                        "20",
                        "Time B"
                );

        SportsFilter teamFilter =
                new SportsFilter(
                        917,
                        "10"
                );

        SportsFilter categoryFilter =
                new SportsFilter(
                        917,
                        null
                );

        MatchView latest =
                finished(
                        "100",
                        teamA,
                        teamB
                );

        MatchView next =
                scheduled(
                        "101",
                        teamA,
                        teamB
                );

        StandingView standingA =
                standing(
                        2,
                        "Grupo A",
                        teamA
                );

        StandingView standingB =
                standing(
                        1,
                        "Grupo A",
                        teamB
                );

        TopScorerView scorer =
                new TopScorerView(
                        1,
                        "2ª Fase",
                        "Atleta B",
                        null,
                        teamB,
                        10,
                        false
                );

        when(
                snapshots.snapshot(917)
        ).thenReturn(
                snapshot(
                        List.of(
                                teamA,
                                teamB
                        )
                )
        );

        when(
                sports.matchCalendar(
                        teamFilter,
                        null,
                        null,
                        null
                )
        ).thenReturn(
                new MatchCalendarView(
                        "2ª Fase",
                        List.of(
                                latest
                        ),
                        List.of(
                                next
                        )
                )
        );

        when(
                sports.standings(
                        teamFilter,
                        null,
                        null
                )
        ).thenReturn(
                List.of(
                        standingB,
                        standingA
                )
        );

        when(
                sports.topScorers(
                        categoryFilter,
                        null,
                        4
                )
        ).thenReturn(
                List.of(
                        scorer
                )
        );

        var result =
                service.get(
                        teamFilter
                );

        assertThat(
                result.mode()
        ).isEqualTo(
                SportsHomeMode.TEAM
        );

        assertThat(
                result.team()
        ).isEqualTo(
                teamA
        );

        assertThat(
                result.teamStanding()
        ).isEqualTo(
                standingA
        );

        assertThat(
                result.latestMatch()
        ).isEqualTo(
                latest
        );

        assertThat(
                result.nextMatch()
        ).isEqualTo(
                next
        );

        /*
         * A artilharia continua geral.
         */
        assertThat(
                result.topScorers()
        ).containsExactly(
                scorer
        );
    }

    @Test
    void buildsCategoryHomeWithoutFollowedTeam() {
        TeamView teamA =
                team(
                        "10",
                        "Time A"
                );

        TeamView teamB =
                team(
                        "20",
                        "Time B"
                );

        SportsFilter filter =
                new SportsFilter(
                        917,
                        null
                );

        when(
                snapshots.snapshot(917)
        ).thenReturn(
                snapshot(
                        List.of(
                                teamA,
                                teamB
                        )
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
                        "1ª Fase",
                        List.of(),
                        List.of()
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
                        standing(
                                1,
                                "Grupo A",
                                teamA
                        ),

                        standing(
                                1,
                                "Grupo B",
                                teamB
                        )
                )
        );

        when(
                sports.topScorers(
                        filter,
                        null,
                        4
                )
        ).thenReturn(
                List.of()
        );

        var result =
                service.get(
                        filter
                );

        assertThat(
                result.mode()
        ).isEqualTo(
                SportsHomeMode.CATEGORY
        );

        assertThat(
                result.team()
        ).isNull();

        assertThat(
                result.teamStanding()
        ).isNull();

        assertThat(
                result.teamCount()
        ).isEqualTo(2);

        /*
         * Não mistura grupos diferentes
         * na prévia da Home.
         */
        assertThat(
                result.standings()
        )
                .hasSize(1)
                .allSatisfy(row ->
                        assertThat(
                                row.group()
                        ).isEqualTo(
                                "Grupo A"
                        )
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

    private TeamView team(
            String id,
            String name
    ) {
        return new TeamView(
                id,
                name,
                null,
                null
        );
    }

    private StandingView standing(
            int position,
            String group,
            TeamView team
    ) {
        return new StandingView(
                "2ª Fase",
                group,
                position,
                team,
                8,
                5,
                1,
                2,
                20,
                10,
                10,
                16,
                null,
                null,
                null,
                null
        );
    }

    private MatchView finished(
            String id,
            TeamView home,
            TeamView away
    ) {
        return new MatchView(
                id,
                917,
                "Campeonato Paulista",
                2026,
                "Principal",
                "A1",
                "2ª Fase",
                home,
                away,
                3,
                1,
                Instant.parse(
                        "2026-08-30T20:00:00Z"
                ),
                "FINISHED",
                false,
                null,
                null
        );
    }

    private MatchView scheduled(
            String id,
            TeamView home,
            TeamView away
    ) {
        return new MatchView(
                id,
                917,
                "Campeonato Paulista",
                2026,
                "Principal",
                "A1",
                "2ª Fase",
                home,
                away,
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