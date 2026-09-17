package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;

import java.util.UUID;

public record AwardVoteCategoryResponse(
        UUID id,
        String code,
        String label,
        String targetType,
        String positionCode,
        int displayOrder,
        boolean required
) {

    public static AwardVoteCategoryResponse from(
            AwardVoteCategory category
    ) {
        return new AwardVoteCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getLabel(),
                category.getTargetType().name(),
                category.getPositionCode(),
                category.getDisplayOrder(),
                category.isRequired()
        );
    }
}
