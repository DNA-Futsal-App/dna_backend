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
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/public/awards/popular-voting")
public class PublicAwardPopularVotingController {
    private final AwardPopularVotingService service;

    public PublicAwardPopularVotingController(AwardPopularVotingService service) {
        this.service = service;
    }

    @GetMapping("/editions")
    public List<AwardPopularVotingWindowResponse> editions() { return service.editions(); }

    @GetMapping("/editions/{editionId}/contexts")
    public List<AwardPopularContextResponse> contexts(@PathVariable UUID editionId) {
        return service.contexts(editionId);
    }

    @GetMapping("/editions/{editionId}/board")
    public ResponseEntity<AwardPopularBoardResponse> board(@PathVariable UUID editionId,
             @RequestParam @Min(1) long eventId, @RequestParam @Min(1) long divisionId,
             @RequestParam @Min(1) long categoryId,
             @RequestParam AwardRegistrationGender gender,
             @RequestParam AwardRegistrationContestCategory contestCategory) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(service.board(editionId, new AwardPopularContext(
                        eventId, divisionId, categoryId, gender, contestCategory)));
    }

    @GetMapping("/editions/{editionId}/media/{entryId}")
    public AwardMediaTicketResponse media(@PathVariable UUID editionId, @PathVariable UUID entryId) {
        return service.mediaTicket(editionId, entryId);
    }
}
