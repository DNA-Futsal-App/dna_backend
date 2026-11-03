package br.com.dnafutsal.backend.awards.api;

public record AwardPopularBatchItemResponse(AwardPopularContext context, String status,
        AwardPopularReadinessResponse readiness) { }
