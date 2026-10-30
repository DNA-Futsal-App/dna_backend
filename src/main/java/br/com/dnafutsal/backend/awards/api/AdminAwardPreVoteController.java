package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardPreVoteService;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/awards/editions/{editionId}/pre-vote")
public class AdminAwardPreVoteController {
    private final AwardPreVoteService preVote;

    public AdminAwardPreVoteController(AwardPreVoteService preVote) {
        this.preVote = preVote;
    }

    @GetMapping("/contexts")
    public List<AwardPreVoteContextResponse> contexts(@PathVariable UUID editionId) {
        return preVote.contexts(editionId);
    }

    @GetMapping
    public AwardPreVoteBoardResponse board(@PathVariable UUID editionId,
                                           @RequestParam @Min(1) long eventId,
                                           @RequestParam @Min(1) long divisionId,
                                           @RequestParam @Min(1) long categoryId,
                                           @RequestParam AwardRegistrationGender gender,
                                           @RequestParam AwardRegistrationContestCategory contestCategory) {
        return preVote.board(editionId,
                new AwardPreVoteContextRequest(eventId, divisionId, categoryId, gender, contestCategory));
    }

    @PostMapping("/entries/{entryId}/score")
    public AwardPreVoteBoardResponse score(@PathVariable UUID editionId,
                                            @PathVariable UUID entryId,
                                            @RequestBody @Valid AwardPreVoteScoreRequest request) {
        return preVote.score(editionId, entryId, request.score());
    }

    @PostMapping("/finalize")
    public AwardPreVoteBoardResponse finalizeContext(@PathVariable UUID editionId,
                                                      @RequestBody @Valid AwardPreVoteContextRequest request) {
        return preVote.finalizeContext(editionId, request);
    }
}
