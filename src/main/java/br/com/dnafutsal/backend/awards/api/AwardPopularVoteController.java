package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardPopularVotingService;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/awards/popular-voting/editions/{editionId}")
public class AwardPopularVoteController {
    private final AwardPopularVotingService service;

    public AwardPopularVoteController(AwardPopularVotingService service) { this.service = service; }

    @GetMapping("/my-vote")
    public AwardPopularVoteResponse myVote(@PathVariable UUID editionId,
             @RequestParam @Min(1) long eventId, @RequestParam @Min(1) long divisionId,
             @RequestParam @Min(1) long categoryId,
             @RequestParam AwardRegistrationGender gender,
             @RequestParam AwardRegistrationContestCategory contestCategory) {
        return service.myVote(editionId, new AwardPopularContext(eventId, divisionId, categoryId, gender, contestCategory));
    }

    @PostMapping("/vote")
    public ResponseEntity<AwardPopularVoteResponse> vote(@PathVariable UUID editionId,
                                                         @Valid @RequestBody AwardPopularVoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.vote(editionId, request));
    }
}
