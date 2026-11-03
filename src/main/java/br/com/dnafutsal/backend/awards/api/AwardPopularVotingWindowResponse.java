package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.UUID;

public record AwardPopularVotingWindowResponse(UUID editionId, String editionName, int season,
                                               String status, Instant opensAt, Instant closesAt,
                                               Instant manuallyClosedAt) { }
