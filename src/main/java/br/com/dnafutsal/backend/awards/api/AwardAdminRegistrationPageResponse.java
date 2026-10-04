package br.com.dnafutsal.backend.awards.api;

import java.util.List;

public record AwardAdminRegistrationPageResponse(
        List<AwardAdminRegistrationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long pendingReview,
        long approved,
        long rejected
) {
}
