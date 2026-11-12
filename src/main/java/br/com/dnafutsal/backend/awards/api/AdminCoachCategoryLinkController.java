package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachCategoryLinkService;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/awards/editions/{editionId}/coach-category-links")
public class AdminCoachCategoryLinkController {
    private final CoachCategoryLinkService service;

    public AdminCoachCategoryLinkController(CoachCategoryLinkService service) {
        this.service = service;
    }

    public record Request(@Positive long eventId, @Positive long divisionId,
                          @Positive long categoryId, @NotNull AwardRegistrationGender gender,
                          @NotNull Instant expiresAt) { }

    @GetMapping
    public List<CoachCategoryLinkService.LinkView> list(@PathVariable UUID editionId) {
        return service.list(editionId);
    }

    @PostMapping
    public ResponseEntity<CoachCategoryLinkService.CreatedLink> create(
            @PathVariable UUID editionId, @Valid @RequestBody Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(editionId,
                new CoachCategoryLinkService.CreateRequest(request.eventId(), request.divisionId(),
                        request.categoryId(), request.gender(), request.expiresAt())));
    }

    @PostMapping("/{linkId}/revoke")
    public CoachCategoryLinkService.LinkView revoke(@PathVariable UUID editionId,
                                                    @PathVariable UUID linkId) {
        return service.revoke(editionId, linkId);
    }
}
