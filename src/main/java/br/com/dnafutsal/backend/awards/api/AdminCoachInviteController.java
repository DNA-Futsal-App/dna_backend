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
@RequestMapping("/api/v1/admin/awards/coach-invites")
public class AdminCoachInviteController {

    private final CoachInviteService coachInvites;

    public AdminCoachInviteController(
            CoachInviteService coachInvites
    ) {
        this.coachInvites = coachInvites;
    }

    @PostMapping
    ResponseEntity<CreateCoachInviteResponse> create(
            @Valid @RequestBody CreateCoachInviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coachInvites.create(request));
    }
}
