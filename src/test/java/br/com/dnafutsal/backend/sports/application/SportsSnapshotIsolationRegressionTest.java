package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SportsSnapshotIsolationRegressionTest {
    private final SportsDataGateway gateway = mock(SportsDataGateway.class);
    private final SportsSnapshotService snapshots = new SportsSnapshotService(gateway);

    @Test
    void refusesSnapshotFromAnotherCompetition() {
        var snapshot = new SportsSnapshot(
                new SportsEventView(917L, "Paulista", 2026, "Principal", "A1", null),
                List.of(), List.of(), List.of(), List.of(), Instant.now());
        when(gateway.snapshot(926L)).thenReturn(snapshot);
        assertThatThrownBy(() -> snapshots.snapshot(926L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SPORTS_EVENT_MISMATCH"));
    }

    @Test
    void refusesUnavailableSnapshotWithoutSilentlyReturningDefaultData() {
        assertThatThrownBy(() -> snapshots.snapshot(926L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SPORTS_EVENT_MISMATCH"));
    }

    @Test
    void acceptsSnapshotOfTheRequestedCompetition() {
        var snapshot = new SportsSnapshot(
                new SportsEventView(926L, "Paulista Feminino", 2026, "Sub-12", "A2", null),
                List.of(), List.of(), List.of(), List.of(), Instant.now());
        when(gateway.snapshot(926L)).thenReturn(snapshot);
        assertThat(snapshots.snapshot(926L)).isSameAs(snapshot);
    }
}
