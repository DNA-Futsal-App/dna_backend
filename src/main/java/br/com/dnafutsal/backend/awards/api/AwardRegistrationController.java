package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/awards/registrations")
public class AwardRegistrationController {

    private final AwardRegistrationService registrations;

    public AwardRegistrationController(
            AwardRegistrationService registrations
    ) {
        this.registrations = registrations;
    }

    @GetMapping("/context")
    AwardRegistrationContextResponse context() {
        return registrations.context();
    }

    @GetMapping("/current")
    AwardRegistrationResponse current() {
        return registrations.current();
    }

    @PostMapping
    ResponseEntity<AwardRegistrationResponse> create(
            @Valid
            @RequestBody
            CreateAwardRegistrationRequest request
    ) {
        return ResponseEntity.status(
                        HttpStatus.CREATED
                )
                .body(
                        registrations.create(
                                request
                        )
                );
    }

    @PostMapping(
            "/{registrationId}/entries/{entryId}/upload-ticket"
    )
    AwardUploadTicketResponse createUploadTicket(
            @PathVariable
            UUID registrationId,

            @PathVariable
            UUID entryId,

            @Valid
            @RequestBody
            CreateAwardUploadTicketRequest request
    ) {
        return registrations.createUploadTicket(
                registrationId,
                entryId,
                request
        );
    }

    @PostMapping(
            "/{registrationId}/entries/{entryId}/complete-upload"
    )
    ResponseEntity<AwardRegistrationEntryResponse> completeUpload(
            @PathVariable
            UUID registrationId,

            @PathVariable
            UUID entryId
    ) {
        return ResponseEntity
                .accepted()
                .body(
                        registrations.completeUpload(
                                registrationId,
                                entryId
                        )
                );
    }

    @PutMapping(
            "/{registrationId}/entries/{entryId}/link"
    )
    AwardRegistrationEntryResponse updateLink(
            @PathVariable
            UUID registrationId,

            @PathVariable
            UUID entryId,

            @Valid
            @RequestBody
            UpdateAwardRegistrationLinkRequest request
    ) {
        return registrations.updateLink(
                registrationId,
                entryId,
                request
        );
    }

    @PostMapping("/{registrationId}/submit")
    AwardRegistrationResponse submit(
            @PathVariable
            UUID registrationId
    ) {
        return registrations.submit(
                registrationId
        );
    }

    @PostMapping(
            "/{registrationId}/entries/{entryId}/media-ticket"
    )
    AwardMediaTicketResponse createMediaTicket(
            @PathVariable
            UUID registrationId,

            @PathVariable
            UUID entryId
    ) {
        return registrations.createMediaTicket(
                registrationId,
                entryId
        );
    }

    @PostMapping("/{registrationId}/entries")
    ResponseEntity<AwardRegistrationEntryResponse> addEntry(
            @PathVariable
            UUID registrationId,

            @Valid
            @RequestBody
            CreateAwardRegistrationEntryRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        registrations.addEntry(
                                registrationId,
                                request
                        )
                );
    }

    @DeleteMapping(
            "/{registrationId}/entries/{entryId}"
    )
    AwardRegistrationResponse withdrawEntry(
            @PathVariable
            UUID registrationId,

            @PathVariable
            UUID entryId
    ) {
        return registrations.withdrawEntry(
                registrationId,
                entryId
        );
    }

    @DeleteMapping("/{registrationId}")
    AwardRegistrationResponse withdrawAll(
            @PathVariable
            UUID registrationId
    ) {
        return registrations.withdrawAll(
                registrationId
        );
    }
}
