package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import java.util.UUID;

public record AwardPreVoteEntryResponse(
        UUID entryId,
        UUID registrationId,
        long registrationNumber,
        String athleteName,
        String athleteInstagram,
        String teamName,
        AwardRegistrationMediaSource sourceType,
        String externalUrl,
        int scoresCount,
        Double average,
        Integer myScore,
        Integer finalistRank
) {}
