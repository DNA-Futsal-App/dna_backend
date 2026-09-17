
package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AwardAdminResultsResponse(
        UUID editionId,
        String editionName,
        int season,
        String status,
        Instant votingClosedAt,
        int ballotsSubmitted,
        int voteRows,
        boolean integrityOk,
        List<AwardContextResultResponse> contexts
) {
}
