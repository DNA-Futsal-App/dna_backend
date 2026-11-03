package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import java.util.UUID;

public record AwardPopularFinalistResponse(UUID entryId, String athleteName, String teamName,
                                           AwardRegistrationMediaSource sourceType, String externalUrl,
                                           Long votes) { }
