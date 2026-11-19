package br.com.dnafutsal.backend.awards.application;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Seleciona somente atletas acima do corte e os empatados com o terceiro. */
public final class AwardTieDecisionPolicy {
    private AwardTieDecisionPolicy() {}

    public record Candidate(UUID entryId, long scoreSum, int scoreCount) {}

    private static int compare(Candidate first, Candidate second) {
        return Long.compare(first.scoreSum() * second.scoreCount(),
                second.scoreSum() * first.scoreCount());
    }

    public static boolean tiedAtCutoff(List<Candidate> ranking) {
        return ranking.size() >= 4 && ranking.stream().allMatch(c -> c.scoreCount() > 0)
                && compare(ranking.get(2), ranking.get(3)) == 0;
    }

    /** Respeita as medias; a ordem de desempate dos escolhidos permanece a do ranking. */
    public static Optional<List<UUID>> select(List<Candidate> ranking, int count, List<UUID> selected) {
        if (!tiedAtCutoff(ranking) || (count != 3 && count != 4) || selected == null
                || selected.size() != count || new HashSet<>(selected).size() != count) {
            return Optional.empty();
        }
        Candidate cutoff = ranking.get(2);
        Set<UUID> compulsory = new HashSet<>();
        Set<UUID> eligible = new HashSet<>();
        for (Candidate candidate : ranking) {
            int difference = compare(candidate, cutoff);
            if (difference > 0) compulsory.add(candidate.entryId());
            if (difference >= 0) eligible.add(candidate.entryId());
        }
        if (!selected.containsAll(compulsory) || !eligible.containsAll(selected)) {
            return Optional.empty();
        }
        Set<UUID> chosen = Set.copyOf(selected);
        return Optional.of(ranking.stream().map(Candidate::entryId)
                .filter(chosen::contains).toList());
    }
}
