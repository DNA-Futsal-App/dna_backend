package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/awards/coach-invites")
public class CoachInviteController {

    private final CoachInviteService coachInvites;

    public CoachInviteController(CoachInviteService coachInvites) {
        this.coachInvites = coachInvites;
    }

    @PostMapping("/claim")
    ResponseEntity<CoachAccessResponse> claim(
            @Valid @RequestBody ClaimCoachInviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coachInvites.claim(request));
    }
}
