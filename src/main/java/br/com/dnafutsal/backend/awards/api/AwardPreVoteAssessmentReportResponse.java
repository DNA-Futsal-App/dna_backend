package br.com.dnafutsal.backend.awards.api;

import java.util.List;

public record AwardPreVoteAssessmentReportResponse(
        AwardPreVoteContextRequest context,
        int activeAdministrators,
        int totalEntries,
        List<AwardPreVoteAssessmentEntryResponse> entries
) { }
