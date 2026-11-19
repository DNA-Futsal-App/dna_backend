package br.com.dnafutsal.backend.awards.api;

import java.util.List;
import java.util.UUID;

public record AwardPreVoteTieResponse(
        boolean tiedAtCutoff,
        boolean published,
        Integer decidedCount,
        List<UUID> selectedEntryIds,
        List<Candidate> candidates
) {
    public record Candidate(UUID entryId, String athleteName, String teamName,
                            double average, boolean compulsory, boolean eligible) {}
}
