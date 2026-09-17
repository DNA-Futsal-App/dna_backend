package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;

import java.time.Instant;
import java.util.UUID;

public record AwardEditionAdminResponse(
        UUID id,
        String slug,
        String name,
        int season,
        String status,
        Instant votingOpensAt,
        Instant votingClosesAt
) {

    public static AwardEditionAdminResponse from(
            AwardEdition edition
    ) {
        return new AwardEditionAdminResponse(
                edition.getId(),
                edition.getSlug(),
                edition.getName(),
                edition.getSeason(),
                edition.getStatus().name(),
                edition.getVotingOpensAt(),
                edition.getVotingClosesAt()
        );
    }
}
