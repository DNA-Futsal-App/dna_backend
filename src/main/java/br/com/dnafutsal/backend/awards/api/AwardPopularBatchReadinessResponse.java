package br.com.dnafutsal.backend.awards.api;

import java.util.List;

public record AwardPopularBatchReadinessResponse(AwardPopularBatchSelection selection,
        boolean ready, List<AwardPopularBatchItemResponse> items) { }
