package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.*;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scopes official snapshot data without creating new sporting persistence. */
@Service
public class SportsCompetitionService {
    private static final Pattern BRACKET = Pattern.compile("(?iu)\\bchave\\s+([\\p{L}\\p{N}][\\p{L}\\p{N} -]*)");
    private static final Pattern ROUND_BRACKET = Pattern.compile(
            "(?iu)\\b(?:oitavas?|quartas?)(?:\\s+de)?(?:\\s+final)?\\s+([\\p{L}\\p{N}][\\p{L}\\p{N} -]*)$");

    private final SportsSnapshotService snapshots;
    private final SportsQueryService legacy;

    public SportsCompetitionService(SportsSnapshotService snapshots, SportsQueryService legacy) {
        this.snapshots = snapshots;
        this.legacy = legacy;
    }

    public CompetitionStructureView structure(long eventId) {
        SportsSnapshot data = snapshots.snapshot(eventId);
        Map<String, LinkedHashMap<String, PhaseBuilder>> stages = new LinkedHashMap<>();
        stages.put("PAULISTA", new LinkedHashMap<>());
        stages.put("UNIAO", new LinkedHashMap<>());

        for (MatchView match : data.matches()) {
            phase(stages, scope(data, match.phase()), match.phase()).hasMatches = true;
        }
        for (StandingView row : data.standings()) {
            PhaseBuilder builder = phase(stages, scope(data, row.phase()), row.phase());
            builder.hasStandings = true;
            if (hasText(row.group())) builder.groups.add(row.group().trim());
            addBracket(builder.brackets, row.group(), false);
        }
        for (TopScorerView row : data.topScorers()) {
            phase(stages, scope(data, row.phase()), row.phase()).hasScorers = true;
        }
        List<CompetitionStructureView.Stage> result = new ArrayList<>();
        for (var stage : stages.entrySet()) {
            if (stage.getValue().isEmpty()) continue;
            List<CompetitionStructureView.Phase> phases = new ArrayList<>();
            for (PhaseBuilder b : stage.getValue().values()) {
                phases.add(new CompetitionStructureView.Phase(b.name, List.copyOf(b.groups),
                        List.copyOf(b.brackets), isKnockout(b.name), b.hasStandings, b.hasScorers));
            }
            result.add(new CompetitionStructureView.Stage(stage.getKey(),
                    stage.getKey().equals("UNIAO") ? "Torneio União" : "Campeonato Paulista", List.copyOf(phases)));
        }
        return new CompetitionStructureView(eventId, data.event().title(), List.copyOf(result));
    }

    public List<MatchView> played(SportsFilter filter, String stage, String phase, LocalDate from, LocalDate to) {
        return filterMatches(filter, stage, phase, legacy.playedMatches(filter, null, from, to));
    }

    public List<MatchView> upcoming(SportsFilter filter, String stage, String phase, LocalDate from, LocalDate to) {
        return filterMatches(filter, stage, phase, legacy.upcomingMatches(filter, null, from, to));
    }

    public List<MatchView> pending(SportsFilter filter, String stage, String phase, LocalDate from, LocalDate to) {
        return filterMatches(filter, stage, phase, legacy.pendingResults(filter, null, from, to));
    }

    public MatchCalendarView calendar(SportsFilter filter, String stage, String phase, LocalDate from, LocalDate to) {
        MatchCalendarView source = legacy.matchCalendar(filter, null, from, to);
        List<MatchView> played = filterMatches(filter, stage, phase, source.played());
        List<MatchView> upcoming = filterMatches(filter, stage, phase, source.upcoming());
        List<MatchView> pending = filterMatches(filter, stage, phase, source.pendingResults());
        SportsScheduleState state = !upcoming.isEmpty() ? SportsScheduleState.ACTIVE
                : (!played.isEmpty() || !pending.isEmpty()) ? SportsScheduleState.AWAITING_SCHEDULE
                : SportsScheduleState.NO_GAMES;
        String currentPhase = !upcoming.isEmpty() ? upcoming.get(0).phase()
                : !pending.isEmpty() ? pending.get(0).phase()
                : !played.isEmpty() ? played.get(0).phase() : null;
        return new MatchCalendarView(currentPhase, state, played, upcoming, pending);
    }

    public List<StandingView> standings(SportsFilter filter, String stage, String phase, String group) {
        SportsSnapshot snapshot = snapshots.snapshot(filter.eventId());
        checkStage(stage);
        // The legacy default phase may belong to União when both competitions
        // share an eventId. Select only a classification phase of this stage.
        if ("PAULISTA".equals(stage) && !hasText(phase) && !hasText(group)) {
            phase = defaultPaulistaClassification(snapshot);
        }
        final String selectedPhase = phase;
        // Union groups and official classification brackets may coexist in one event.
        // Never pick a 'current classification phase' from the other competition.
        Map<String, StandingView> unique = new LinkedHashMap<>();
        for (StandingView row : snapshot.standings()) {
            if (!stage.equals(scope(snapshot, row.phase())) || !requested(row.phase(), selectedPhase)
                    || !requested(row.group(), group)) continue;
            if (hasText(filter.teamId()) && (row.team() == null || !filter.teamId().equals(row.team().id()))) continue;
            if (row.team() == null) continue;
            // Row identity is phase + official group + team, never just team.
            String teamKey = hasText(row.team().id()) ? row.team().id() : normalize(row.team().name());
            String key = normalize(row.phase()) + "|" + normalize(row.group()) + "|" + teamKey;
            unique.merge(key, row, this::preferCompleteStanding);
        }
        return unique.values().stream().sorted(Comparator
                .comparing(StandingView::phase, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(StandingView::group, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(StandingView::position, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String defaultPaulistaClassification(SportsSnapshot snapshot) {
        Map<String, List<StandingView>> phases = new LinkedHashMap<>();
        for (StandingView row : snapshot.standings()) {
            if (!"PAULISTA".equals(scope(snapshot, row.phase())) || !hasText(row.phase())) continue;
            String label = normalize(row.phase());
            if (!label.contains("classific") || isKnockout(row.phase())) continue;
            phases.computeIfAbsent(label, ignored -> new ArrayList<>()).add(row);
        }
        // Keep the legacy preference for the most comprehensive official
        // classification, but only among Paulista phases of this event.
        return phases.values().stream()
                .max(Comparator.comparingInt((List<StandingView> rows) -> (int) rows.stream()
                                .filter(row -> row.team() != null)
                                .map(row -> hasText(row.team().id()) ? row.team().id() : normalize(row.team().name()))
                                .distinct().count())
                        .thenComparingInt(rows -> rows.stream()
                                .mapToInt(row -> row.played() == null ? 0 : row.played()).sum()))
                .map(rows -> rows.get(0).phase())
                .orElse(null);
    }

    private StandingView preferCompleteStanding(StandingView left, StandingView right) {
        int first = left.played() == null ? -1 : left.played();
        int second = right.played() == null ? -1 : right.played();
        if (first != second) return second > first ? right : left;
        // Prefer the row with the more complete official numeric fields.
        int leftFields = standingFields(left);
        int rightFields = standingFields(right);
        return rightFields > leftFields ? right : left;
    }

    private int standingFields(StandingView row) {
        int count = 0;
        if (row.position() != null) count++;
        if (row.played() != null) count++;
        if (row.wins() != null) count++;
        if (row.draws() != null) count++;
        if (row.losses() != null) count++;
        if (row.goalsFor() != null) count++;
        if (row.goalsAgainst() != null) count++;
        if (row.goalDifference() != null) count++;
        if (row.points() != null) count++;
        return count;
    }

    public List<TopScorerView> scorers(SportsFilter filter, String stage, String phase, int limit) {
        SportsSnapshot snapshot = snapshots.snapshot(filter.eventId());
        checkStage(stage);
        List<TopScorerView> source = snapshot.topScorers().stream()
                .filter(row -> stage.equals(scope(snapshot, row.phase())) && requested(row.phase(), phase))
                .filter(row -> !hasText(filter.teamId()) || row.team() != null && filter.teamId().equals(row.team().id()))
                .sorted(Comparator.comparing(TopScorerView::goals, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit).toList();
        List<TopScorerView> ranked = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            TopScorerView row = source.get(i);
            ranked.add(new TopScorerView(i + 1, row.phase(), row.athleteName(), row.athleteImageUrl(),
                    row.team(), row.goals(), row.personalDataSuppressed()));
        }
        return List.copyOf(ranked);
    }

    private List<MatchView> filterMatches(SportsFilter filter, String stage, String phase, List<MatchView> matches) {
        checkStage(stage);
        SportsSnapshot snapshot = snapshots.snapshot(filter.eventId());
        return matches.stream().filter(row -> stage.equals(scope(snapshot, row.phase()))
                && requested(row.phase(), phase)).toList();
    }

    private String scope(SportsSnapshot data, String phase) {
        if (normalize(data.event().title()).contains("torneio uniao")) return "UNIAO";
        return normalize(phase).startsWith("torneio uniao") ? "UNIAO" : "PAULISTA";
    }

    private PhaseBuilder phase(Map<String, LinkedHashMap<String, PhaseBuilder>> stages, String stage, String name) {
        String label = hasText(name) ? name.trim() : "Geral";
        PhaseBuilder builder = stages.get(stage).computeIfAbsent(normalize(label), ignored -> new PhaseBuilder(label));
        addBracket(builder.brackets, label, true);
        return builder;
    }

    private void addBracket(Set<String> brackets, String value, boolean phase) {
        if (!hasText(value)) return;
        Matcher matcher = (phase ? ROUND_BRACKET : BRACKET).matcher(value.trim());
        if (matcher.find()) {
            String candidate = phase ? matcher.group(1).trim() : matcher.group().trim();
            // A generic round does not identify a bracket named Final.
            if (!phase || (!normalize(candidate).equals("final") && !normalize(candidate).equals("de final"))) {
                brackets.add(phase ? "Chave " + candidate : candidate);
            }
        }
        if (phase) {
            matcher = BRACKET.matcher(value);
            if (matcher.find()) brackets.add(matcher.group().trim());
        }
    }

    private boolean isKnockout(String value) {
        String n = normalize(value);
        return n.contains("oitav") || n.contains("quart") || n.contains("semifinal") || n.contains("semi final")
                || n.equals("final") || n.endsWith(" final") || n.contains("mata mata") || n.contains("eliminatoria");
    }

    private boolean requested(String actual, String expected) {
        return !hasText(expected) || normalize(actual).equals(normalize(expected));
    }

    private void checkStage(String stage) {
        if (!"UNIAO".equals(stage) && !"PAULISTA".equals(stage)) {
            throw Errors.badRequest("SPORTS_STAGE_INVALID", "Etapa inválida. Use PAULISTA ou UNIAO.");
        }
    }

    private String normalize(String input) {
        return Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static final class PhaseBuilder {
        final String name;
        final Set<String> groups = new LinkedHashSet<>();
        final Set<String> brackets = new LinkedHashSet<>();
        boolean hasMatches;
        boolean hasStandings;
        boolean hasScorers;
        PhaseBuilder(String name) { this.name = name; }
    }
}
