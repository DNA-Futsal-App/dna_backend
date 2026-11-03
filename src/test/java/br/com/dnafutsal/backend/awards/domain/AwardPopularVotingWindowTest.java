package br.com.dnafutsal.backend.awards.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AwardPopularVotingWindowTest {
    private static final Instant OPEN = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void openingInclusiveClosingExclusive() {
        var window = new AwardPopularVotingWindow(UUID.randomUUID(), OPEN, CLOSE);
        assertFalse(window.isOpen(OPEN.minusNanos(1)));
        assertEquals("SCHEDULED", window.statusAt(OPEN.minusNanos(1)));
        assertTrue(window.isOpen(OPEN));
        assertEquals("OPEN", window.statusAt(OPEN));
        assertFalse(window.isOpen(CLOSE));
        assertEquals("CLOSED", window.statusAt(CLOSE));
    }

    @Test
    void manualClosureCannotBeReversed() {
        var window = new AwardPopularVotingWindow(UUID.randomUUID(), OPEN, CLOSE);
        Instant now = OPEN.plusSeconds(10);
        window.close(now);
        window.close(now.plusSeconds(5));
        assertFalse(window.isOpen(now.plusSeconds(20)));
        assertEquals(now, window.getManuallyClosedAt());
    }
}
