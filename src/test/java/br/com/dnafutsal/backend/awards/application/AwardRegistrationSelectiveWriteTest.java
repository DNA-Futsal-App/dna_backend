package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.awards.api.UpdateAwardRegistrationLinkRequest;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwardRegistrationSelectiveWriteTest {
    @Mock CurrentUserService currentUser;
    @Mock AwardEditionRepository editions;
    @Mock AwardPreVoteEditionLockRepository locks;
    @Mock AwardRegistrationRepository registrations;
    @Mock AwardRegistrationEntryRepository entries;
    @Mock AwardRegistrationReopeningService reopenings;
    @Mock AwardEdition edition;
    @Mock AwardRegistration registration;
    @Mock AwardRegistrationEntry entry;
    @Mock OracleAwardVideoStorage storage;

    @Test
    void cannotReplaceVideoOutsideAuthorizedModality() {
        UUID representative = UUID.randomUUID();
        UUID editionId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(currentUser.userId()).thenReturn(representative);
        when(registrations.findByIdAndRepresentativeUserId(registrationId, representative))
                .thenReturn(Optional.of(registration));
        when(entries.findByIdAndRegistrationId(entryId, registrationId))
                .thenReturn(Optional.of(entry));
        when(registration.getEditionId()).thenReturn(editionId);
        when(locks.lockEdition(editionId)).thenReturn(Optional.of(edition));
        when(registration.getEventId()).thenReturn(908L);
        when(registration.getDivisionId()).thenReturn(3L);
        when(registration.getCategoryId()).thenReturn(12L);
        when(registration.getGender()).thenReturn(AwardRegistrationGender.FEMALE);
        when(entry.getContestCategory()).thenReturn(AwardRegistrationContestCategory.BEST_DRIBBLE);
        // O default boolean false do Mockito representa ausência de permissão de reabertura.

        var service = new AwardRegistrationService(currentUser, null, editions, locks,
                registrations, entries, null, null, storage, null, null,
                Clock.systemUTC(), null, null, reopenings);

        assertThatThrownBy(() -> service.updateLink(registrationId, entryId,
                new UpdateAwardRegistrationLinkRequest("https://example.com/video")))
                .hasMessageContaining("não está permitida");
        verify(entries, never()).saveAndFlush(any());
        verify(entry, never()).updateExternalUrl(anyString());
    }
}
