package br.com.dnafutsal.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.awards")
public record AwardProperties(
        @NotNull Duration coachInviteTtl,
        @NotNull Duration coachInviteReservationTtl
) {

    @ConstructorBinding
    public AwardProperties(
            Duration coachInviteTtl,
            Duration coachInviteReservationTtl
    ) {
        this.coachInviteTtl = coachInviteTtl;
        this.coachInviteReservationTtl = coachInviteReservationTtl;
    }

    public AwardProperties(
            Duration coachInviteTtl
    ) {
        this(
                coachInviteTtl,
                Duration.ofDays(2)
        );
    }
}
