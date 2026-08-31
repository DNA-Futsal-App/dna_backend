package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
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
                        "FINISHED".equals(
                                match.status()
                        )
                )
                .sorted(
                        Comparator.comparing(
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
                        "SCHEDULED".equals(
                                match.status()
                        )
                )
                .filter(match ->
                        match.scheduledAt() == null
                                || !match.scheduledAt()
                                .isBefore(today)
                )
                .sorted(
                        Comparator.comparing(
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
        String phase =
                hasText(requestedPhase)
                        ? requestedPhase
                        : currentPhase(
                        snapshot
                );

        if (!hasText(phase)) {
            return snapshot.standings();
        }

        List<StandingView> result =
                snapshot.standings()
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
            return snapshot.standings();
        }

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
                                "SCHEDULED".equals(
                                        match.status()
                                )
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
                                "SCHEDULED".equals(
                                        match.status()
                                )
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
                                "FINISHED".equals(
                                        match.status()
                                )
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