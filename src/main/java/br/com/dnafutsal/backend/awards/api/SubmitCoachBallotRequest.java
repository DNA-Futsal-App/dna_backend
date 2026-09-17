package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubmitCoachBallotRequest(
        @NotEmpty List<@Valid CoachBallotVoteRequest> votes
) {
}
