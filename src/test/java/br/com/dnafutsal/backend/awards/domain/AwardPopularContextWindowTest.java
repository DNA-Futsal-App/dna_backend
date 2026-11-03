package br.com.dnafutsal.backend.awards.domain;

import br.com.dnafutsal.backend.awards.api.AwardPopularContext;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AwardPopularContextWindowTest {
    @Test
    void differentCategoriesCanHaveIndependentSchedules() {
        UUID edition = UUID.randomUUID();
        var goal = new AwardPopularContext(1, 2, 3, AwardRegistrationGender.MALE,
                AwardRegistrationContestCategory.BEAUTIFUL_GOAL);
        var save = new AwardPopularContext(1, 2, 3, AwardRegistrationGender.MALE,
                AwardRegistrationContestCategory.BEST_SAVE);
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        var open = new AwardPopularContextWindow(edition, goal,
                now.minusSeconds(5), now.plusSeconds(1800));
        var upcoming = new AwardPopularContextWindow(edition, save,
                now.plusSeconds(3600), now.plusSeconds(7200));
        assertEquals("OPEN", open.statusAt(now));
        assertEquals("SCHEDULED", upcoming.statusAt(now));
        open.close(now);
        assertEquals("CLOSED", open.statusAt(now));
        assertEquals("SCHEDULED", upcoming.statusAt(now));
    }
}
