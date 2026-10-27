package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;
import org.springframework.stereotype.Component;

/** Validate explicitly selected competition preferences against FPFS event IDs. */
@Component
public class SportsPreferenceValidator {

    private final SportsCatalogService catalog;

    public SportsPreferenceValidator(SportsCatalogService catalog) {
        this.catalog = catalog;
    }

    public void validate(SportsCompetitionCode competitionCode, Long eventId,
                         String divisionId, String categoryId, String teamId) {
        // Legacy clients and existing Union followers do not send a competition code.
        if (competitionCode == null) {
            return;
        }
        // It is valid to follow the female championship before choosing a category.
        // Sports endpoints must refuse to fall back to the masculine default in that case.
        if (eventId == null && divisionId == null && categoryId == null && teamId == null) {
            return;
        }
        if (eventId == null || divisionId == null || categoryId == null) {
            throw invalid();
        }

        long division = positiveId(divisionId);
        long category = positiveId(categoryId);
        var event = catalog.event(eventId);
        int season = event.season();

        boolean knownDivision = catalog.divisions(season, competitionCode.catalogTitle())
                .stream().anyMatch(item -> item.id() == division);
        if (!knownDivision) {
            throw invalid();
        }

        CatalogCategoryView match = catalog.categories(season, competitionCode.catalogTitle(), division)
                .stream()
                .filter(item -> item.id() == category && item.eventId() == eventId)
                .findFirst()
                .orElseThrow(SportsPreferenceValidator::invalid);

        if (teamId != null && catalog.teams(match.eventId()).stream()
                .noneMatch(team -> teamId.equals(team.id()))) {
            throw invalid();
        }
    }

    private static long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Return a client error, not a server error for malformed IDs.
        }
        throw invalid();
    }

    private static BusinessException invalid() {
        return Errors.badRequest("SPORTS_PREFERENCE_INVALID",
                "A divisão, categoria, evento ou equipe não pertence à competição selecionada.");
    }
}
