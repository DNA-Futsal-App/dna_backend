package br.com.dnafutsal.backend.awards.api;
import br.com.dnafutsal.backend.awards.application.CoachVotingV2Service;
import jakarta.validation.Valid; import jakarta.validation.constraints.Size; import org.springframework.http.*; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*; import java.util.*;
@Validated @RestController @RequestMapping("/api/v1/awards/coach-voting-v2")
public class CoachVotingV2Controller {
 private final CoachVotingV2Service voting; public CoachVotingV2Controller(CoachVotingV2Service voting){this.voting=voting;}
 @GetMapping("/context") CoachVotingContextResponse context(@RequestParam UUID contextId){return voting.context(contextId);}
 @GetMapping("/candidates") List<CoachVotingCandidateResponse> candidates(@RequestParam UUID contextId,@RequestParam UUID voteCategoryId,@RequestParam @Size(min=1,max=100) String teamId){return voting.candidates(contextId,voteCategoryId,teamId);}
 @GetMapping("/ballot") CoachBallotResponse ballot(@RequestParam UUID contextId){return voting.ballot(contextId);}
 @PostMapping("/ballot") ResponseEntity<CoachBallotResponse> submit(@Valid @RequestBody SubmitCoachBallotV2Request request){return ResponseEntity.status(HttpStatus.CREATED).body(voting.submit(request));}
}
