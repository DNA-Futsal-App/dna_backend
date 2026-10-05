package br.com.dnafutsal.backend.awards.api;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.*;
public record SubmitCoachBallotV2Request(@NotNull UUID contextId,@NotEmpty List<@Valid CoachBallotVoteRequest> votes){}
