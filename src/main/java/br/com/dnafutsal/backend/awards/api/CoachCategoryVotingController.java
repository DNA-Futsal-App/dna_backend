package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachCategoryVotingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Approved-category voting; no endpoint here allows self-selection of a voting context. */
@Validated
@RestController
@RequestMapping("/api/v1/awards/coach-category-voting")
public class CoachCategoryVotingController {

    private final CoachCategoryVotingService voting;

    public CoachCategoryVotingController(CoachCategoryVotingService voting) {
        this.voting = voting;
    }

    @GetMapping("/contexts")
    public List<CoachCategoryVotingService.ContextSummary> contexts() {
        return voting.contexts();
    }

    @GetMapping("/context")
    public CoachVotingContextResponse context(@RequestParam UUID contextId) {
        return voting.context(contextId);
    }

    @GetMapping("/candidates")
    public List<CoachVotingCandidateResponse> candidates(@RequestParam UUID contextId,
                                                          @RequestParam UUID voteCategoryId,
                                                          @RequestParam @Size(min = 1, max = 100) String teamId) {
        return voting.candidates(contextId, voteCategoryId, teamId);
    }

    @GetMapping("/ballot")
    public CoachBallotResponse ballot(@RequestParam UUID contextId) {
        return voting.ballot(contextId);
    }

    @PostMapping("/ballot")
    public ResponseEntity<CoachBallotResponse> submit(
            @Valid @RequestBody SubmitCoachBallotV2Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voting.submit(request));
    }
}
