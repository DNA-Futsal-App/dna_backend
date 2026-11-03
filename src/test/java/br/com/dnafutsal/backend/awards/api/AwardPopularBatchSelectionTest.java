package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwardPopularBatchSelectionTest {
    @Test
    void allModalitiesForOneGenderProducesFourIndependentContexts() {
        var selection = new AwardPopularBatchSelection(16, 3, 7, AwardRegistrationGender.MALE, null);
        assertEquals(AwardRegistrationContestCategory.values().length, selection.contexts().size());
        assertTrue(selection.contexts().stream().allMatch(c -> c.gender() == AwardRegistrationGender.MALE));
    }

    @Test
    void bothGendersAndAllModalitiesProducesEightContexts() {
        var selection = new AwardPopularBatchSelection(16, 3, 7, null, null);
        assertEquals(AwardRegistrationGender.values().length *
                AwardRegistrationContestCategory.values().length, selection.contexts().size());
    }

    @Test
    void singleModalityOnlyProducesOneContext() {
        var selection = new AwardPopularBatchSelection(16, 3, 7, AwardRegistrationGender.FEMALE,
                AwardRegistrationContestCategory.BEST_SAVE);
        assertEquals(1, selection.contexts().size());
        assertEquals(AwardRegistrationContestCategory.BEST_SAVE, selection.contexts().get(0).contestCategory());
    }
}
