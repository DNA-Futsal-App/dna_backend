package br.com.dnafutsal.backend.awards.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwardCoachCredentialLifecycleTest {
    @Test
    void deactivatedCredentialCanBeReactivatedWithoutReplacingItsIdentity() {
        var credential = new AwardCoachCredential(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID id = credential.getId();
        credential.deactivate();
        assertThat(credential.isActive()).isFalse();
        credential.reactivate();
        assertThat(credential.isActive()).isTrue();
        assertThat(credential.getId()).isEqualTo(id);
    }

    @Test
    void removedCredentialMustNotBeReactivated() {
        var credential = new AwardCoachCredential(UUID.randomUUID(), UUID.randomUUID(), null);
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        credential.deactivate();
        credential.remove(now);
        assertThat(credential.isRemoved()).isTrue();
        assertThat(credential.getRemovedAt()).isEqualTo(now);
        assertThatThrownBy(credential::reactivate).isInstanceOf(IllegalStateException.class);
    }
}
