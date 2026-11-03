package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardPopularVotingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/awards/editions/{editionId}/popular-voting")
public class AdminAwardPopularVotingController {
    private final AwardPopularVotingService service;

    public AdminAwardPopularVotingController(AwardPopularVotingService service) { this.service = service; }

    @GetMapping("/window")
    public AwardPopularVotingWindowResponse window(@PathVariable UUID editionId) {
        return service.adminWindow(editionId);
    }

    @PostMapping("/window")
    public AwardPopularVotingWindowResponse schedule(@PathVariable UUID editionId,
                                                     @Valid @RequestBody AwardPopularWindowRequest request) {
        return service.schedule(editionId, request);
    }

    @PostMapping("/close")
    public AwardPopularVotingWindowResponse close(@PathVariable UUID editionId) {
        return service.close(editionId);
    }
}
