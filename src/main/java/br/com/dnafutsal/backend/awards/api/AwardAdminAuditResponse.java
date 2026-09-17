
package br.com.dnafutsal.backend.awards.api;

import java.util.List;
import java.util.UUID;

public record AwardAdminAuditResponse(
        UUID editionId,
        String editionName,
        String status,
        int ballotsSubmitted,
        int voteRows,
        int requiredCategories,
        int completeBallots,
        int invalidBallots,
        boolean integrityOk,
        List<AwardAuditIssueResponse> issues
) {
}
