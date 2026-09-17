package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachInviteService;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/public/awards/coach-invites")
public class PublicCoachInviteController {

    private final CoachInviteService coachInvites;

    public PublicCoachInviteController(
            CoachInviteService coachInvites
    ) {
        this.coachInvites = coachInvites;
    }

    @GetMapping("/{token}")
    CoachInviteResponse inspect(
            @PathVariable
            @Size(min = 20, max = 200)
            String token
    ) {
        return coachInvites.inspect(token);
    }
}
