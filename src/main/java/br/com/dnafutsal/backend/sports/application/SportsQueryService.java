package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SportsQueryService {

    private final SportsSnapshotService snapshots;
    private final ZoneId zoneId;

    public SportsQueryService(SportsSnapshotService snapshots, AppProperties properties) {
        this.snapshots = snapshots;
        this.zoneId = properties.zoneId();
    }

    public List<MatchView> playedMatches(SportsFilter filter, String phase, LocalDate from, LocalDate to) {
        validateDates(from, to);
        return matches(filter, phase, from, to).stream()
                .filter(match -> "FINISHED".equals(match.status()))
                .toList();
    }

    public List<MatchView> upcomingMatches(SportsFilter filter, String phase, LocalDate from, LocalDate to) {
        validateDates(from, to);
        return matches(filter, phase, from, to).stream()
                .filter(match -> "SCHEDULED".equals(match.status()))
                .toList();
    }

    public List<StandingView> standings(SportsFilter filter, String phase, String group) {
        return snapshots.snapshot(filter.eventId()).standings().stream()
                .filter(row -> contains(row.phase(), phase))
                .filter(row -> contains(row.group(), group))
                .filter(row -> filter.teamId() == null || filter.teamId().equals(row.team().id()))
                .toList();
    }

    public List<TopScorerView> topScorers(SportsFilter filter, String phase, int limit) {
        List<TopScorerView> filtered = snapshots.snapshot(filter.eventId()).topScorers().stream()
                .filter(scorer -> contains(scorer.phase(), phase))
                .filter(scorer -> filter.teamId() == null || filter.teamId().equals(scorer.team().id()))
                .limit(limit)
                .toList();
        List<TopScorerView> ranked = new ArrayList<>(filtered.size());
        for (int index = 0; index < filtered.size(); index++) {
            TopScorerView scorer = filtered.get(index);
            ranked.add(new TopScorerView(index + 1, scorer.phase(), scorer.athleteName(),
                    scorer.athleteImageUrl(), scorer.team(), scorer.goals(), scorer.personalDataSuppressed()));
        }
        return List.copyOf(ranked);
    }

    private List<MatchView> matches(SportsFilter filter, String phase, LocalDate from, LocalDate to) {
        return snapshots.snapshot(filter.eventId()).matches().stream()
                .filter(match -> contains(match.phase(), phase))
                .filter(match -> filter.teamId() == null
                        || filter.teamId().equals(match.homeTeam().id())
                        || filter.teamId().equals(match.awayTeam().id()))
                .filter(match -> withinRange(match, from, to))
                .toList();
    }

    private boolean withinRange(MatchView match, LocalDate from, LocalDate to) {
        if (match.scheduledAt() == null) {
            return true;
        }
        LocalDate date = match.scheduledAt().atZone(zoneId).toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw Errors.badRequest("SPORTS_DATE_RANGE_INVALID", "A data inicial deve ser anterior à data final.");
        }
    }

    private boolean contains(String actual, String expected) {
        return expected == null || expected.isBlank() || normalize(actual).contains(normalize(expected));
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
}
