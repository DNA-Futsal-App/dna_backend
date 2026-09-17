
package br.com.dnafutsal.backend.awards.api;

import java.util.UUID;

public record AwardAuditIssueResponse(
        UUID ballotId,
        String code,
        String detail
) {
}
