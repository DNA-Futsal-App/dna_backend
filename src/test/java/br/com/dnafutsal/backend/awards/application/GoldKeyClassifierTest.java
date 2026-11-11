package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.MatchStatus;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldKeyClassifierTest {
    private final TeamView gold = team("10", "Clube Ouro");
    private final TeamView silver = team("20", "Clube Prata");

    @Test
    void allowsOnlyExplicitGoldTeamsAndDeduplicatesOfficialRows() {
        GoldKeyClassifier.Analysis result = GoldKeyClassifier.analyze(
                snapshot(List.of(
                        standing("Quartas de Final", "CHAVE OURO", gold),
                        standing("Semifinal", "Chave Ouro", gold),
                        standing("Quartas de Final", "Chave Prata", silver)), List.of()),
                "PAULISTA", List.of(gold, silver));

        assertThat(result.ready()).isTrue();
        assertThat(result.teams()).extracting(TeamView::id).containsExactly("10");
    }

    @Test
    void rejectsConflictingAssignmentsInsteadOfChoosingFirstRow() {
        GoldKeyClassifier.Analysis result = GoldKeyClassifier.analyze(
                snapshot(List.of(standing("Quartas", "Chave Prata", gold),
                        standing("Final", "Chave Ouro", gold)), List.of()),
                "PAULISTA", List.of(gold));

        assertThat(result.ready()).isFalse();
        assertThat(result.ambiguousTeamIds()).containsExactly("10");
        assertThat(result.teams()).isEmpty();
    }

    @Test
    void doesNotInferGoldFromUnlabeledGroups() {
        GoldKeyClassifier.Analysis result = GoldKeyClassifier.analyze(
                snapshot(List.of(standing("Classificatória", "Grupo A", gold)), List.of()),
                "PAULISTA", List.of(gold));

        assertThat(result.ready()).isFalse();
        assertThat(result.teams()).isEmpty();
    }

    @Test
    void detectsGoldFromMatchesAndDoesNotMixUnionWithPaulista() {
        MatchView paulista = match("Quartas de Final - Chave Ouro", gold, null);
        MatchView uniao = match("Torneio União - Chave Ouro", silver, null);
        SportsSnapshot source = snapshot(List.of(), List.of(paulista, uniao));

        GoldKeyClassifier.Analysis result = GoldKeyClassifier.analyze(source, "PAULISTA", List.of(gold, silver));
        assertThat(result.teams()).extracting(TeamView::id).containsExactly("10");
    }

    @Test
    void preventsUnknownIdsFromBeingAccepted() {
        GoldKeyClassifier.Analysis result = GoldKeyClassifier.analyze(
                snapshot(List.of(standing("Quartas", "Chave Ouro", gold)), List.of()),
                "PAULISTA", List.of(silver));

        assertThat(result.ready()).isFalse();
        assertThat(result.unresolvedTeams()).containsExactly("10");
    }

    private SportsSnapshot snapshot(List<StandingView> rows, List<MatchView> matches) {
        return new SportsSnapshot(new SportsEventView(904L, "Campeonato Paulista", 2026, "Sub-12", "A1", null),
                List.of(gold, silver), matches, rows, List.of(), Instant.parse("2026-11-08T00:00:00Z"));
    }

    private TeamView team(String id, String name) {
        return new TeamView(id, name, null, null);
    }

    private StandingView standing(String phase, String group, TeamView team) {
        return new StandingView(phase, group, 1, team, 1, 1, 0, 0, 1, 0, 1,
                3, 1d, 1d, 0d, 1d);
    }

    private MatchView match(String phase, TeamView home, TeamView away) {
        return new MatchView("1", 904L, "Campeonato Paulista", 2026,
                "Sub-12", "A1", phase, home, away, null, null,
                null, null, MatchStatus.SCHEDULED, false, null, null);
    }
}
