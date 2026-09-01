package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
class SportsScraperMapper {

    private final ZoneId zoneId;
    private final Clock clock;

    SportsScraperMapper(AppProperties properties, Clock clock) {
        this.zoneId = properties.zoneId();
        this.clock = clock;
    }

    CatalogItemView catalogItem(
            ScraperCatalogOption source
    ) {
        if (
                source == null ||
                        source.id() <= 0 ||
                        source.name() == null ||
                        source.name().isBlank()
        ) {
            throw Errors.badGateway(
                    "SPORTS_DATA_INVALID",
                    "A fonte de dados esportivos retornou uma divisão inválida."
            );
        }

        return new CatalogItemView(
                source.id(),
                source.name().trim()
        );
    }

    CatalogCategoryView catalogCategory(
            ScraperCatalogCategory source
    ) {
        if (
                source == null ||
                        source.id() <= 0 ||
                        source.eventId() <= 0 ||
                        source.name() == null ||
                        source.name().isBlank()
        ) {
            throw Errors.badGateway(
                    "SPORTS_DATA_INVALID",
                    "A fonte de dados esportivos retornou uma categoria inválida."
            );
        }

        return new CatalogCategoryView(
                source.id(),
                source.name().trim(),
                source.eventId()
        );
    }

    SportsEventView event(ScraperEvent source) {
        if (source == null) {
            throw Errors.badGateway("SPORTS_DATA_INVALID",
                    "A fonte de dados esportivos retornou um evento inválido.");
        }
        return new SportsEventView(source.eventId(), source.title(), source.season(), source.category(),
                source.division(), source.sourceUrl());
    }

    List<TeamView> teams(List<ScraperTeam> source) {
        return safe(source).stream().map(this::team).toList();
    }

    SportsSnapshot snapshot(ScraperSnapshot source, List<ScraperScorer> scorerOverride) {
        if (source == null || source.event() == null) {
            throw Errors.badGateway("SPORTS_DATA_INVALID",
                    "A fonte de dados esportivos retornou um snapshot inválido.");
        }

        SportsEventView event = event(source.event());
        List<TeamView> teams = teams(source.teams());
        Map<String, TeamView> teamsByName = indexByName(teams);
        List<MatchView> matches = safe(source.games()).stream()
                .map(game -> match(event, game, teamsByName))
                .toList();
        List<StandingView> standings = safe(source.standings()).stream()
                .map(row -> standing(row, teamsByName))
                .toList();
        List<ScraperScorer> scorers = scorerOverride == null ? safe(source.scorers()) : safe(scorerOverride);

        return new SportsSnapshot(event, teams, matches, standings, topScorers(scorers, teamsByName),
                source.collectedAt() == null ? clock.instant() : source.collectedAt());
    }

    private MatchView match(
            SportsEventView event,
            ScraperGame game,
            Map<String, TeamView> teamsByName
    ) {
        LocalDate scheduledDate =
                game.date();

        Instant scheduledAt =
                game.date() == null
                        || game.time() == null
                        ? null
                        : game.date()
                        .atTime(
                                game.time()
                        )
                        .atZone(
                                zoneId
                        )
                        .toInstant();

        MatchStatus status =
                matchStatus(
                        game,
                        scheduledDate,
                        scheduledAt
                );

        return new MatchView(
                gameId(
                        event.eventId(),
                        game
                ),

                event.eventId(),
                event.title(),
                event.season(),
                event.category(),
                event.division(),

                game.phase(),

                resolveTeam(
                        game.homeTeam(),
                        game.homeLogoUrl(),
                        teamsByName
                ),

                resolveTeam(
                        game.awayTeam(),
                        game.awayLogoUrl(),
                        teamsByName
                ),

                game.homeScore(),
                game.awayScore(),

                scheduledDate,
                scheduledAt,

                status,

                game.walkover(),
                game.venue(),
                game.matchSheetUrl()
        );
    }

    private StandingView standing(ScraperStanding row, Map<String, TeamView> teamsByName) {
        return new StandingView(
                row.phase(),
                row.group(),
                row.position(),
                resolveTeam(row.team(), row.logoUrl(), teamsByName),
                row.games(),
                row.wins(),
                row.draws(),
                row.losses(),
                row.goalsFor(),
                row.goalsAgainst(),
                row.goalDifference(),
                row.points(),
                row.average(),
                row.goalsForAverage(),
                row.goalsAgainstAverage(),
                row.technicalIndex()
        );
    }

    private List<TopScorerView> topScorers(List<ScraperScorer> source, Map<String, TeamView> teamsByName) {
        List<ScraperScorer> sorted = source.stream()
                .sorted(Comparator.comparing(ScraperScorer::goals,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ScraperScorer::player, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<TopScorerView> result = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            ScraperScorer scorer = sorted.get(index);
            result.add(new TopScorerView(
                    index + 1,
                    scorer.phase(),
                    scorer.player(),
                    scorer.playerImageUrl(),
                    resolveTeam(scorer.team(), scorer.teamLogoUrl(), teamsByName),
                    scorer.goals(),
                    scorer.personalDataSuppressed()
            ));
        }
        return List.copyOf(result);
    }

    private TeamView team(ScraperTeam source) {
        return new TeamView(Long.toString(source.teamId()), source.name(), null, source.logoUrl());
    }

    private Map<String, TeamView> indexByName(List<TeamView> teams) {
        Map<String, TeamView> result = new LinkedHashMap<>();
        teams.forEach(team -> result.putIfAbsent(normalize(team.name()), team));
        return result;
    }

    private TeamView resolveTeam(String name, String logoUrl, Map<String, TeamView> teamsByName) {
        String normalized = normalize(name);
        TeamView known = teamsByName.get(normalized);
        if (known != null) {
            if (known.logoUrl() == null && logoUrl != null) {
                return new TeamView(known.id(), known.name(), known.shortName(), logoUrl);
            }
            return known;
        }
        String displayName = name == null || name.isBlank() ? "Equipe não informada" : name.trim();
        String suffix = normalized.isBlank() ? deterministicId(displayName) : normalized.replace(' ', '-');
        return new TeamView("name:" + suffix, displayName, null, logoUrl);
    }

    private String gameId(long eventId, ScraperGame game) {
        if (game.gameId() != null) {
            return Long.toString(game.gameId());
        }
        String source = eventId + "|" + game.phase() + "|" + game.date() + "|" + game.time()
                + "|" + game.homeTeam() + "|" + game.awayTeam();
        return "generated:" + deterministicId(source);
    }

    private String deterministicId(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String normalize(String value) {
        String prepared = (value == null ? "" : value).replace('ª', 'a').replace('º', 'o');
        return Normalizer.normalize(prepared, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private MatchStatus matchStatus(
            ScraperGame game,
            LocalDate scheduledDate,
            Instant scheduledAt
    ) {
        /*
         * W.O. é um resultado oficial já declarado.
         */
        if (game.walkover()) {
            return MatchStatus.FINISHED;
        }

        LocalDate today =
                LocalDate.now(
                        clock.withZone(
                                zoneId
                        )
                );

        Instant now =
                clock.instant();

        if (
                scheduledDate != null
                        && scheduledDate.isAfter(
                        today
                )
        ) {
            return MatchStatus.SCHEDULED;
        }

        if (
                scheduledDate != null
                        && scheduledDate.isEqual(
                        today
                )
                        && scheduledAt != null
                        && scheduledAt.isAfter(
                        now
                )
        ) {
            return MatchStatus.SCHEDULED;
        }

        boolean hasResult =
                game.homeScore() != null
                        && game.awayScore() != null;

        if (hasResult) {
            return MatchStatus.FINISHED;
        }

        /*
         * Dia anterior sem resultado.
         */
        if (
                scheduledDate != null
                        && scheduledDate.isBefore(
                        today
                )
        ) {
            return MatchStatus.RESULT_PENDING;
        }

        /*
         * Jogo de hoje cujo horário passou,
         * mas a fonte ainda não atualizou.
         */
        if (
                scheduledAt != null
                        && !scheduledAt.isAfter(
                        now
                )
        ) {
            return MatchStatus.RESULT_PENDING;
        }

        return MatchStatus.SCHEDULED;
    }
}
