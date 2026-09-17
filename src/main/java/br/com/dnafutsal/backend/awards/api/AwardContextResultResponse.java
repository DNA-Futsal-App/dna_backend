
package br.com.dnafutsal.backend.awards.api;

import java.util.List;

public record AwardContextResultResponse(
        long eventId,
        long divisionId,
        long categoryId,
        int ballotsSubmitted,
        List<AwardCategoryResultResponse> categories
) {
}
