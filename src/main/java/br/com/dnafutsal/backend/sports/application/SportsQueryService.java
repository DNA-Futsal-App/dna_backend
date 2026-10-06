package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.*;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class SportsQueryService {

    private final SportsSnapshotService snapshots;
    private final ZoneId zoneId;
    private final Clock clock;

    public SportsQueryService(
            SportsSnapshotService snapshots,
            AppProperties properties,
            Clock clock
    ) {
        this.snapshots = snapshots;
        this.zoneId = properties.zoneId();
        this.clock = clock;
    }

    public List<MatchView> playedMatches(
            SportsFilter filter,
            String phase,
            LocalDate from,
            LocalDate to
    ) {
        validateDates(from, to);

        return matches(
                filter,
                phase,
                from,
                to
        )
                .stream()
                .filter(match ->
                        match.status()
                                == MatchStatus.FINISHED
                )
                .sorted(
                        Comparator
                                .comparing(
                                        MatchView::scheduledDate,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                                .thenComparing(
                                        MatchView::scheduledAt,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                                .thenComparing(
                                        MatchView::id
                                )
                )
                .toList();
    }

    public List<MatchView> upcomingMatches(
            SportsFilter filter,
            String phase,
            LocalDate from,
            LocalDate to
    ) {
        validateDates(from, to);

        Instant today =
                startOfToday();

        return matches(
                filter,
                phase,
                from,
                to
        )
                .stream()
                .filter(match ->
                        match.status()
                                == MatchStatus.SCHEDULED
                )
                .filter(match ->
                        match.scheduledAt() == null
                                || !match.scheduledAt()
                                .isBefore(today)
                )
                .sorted(
                        Comparator
                                .comparing(
                                        MatchView::scheduledDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                                .thenComparing(
                                        MatchView::scheduledAt,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                                .thenComparing(
                                        MatchView::id
                                )
                )
                .toList();
    }

    public List<StandingView> standings(
            SportsFilter filter,
            String requestedPhase,
            String requestedGroup
    ) {
        SportsSnapshot snapshot =
                snapshots.snapshot(
                        filter.eventId()
                );

        List<StandingView> rows =
                standingsForPhase(
                        snapshot,
                        requestedPhase
                );
        if (hasText(requestedGroup)) {
            rows = rows.stream()
                    .filter(row ->
                            contains(
                                    row.group(),
                                    requestedGroup
                            )
                    )
                    .toList();


        } else if (hasText(filter.teamId())) {

            String followedTeamGroup =
                    rows.stream()
                            .filter(row ->
                                    filter.teamId()
                                            .equals(
                                                    row.team().id()
                                            )
                            )
                            .map(
                                    StandingView::group
                            )
                            .filter(
                                    this::hasText
                            )
                            .findFirst()
                            .orElse(null);

            if (
                    followedTeamGroup != null
            ) {
                rows = rows.stream()
                        .filter(row ->
                                same(
                                        row.group(),
                                        followedTeamGroup
                                )
                        )
                        .toList();
            }
        }

        return rows.stream()
                .sorted(
                        Comparator.comparing(
                                        StandingView::position,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                                .thenComparing(
                                        row ->
                                                row.team().name(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .toList();
    }

    public List<TopScorerView> topScorers(
            SportsFilter filter,
            String requestedPhase,
            int limit
    ) {
        SportsSnapshot snapshot =
                snapshots.snapshot(
                        filter.eventId()
                );

        List<TopScorerView> rows =
                scorersForPhase(
                        snapshot,
                        requestedPhase
                );

        if (hasText(filter.teamId())) {
            rows = rows.stream()
                    .filter(scorer ->
                            filter.teamId()
                                    .equals(
                                            scorer.team().id()
                                    )
                    )
                    .toList();
        }

        List<TopScorerView> filtered =
                rows.stream()
                        .sorted(
                                Comparator.comparing(
                                                TopScorerView::goals,
                                                Comparator.nullsLast(
                                                        Comparator.reverseOrder()
                                                )
                                        )
                                        .thenComparing(
                                                TopScorerView::athleteName,
                                                Comparator.nullsLast(
                                                        String.CASE_INSENSITIVE_ORDER
                                                )
                                        )
                        )
                        .limit(limit)
                        .toList();
        final var ranked = getTopScorerViews(filtered);

        return List.copyOf(
                ranked
        );
    }

    public MatchCalendarView matchCalendar(
            SportsFilter filter,
            String phase,
            LocalDate from,
            LocalDate to
    ) {
        validateDates(
                from,
                to
        );

        SportsSnapshot snapshot =
                snapshots.snapshot(
                        filter.eventId()
                );

        List<MatchView> played =
                playedMatches(
                        filter,
                        phase,
                        from,
                        to
                );

        List<MatchView> upcoming =
                upcomingMatches(
                        filter,
                        phase,
                        from,
                        to
                );

        List<MatchView> pending =
                pendingResults(
                        filter,
                        phase,
                        from,
                        to
                );

        return new MatchCalendarView(
                currentPhase(
                        snapshot
                ),

                scheduleState(
                        played,
                        upcoming,
                        pending
                ),

                played,
                upcoming,
                pending
        );
    }

    public List<MatchView> pendingResults(
            SportsFilter filter,
            String phase,
            LocalDate from,
            LocalDate to
    ) {
        validateDates(
                from,
                to
        );

        return matches(
                filter,
                phase,
                from,
                to
        )
                .stream()
                .filter(match ->
                        match.status()
                                == MatchStatus.RESULT_PENDING
                )
                .sorted(
                        Comparator
                                .comparing(
                                        MatchView::scheduledDate,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                                .thenComparing(
                                        MatchView::scheduledAt,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                                .thenComparing(
                                        MatchView::id
                                )
                )
                .toList();
    }

    private SportsScheduleState scheduleState(
            List<MatchView> played,
            List<MatchView> upcoming,
            List<MatchView> pending
    ) {
        if (!upcoming.isEmpty()) {
            return SportsScheduleState.ACTIVE;
        }

        if (
                !played.isEmpty()
                        || !pending.isEmpty()
        ) {
            return SportsScheduleState.AWAITING_SCHEDULE;
        }

        return SportsScheduleState.NO_GAMES;
    }

    private static @NonNull List<TopScorerView> getTopScorerViews(List<TopScorerView> filtered) {
        List<TopScorerView> ranked =
                new ArrayList<>(
                        filtered.size()
                );

        for (
                int index = 0;
                index < filtered.size();
                index++
        ) {
            TopScorerView scorer =
                    filtered.get(index);

            ranked.add(
                    new TopScorerView(
                            index + 1,
                            scorer.phase(),
                            scorer.athleteName(),
                            scorer.athleteImageUrl(),
                            scorer.team(),
                            scorer.goals(),
                            scorer.personalDataSuppressed()
                    )
            );
        }
        return ranked;
    }

    private List<MatchView> matches(
            SportsFilter filter,
            String phase,
            LocalDate from,
            LocalDate to
    ) {
        return snapshots
                .snapshot(
                        filter.eventId()
                )
                .matches()
                .stream()
                .filter(match ->
                        contains(
                                match.phase(),
                                phase
                        )
                )
                .filter(match ->
                        filter.teamId() == null
                                || filter.teamId()
                                .equals(
                                        match.homeTeam().id()
                                )
                                || filter.teamId()
                                .equals(
                                        match.awayTeam().id()
                                )
                )
                .filter(match ->
                        withinRange(
                                match,
                                from,
                                to
                        )
                )
                .toList();
    }

    private List<StandingView> standingsForPhase(
            SportsSnapshot snapshot,
            String requestedPhase
    ) {
        if (hasText(requestedPhase)) {
            return deduplicateStandings(
                    snapshot.standings()
                            .stream()
                            .filter(row ->
                                    contains(
                                            row.phase(),
                                            requestedPhase
                                    )
                            )
                            .toList()
            );
        }

        String classificationPhase =
                classificationPhase(
                        snapshot
                );

        if (!hasText(classificationPhase)) {
            return deduplicateStandings(
                    snapshot.standings()
            );
        }

        return deduplicateStandings(
                snapshot.standings()
                        .stream()
                        .filter(row ->
                                same(
                                        row.phase(),
                                        classificationPhase
                                )
                        )
                        .toList()
        );
    }

    private String classificationPhase(
            SportsSnapshot snapshot
    ) {
        var phasesByKey =
                new java.util.LinkedHashMap<String, String>();

        snapshot.standings()
                .stream()
                .map(
                        StandingView::phase
                )
                .filter(
                        this::hasText
                )
                .forEach(phase ->
                        phasesByKey.putIfAbsent(
                                normalize(phase),
                                phase
                        )
                );

        List<String> phases =
                List.copyOf(
                        phasesByKey.values()
                );

        if (phases.isEmpty()) {
            return null;
        }

        /*
         * A FPFS expõe uma aba/fase própria chamada "Fase Classificatória"
         * (ou variantes como "Fase de Classificação"). O scraper preserva
         * esse rótulo em StandingView.phase(), então essa é a fonte de
         * verdade para a tabela final da fase de pontos.
         */
        List<String> officialClassificationPhases =
                phases.stream()
                        .filter(
                                this::isOfficialClassificationPhase
                        )
                        .toList();

        if (
                !officialClassificationPhases.isEmpty()
        ) {
            return officialClassificationPhases
                    .stream()
                    .max(
                            Comparator
                                    .comparingInt(
                                            (String phase) ->
                                                    distinctTeamsInPhase(
                                                            snapshot,
                                                            phase
                                                    )
                                    )
                                    .thenComparingInt(
                                            (String phase) ->
                                                    totalPlayedInPhase(
                                                            snapshot,
                                                            phase
                                                    )
                                    )
                    )
                    .orElse(
                            officialClassificationPhases.get(0)
                    );
        }

        /*
         * Compatibilidade para campeonatos cuja fonte não nomeia
         * explicitamente a fase classificatória.
         */
        List<String> nonKnockoutPhases =
                phases.stream()
                        .filter(phase ->
                                !isKnockoutPhase(
                                        phase
                                )
                        )
                        .toList();

        if (
                nonKnockoutPhases.isEmpty()
        ) {
            return null;
        }

        Comparator<MatchView> byDate =
                Comparator
                        .comparing(
                                MatchView::scheduledDate,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder()
                                )
                        )
                        .thenComparing(
                                MatchView::scheduledAt,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder()
                                )
                        );

        String phaseFromLatestClassificationMatch =
                snapshot.matches()
                        .stream()
                        .filter(match ->
                                hasText(
                                        match.phase()
                                )
                        )
                        .filter(match ->
                                !isKnockoutPhase(
                                        match.phase()
                                )
                        )
                        .filter(match ->
                                nonKnockoutPhases.stream()
                                        .anyMatch(phase ->
                                                same(
                                                        phase,
                                                        match.phase()
                                                )
                                        )
                        )
                        .max(
                                byDate
                        )
                        .map(
                                MatchView::phase
                        )
                        .orElse(null);

        if (
                phaseFromLatestClassificationMatch
                        != null
        ) {
            return nonKnockoutPhases.stream()
                    .filter(phase ->
                            same(
                                    phase,
                                    phaseFromLatestClassificationMatch
                            )
                    )
                    .findFirst()
                    .orElse(
                            phaseFromLatestClassificationMatch
                    );
        }

        return nonKnockoutPhases.get(
                nonKnockoutPhases.size() - 1
        );
    }

    private boolean isOfficialClassificationPhase(
            String value
    ) {
        String phase =
                normalize(value);

        return phase.contains(
                "classificatoria"
        )
                || phase.contains(
                "classificacao"
        );
    }

    private int distinctTeamsInPhase(
            SportsSnapshot snapshot,
            String phase
    ) {
        return Math.toIntExact(
                snapshot.standings()
                        .stream()
                        .filter(row ->
                                same(
                                        row.phase(),
                                        phase
                                )
                        )
                        .map(row ->
                                normalize(
                                        row.team().name()
                                )
                        )
                        .distinct()
                        .count()
        );
    }

    private int totalPlayedInPhase(
            SportsSnapshot snapshot,
            String phase
    ) {
        return snapshot.standings()
                .stream()
                .filter(row ->
                        same(
                                row.phase(),
                                phase
                        )
                )
                .map(
                        StandingView::played
                )
                .filter(
                        java.util.Objects::nonNull
                )
                .mapToInt(
                        Integer::intValue
                )
                .sum();
    }

    private boolean isKnockoutPhase(
            String value
    ) {
        String phase =
                normalize(value);

        return phase.contains("oitav")
                || phase.contains("quart")
                || phase.contains("semi final")
                || phase.contains("semifinal")
                || phase.equals("final")
                || phase.endsWith(" final")
                || phase.contains("mata mata")
                || phase.contains("eliminatoria")
                || phase.contains("playoff");
    }

    private List<StandingView> deduplicateStandings(
            List<StandingView> rows
    ) {
        var unique =
                new java.util.LinkedHashMap<String, StandingView>();

        for (StandingView row : rows) {
            String key =
                    normalize(
                            row.group()
                    )
                            + "|"
                            + normalize(
                            row.team().name()
                    );

            StandingView previous =
                    unique.get(
                            key
                    );

            if (
                    previous == null
                            || isMoreCompleteStanding(
                            row,
                            previous
                    )
            ) {
                unique.put(
                        key,
                        row
                );
            }
        }

        return List.copyOf(
                unique.values()
        );
    }

    private boolean isMoreCompleteStanding(
            StandingView candidate,
            StandingView current
    ) {
        int candidatePlayed =
                candidate.played() == null
                        ? -1
                        : candidate.played();

        int currentPlayed =
                current.played() == null
                        ? -1
                        : current.played();

        if (
                candidatePlayed
                        != currentPlayed
        ) {
            return candidatePlayed
                    > currentPlayed;
        }

        return populatedStandingFields(
                candidate
        )
                > populatedStandingFields(
                current
        );
    }

    private int populatedStandingFields(
            StandingView row
    ) {
        int result = 0;

        if (row.position() != null) result++;
        if (row.played() != null) result++;
        if (row.wins() != null) result++;
        if (row.draws() != null) result++;
        if (row.losses() != null) result++;
        if (row.goalsFor() != null) result++;
        if (row.goalsAgainst() != null) result++;
        if (row.goalDifference() != null) result++;
        if (row.points() != null) result++;
        if (row.average() != null) result++;
        if (row.goalsForAverage() != null) result++;
        if (row.goalsAgainstAverage() != null) result++;
        if (row.technicalIndex() != null) result++;

        return result;
    }

    private List<TopScorerView> scorersForPhase(
            SportsSnapshot snapshot,
            String requestedPhase
    ) {
        String phase =
                hasText(requestedPhase)
                        ? requestedPhase
                        : currentPhase(
                        snapshot
                );

        if (!hasText(phase)) {
            return snapshot.topScorers();
        }

        List<TopScorerView> result =
                snapshot.topScorers()
                        .stream()
                        .filter(row ->
                                contains(
                                        row.phase(),
                                        phase
                                )
                        )
                        .toList();

        if (
                result.isEmpty()
                        && !hasText(requestedPhase)
        ) {
            return snapshot.topScorers();
        }

        return result;
    }

    private String currentPhase(
            SportsSnapshot snapshot
    ) {
        Instant today =
                startOfToday();

        String nextPhase =
                snapshot.matches()
                        .stream()
                        .filter(match ->
                                match.status()
                                        == MatchStatus.SCHEDULED
                        )
                        .filter(match ->
                                match.scheduledAt() != null
                        )
                        .filter(match ->
                                !match.scheduledAt()
                                        .isBefore(today)
                        )
                        .filter(match ->
                                hasText(
                                        match.phase()
                                )
                        )
                        .min(
                                Comparator.comparing(
                                        MatchView::scheduledAt
                                )
                        )
                        .map(
                                MatchView::phase
                        )
                        .orElse(null);

        if (nextPhase != null) {
            return nextPhase;
        }

        String undatedPhase =
                snapshot.matches()
                        .stream()
                        .filter(match ->
                                match.status()
                                        == MatchStatus.SCHEDULED
                        )
                        .filter(match ->
                                match.scheduledAt() == null
                        )
                        .map(
                                MatchView::phase
                        )
                        .filter(
                                this::hasText
                        )
                        .findFirst()
                        .orElse(null);

        if (undatedPhase != null) {
            return undatedPhase;
        }

        String latestFinishedPhase =
                snapshot.matches()
                        .stream()
                        .filter(match ->
                                match.status()
                                        == MatchStatus.FINISHED
                        )
                        .filter(match ->
                                match.scheduledAt() != null
                        )
                        .filter(match ->
                                hasText(
                                        match.phase()
                                )
                        )
                        .max(
                                Comparator.comparing(
                                        MatchView::scheduledAt
                                )
                        )
                        .map(
                                MatchView::phase
                        )
                        .orElse(null);

        if (latestFinishedPhase != null) {
            return latestFinishedPhase;
        }

        return snapshot.standings()
                .stream()
                .map(
                        StandingView::phase
                )
                .filter(
                        this::hasText
                )
                .findFirst()
                .orElse(null);
    }

    private Instant startOfToday() {
        return LocalDate.now(
                        clock.withZone(
                                zoneId
                        )
                )
                .atStartOfDay(
                        zoneId
                )
                .toInstant();
    }

    private boolean withinRange(
            MatchView match,
            LocalDate from,
            LocalDate to
    ) {
        if (
                match.scheduledAt() == null
        ) {
            return true;
        }

        LocalDate date =
                match.scheduledAt()
                        .atZone(
                                zoneId
                        )
                        .toLocalDate();

        return (
                from == null
                        || !date.isBefore(
                        from
                )
        )
                && (
                to == null
                        || !date.isAfter(
                        to
                )
        );
    }

    private void validateDates(
            LocalDate from,
            LocalDate to
    ) {
        if (
                from != null
                        && to != null
                        && from.isAfter(to)
        ) {
            throw Errors.badRequest(
                    "SPORTS_DATE_RANGE_INVALID",
                    "A data inicial deve ser anterior à data final."
            );
        }
    }

    private boolean contains(
            String actual,
            String expected
    ) {
        return expected == null
                || expected.isBlank()
                || normalize(actual)
                .contains(
                        normalize(expected)
                );
    }

    private boolean same(
            String first,
            String second
    ) {
        return normalize(first)
                .equals(
                        normalize(second)
                );
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private String normalize(
            String value
    ) {
        String prepared =
                (
                        value == null
                                ? ""
                                : value
                )
                        .replace(
                                'ª',
                                'a'
                        )
                        .replace(
                                'º',
                                'o'
                        );

        return Normalizer.normalize(
                        prepared,
                        Normalizer.Form.NFD
                )
                .replaceAll(
                        "\\p{M}",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^\\p{L}\\p{N}]+",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }
}
