package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative classification: only explicit official Chave/Grupo Ouro labels qualify. */
final class GoldKeyClassifier {
    private static final Pattern KEY = Pattern.compile("\\b(?:chave|grupo)\\s+(ouro|prata|bronze)\\b");

    private GoldKeyClassifier() { }

    static Analysis analyze(SportsSnapshot snapshot, String stage, List<TeamView> catalogTeams) {
        Map<String, Set<String>> keysByTeam = new HashMap<>();
        Set<String> unresolved = new TreeSet<>();

        for (StandingView row : snapshot.standings()) {
            if (!stage.equals(stageOf(snapshot, row.phase()))) continue;
            String key = keyOf(row.group());
            if (key == null) key = keyOf(row.phase());
            if (key != null) {
                if (row.team() == null || row.team().id() == null || row.team().id().isBlank()) {
                    unresolved.add("classificacao-sem-id");
                } else {
                    observe(keysByTeam, row.team(), key);
                }
            }
        }
        for (MatchView match : snapshot.matches()) {
            if (!stage.equals(stageOf(snapshot, match.phase()))) continue;
            String key = keyOf(match.phase());
            if (key == null) continue;
            if (match.homeTeam() == null && match.awayTeam() == null) {
                unresolved.add("jogo-sem-equipes");
            }
            observe(keysByTeam, match.homeTeam(), key);
            observe(keysByTeam, match.awayTeam(), key);
        }

        Map<String, TeamView> officialTeams = new LinkedHashMap<>();
        for (TeamView team : catalogTeams) {
            if (team != null && team.id() != null && !team.id().isBlank()) {
                officialTeams.putIfAbsent(team.id(), team);
            }
        }
        List<TeamView> eligible = new ArrayList<>();
        Set<String> ambiguous = new TreeSet<>();
        for (Map.Entry<String, Set<String>> entry : keysByTeam.entrySet()) {
            if (!entry.getValue().contains("ouro")) continue;
            if (entry.getValue().size() > 1) {
                ambiguous.add(entry.getKey());
            } else if (officialTeams.containsKey(entry.getKey())) {
                eligible.add(officialTeams.get(entry.getKey()));
            } else {
                unresolved.add(entry.getKey());
            }
        }
        eligible.sort(Comparator.comparing(TeamView::name,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(TeamView::id));
        return new Analysis(List.copyOf(eligible), List.copyOf(ambiguous), List.copyOf(unresolved));
    }

    private static void observe(Map<String, Set<String>> keysByTeam,
                                TeamView team, String key) {
        if (team == null || team.id() == null || team.id().isBlank()) return;
        keysByTeam.computeIfAbsent(team.id(), ignored -> new HashSet<>()).add(key);
    }

    private static String keyOf(String label) {
        Matcher matcher = KEY.matcher(normalize(label));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String stageOf(SportsSnapshot snapshot, String phase) {
        if (normalize(snapshot.event().title()).contains("torneio uniao")) return "UNIAO";
        return normalize(phase).startsWith("torneio uniao") ? "UNIAO" : "PAULISTA";
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    record Analysis(List<TeamView> teams, List<String> ambiguousTeamIds, List<String> unresolvedTeams) {
        boolean ready() {
            return !teams.isEmpty() && ambiguousTeamIds.isEmpty() && unresolvedTeams.isEmpty();
        }
    }
}
