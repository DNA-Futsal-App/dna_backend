package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;
import java.util.List;

/** Official labels are preserved; no fixed enum of competitive brackets. */
public record CompetitionStructureView(
        long eventId,
        String title,
        List<Stage> stages
) implements Serializable {
    public record Stage(String id, String label, List<Phase> phases) implements Serializable { }
    public record Phase(
            String name,
            List<String> groups,
            List<String> brackets,
            boolean knockout,
            boolean hasStandings,
            boolean hasScorers
    ) implements Serializable { }
}
