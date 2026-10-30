package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AwardPreVoteScoreRequest(@Min(1) @Max(10) int score) {}
