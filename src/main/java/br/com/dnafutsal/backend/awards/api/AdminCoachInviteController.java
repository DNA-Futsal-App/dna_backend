package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachInviteService;
import jakarta.validation.Valid;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping("/{inviteId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
            @PathVariable UUID inviteId
    ) {
        coachInvites.revoke(inviteId);
    }
}
