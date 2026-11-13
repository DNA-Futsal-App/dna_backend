package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.infrastructure.SportsSnapshotArchive;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SportsSnapshotArchiveFallbackTest {

    private final SportsDataGateway gateway = mock(SportsDataGateway.class);
    private final SportsSnapshotArchive archive = mock(SportsSnapshotArchive.class);
    private final SportsSnapshotService service = new SportsSnapshotService(gateway, archive);

    @Test
    void savesEachValidSnapshot() {
        SportsSnapshot good = snapshot(920);
        when(gateway.snapshot(920)).thenReturn(good);
        assertThat(service.snapshot(920)).isSameAs(good);
        verify(archive).save(good);
    }

    @Test
    void servesArchivedSnapshotAfterCloudflareRelatedBadGateway() {
        SportsSnapshot old = snapshot(920);
        when(gateway.snapshot(920)).thenThrow(upstream("SPORTS_DATA_UNAVAILABLE"));
        when(archive.findRecent(920)).thenReturn(Optional.of(old));
        assertThat(service.snapshot(920)).isSameAs(old);
    }

    @Test
    void neverReturnsAnotherEventsArchivedSnapshot() {
        when(gateway.snapshot(921)).thenThrow(upstream("SPORTS_DATA_UNAVAILABLE"));
        when(archive.findRecent(921)).thenReturn(Optional.of(snapshot(920)));
        assertThatThrownBy(() -> service.snapshot(921))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SPORTS_DATA_UNAVAILABLE"));
        verify(archive, never()).findRecent(920);
    }

    @Test
    void doesNotServeArchivedDataWhenSourceExplicitlyReturns404() {
        when(gateway.snapshot(920)).thenThrow(new BusinessException(
                HttpStatus.NOT_FOUND, "SPORTS_EVENT_NOT_FOUND", "not found"));
        assertThatThrownBy(() -> service.snapshot(920))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(archive, never()).findRecent(920);
    }

    @Test
    void doesNotStoreSnapshotFromWrongCompetition() {
        when(gateway.snapshot(920)).thenReturn(snapshot(921));
        assertThatThrownBy(() -> service.snapshot(920))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SPORTS_EVENT_MISMATCH"));
        verify(archive, never()).save(any());
    }

    @Test
    void leavesFailureVisibleWhenNoArchiveExists() {
        when(gateway.snapshot(920)).thenThrow(upstream("SPORTS_DATA_UNAVAILABLE"));
        when(archive.findRecent(920)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.snapshot(920))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SPORTS_DATA_UNAVAILABLE"));
    }

    private BusinessException upstream(String code) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, code, "source blocked");
    }

    private SportsSnapshot snapshot(long eventId) {
        return new SportsSnapshot(
                new SportsEventView(eventId, "Paulista", 2026, "Sub-9", "A3", null),
                List.of(), List.of(), List.of(), List.of(), Instant.parse("2026-11-12T12:00:00Z"));
    }
}
