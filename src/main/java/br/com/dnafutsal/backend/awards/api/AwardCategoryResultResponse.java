
package br.com.dnafutsal.backend.awards.api;

import java.util.List;
import java.util.UUID;

public record AwardCategoryResultResponse(
        UUID voteCategoryId,
        String code,
        String label,
        String targetType,
        String positionCode,
        int totalVotes,
        List<AwardCandidateResultResponse> candidates
) {
}
