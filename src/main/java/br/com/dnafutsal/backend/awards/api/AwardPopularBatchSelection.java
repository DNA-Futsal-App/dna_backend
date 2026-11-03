package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;

/** Null gender or contestCategory selects both genders or all four award modalities. */
public record AwardPopularBatchSelection(
        @Min(1) long eventId, @Min(1) long divisionId, @Min(1) long categoryId,
        AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory) {

    public List<AwardPopularContext> contexts() {
        List<AwardPopularContext> result = new ArrayList<>();
        AwardRegistrationGender[] genders = gender == null
                ? AwardRegistrationGender.values() : new AwardRegistrationGender[] { gender };
        AwardRegistrationContestCategory[] categories = contestCategory == null
                ? AwardRegistrationContestCategory.values()
                : new AwardRegistrationContestCategory[] { contestCategory };
        for (var g : genders) {
            for (var category : categories) {
                result.add(new AwardPopularContext(eventId, divisionId, categoryId, g, category));
            }
        }
        return List.copyOf(result);
    }
}
