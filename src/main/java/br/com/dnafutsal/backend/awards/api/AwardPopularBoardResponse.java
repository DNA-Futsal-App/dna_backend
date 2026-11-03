package br.com.dnafutsal.backend.awards.api;

import java.util.List;

public record AwardPopularBoardResponse(String status, List<AwardPopularFinalistResponse> finalists,
                                        Long totalVotes) { }
