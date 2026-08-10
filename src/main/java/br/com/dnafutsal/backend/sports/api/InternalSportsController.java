package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.identity.api.MessageResponse;
import br.com.dnafutsal.backend.sports.application.SportsEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/sports")
public class InternalSportsController {

    private final SportsEventService events;

    public InternalSportsController(SportsEventService events) {
        this.events = events;
    }

    @PostMapping("/match-completed")
    ResponseEntity<MessageResponse> matchCompleted(@Valid @RequestBody MatchCompletedNotification event) {
        boolean accepted = events.accept(event);
        String message = accepted ? "Evento aceito; a atualização do snapshot esportivo foi disparada."
                : "Evento já processado; nenhuma atualização foi repetida.";
        return ResponseEntity.accepted().body(new MessageResponse(message));
    }
}
