package br.com.dnafutsal.backend.awards.application;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AwardTieDecisionPolicyTest {
    private static AwardTieDecisionPolicy.Candidate c(UUID id, int score) {
        return new AwardTieDecisionPolicy.Candidate(id, score, 1);
    }

    @Test
    void administratorCanChooseThreeOrFourAtThirdPlaceTie() {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        var ranked = List.of(c(ids.get(0), 10), c(ids.get(1), 9),
                c(ids.get(2), 8), c(ids.get(3), 8), c(ids.get(4), 7));
        assertEquals(List.of(ids.get(0), ids.get(1), ids.get(3)),
                AwardTieDecisionPolicy.select(ranked, 3,
                        List.of(ids.get(0), ids.get(1), ids.get(3))).orElseThrow());
        assertEquals(ids.subList(0, 4), AwardTieDecisionPolicy.select(ranked, 4,
                ids.subList(0, 4)).orElseThrow());
        assertTrue(AwardTieDecisionPolicy.select(ranked, 4,
                List.of(ids.get(0), ids.get(1), ids.get(2), ids.get(4))).isEmpty());
    }

    @Test
    void tripleTieAllowsAdminToChooseTwoForFourOrOneForThree() {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var ranked = List.of(c(ids.get(0), 10), c(ids.get(1), 9),
                c(ids.get(2), 8), c(ids.get(3), 8), c(ids.get(4), 8), c(ids.get(5), 6));
        assertTrue(AwardTieDecisionPolicy.select(ranked, 4,
                List.of(ids.get(0), ids.get(1), ids.get(3), ids.get(4))).isPresent());
        assertTrue(AwardTieDecisionPolicy.select(ranked, 3,
                List.of(ids.get(0), ids.get(1), ids.get(4))).isPresent());
        assertTrue(AwardTieDecisionPolicy.select(ranked, 4,
                List.of(ids.get(0), ids.get(1), ids.get(2), ids.get(5))).isEmpty());
    }

    @Test
    void noTieCannotBeBypassedAndMissingCompulsoryCandidateIsRejected() {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var ranked = List.of(c(ids.get(0), 10), c(ids.get(1), 9),
                c(ids.get(2), 8), c(ids.get(3), 7));
        assertFalse(AwardTieDecisionPolicy.tiedAtCutoff(ranked));
        assertTrue(AwardTieDecisionPolicy.select(ranked, 4, ids).isEmpty());
        var tie = List.of(c(ids.get(0), 10), c(ids.get(1), 9),
                c(ids.get(2), 8), c(ids.get(3), 8));
        assertTrue(AwardTieDecisionPolicy.select(tie, 3,
                List.of(ids.get(0), ids.get(2), ids.get(3))).isEmpty());
    }
}
