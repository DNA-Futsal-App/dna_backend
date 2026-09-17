package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachBallotService;
import br.com.dnafutsal.backend.awards.application.CoachVotingQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/awards/coach-voting")
public class CoachVotingController {

    private final CoachVotingQueryService queries;
    private final CoachBallotService ballots;

    public CoachVotingController(
            CoachVotingQueryService queries,
            CoachBallotService ballots
    ) {
        this.queries = queries;
        this.ballots = ballots;
    }

    @GetMapping("/context")
    CoachVotingContextResponse context() {
        return queries.context();
    }

    @GetMapping("/candidates")
    List<CoachVotingCandidateResponse> candidates(
            @RequestParam UUID voteCategoryId,
            @RequestParam
            @Size(min = 1, max = 100)
            String teamId
    ) {
        return queries.candidates(
                voteCategoryId,
                teamId
        );
    }

    @GetMapping("/ballot")
    CoachBallotResponse ballot() {
        return queries.ballot();
    }

    @PostMapping("/ballot")
    ResponseEntity<CoachBallotResponse> submit(
            @Valid
            @RequestBody
            SubmitCoachBallotRequest request
    ) {
        return ResponseEntity.status(
                        HttpStatus.CREATED
                )
                .body(
                        ballots.submit(
                                request
                        )
                );
    }
}
