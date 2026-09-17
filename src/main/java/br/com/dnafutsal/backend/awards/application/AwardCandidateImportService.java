package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardCandidateResponse;
import br.com.dnafutsal.backend.awards.api.ImportAwardTeamRequest;
import br.com.dnafutsal.backend.awards.api.ImportAwardTeamResponse;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.SportsPersonView;
import br.com.dnafutsal.backend.sports.domain.SportsTeamDetailsView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AwardCandidateImportService {

    private static final Pattern IMAGE_ID =
            Pattern.compile(
                    "/([A-Za-z0-9_-]{3,120})\\.[A-Za-z0-9]{2,5}(?:[?#].*)?$"
            );

    private final AwardEditionRepository editions;
    private final SportsCatalogService sports;
    private final AwardCandidateSnapshotWriter writer;

    public AwardCandidateImportService(
            AwardEditionRepository editions,
            SportsCatalogService sports,
            AwardCandidateSnapshotWriter writer
    ) {
        this.editions = editions;
        this.sports = sports;
        this.writer = writer;
    }

    public ImportAwardTeamResponse importTeam(
            ImportAwardTeamRequest request
    ) {
        AwardEdition edition = editions.findById(
                        request.editionId()
                )
                .orElseThrow(() -> Errors.notFound(
                        "AWARD_EDITION_NOT_FOUND",
                        "Edição do prêmio não encontrada."
                ));

        if (edition.getStatus() != AwardEditionStatus.DRAFT) {
            throw Errors.conflict(
                    "AWARD_SNAPSHOT_LOCKED",
                    "Os candidatos só podem ser importados enquanto a edição estiver em rascunho."
            );
        }

        boolean divisionExists =
                sports.divisions(
                                edition.getSeason()
                        )
                        .stream()
                        .anyMatch(item ->
                                item.id()
                                        == request.divisionId()
                        );

        if (!divisionExists) {
            throw Errors.badRequest(
                    "AWARD_DIVISION_INVALID",
                    "A divisão informada não pertence à temporada desta edição."
            );
        }

        CatalogCategoryView category =
                sports.categories(
                                edition.getSeason(),
                                request.divisionId()
                        )
                        .stream()
                        .filter(item ->
                                item.id()
                                        == request.categoryId()
                        )
                        .findFirst()
                        .orElseThrow(() -> Errors.badRequest(
                                "AWARD_CATEGORY_INVALID",
                                "A categoria informada não pertence à divisão desta edição."
                        ));

        if (category.eventId() != request.eventId()) {
            throw Errors.badRequest(
                    "AWARD_EVENT_MISMATCH",
                    "O evento informado não corresponde à categoria selecionada."
            );
        }

        String teamId =
                Long.toString(
                        request.teamId()
                );

        TeamView team =
                sports.teams(
                                request.eventId()
                        )
                        .stream()
                        .filter(item ->
                                teamId.equals(
                                        item.id()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() -> Errors.badRequest(
                                "AWARD_TEAM_INVALID",
                                "O time informado não pertence ao evento selecionado."
                        ));

        SportsTeamDetailsView details =
                sports.teamDetails(
                        request.eventId(),
                        request.teamId()
                );

        if (details.personalDataSuppressed()) {
            throw Errors.conflict(
                    "AWARD_PERSONAL_DATA_UNAVAILABLE",
                    "O scraper não disponibilizou o elenco. Verifique EXPOSE_PERSONAL_DATA no scraper."
            );
        }

        List<AwardTeamCandidateSnapshot.Candidate> imported =
                new ArrayList<>();

        int athletesFound = 0;

        for (SportsPersonView athlete
                : safe(details.athletes())) {
            if (athlete == null
                    || athlete.name() == null
                    || athlete.name().isBlank()) {
                continue;
            }

            imported.add(
                    candidate(
                            request.eventId(),
                            teamId,
                            AwardCandidateType.ATHLETE,
                            athlete
                    )
            );
            athletesFound++;
        }

        if (athletesFound == 0) {
            throw Errors.conflict(
                    "AWARD_TEAM_ROSTER_EMPTY",
                    "Nenhum atleta foi encontrado para este time; o snapshot anterior não foi alterado."
            );
        }

        int coachesFound = 0;

        for (SportsPersonView staff
                : safe(details.staff())) {
            if (staff == null
                    || staff.name() == null
                    || staff.name().isBlank()
                    || !isHeadCoach(
                            staff.role()
                    )) {
                continue;
            }

            imported.add(
                    candidate(
                            request.eventId(),
                            teamId,
                            AwardCandidateType.COACH,
                            staff
                    )
            );
            coachesFound++;
        }

        AwardTeamCandidateSnapshot snapshot =
                new AwardTeamCandidateSnapshot(
                        edition.getId(),
                        request.eventId(),
                        request.divisionId(),
                        request.categoryId(),
                        teamId,
                        team.name(),
                        team.logoUrl(),
                        List.copyOf(imported)
                );

        AwardCandidateSnapshotWriter.Result result =
                writer.write(
                        snapshot
                );

        return new ImportAwardTeamResponse(
                edition.getId(),
                request.eventId(),
                request.divisionId(),
                request.categoryId(),
                teamId,
                team.name(),
                athletesFound,
                coachesFound,
                result.created(),
                result.updated(),
                result.deactivated(),
                result.positionsPending(),
                result.candidates()
                        .stream()
                        .map(
                                AwardCandidateResponse::from
                        )
                        .toList()
        );
    }

    private AwardTeamCandidateSnapshot.Candidate candidate(
            long eventId,
            String teamId,
            AwardCandidateType type,
            SportsPersonView person
    ) {
        String externalPersonId =
                externalPersonId(
                        person.imageUrl()
                );

        String identity =
                externalPersonId != null
                        ? "id:" + externalPersonId
                        : "name:"
                        + normalize(person.name())
                        + "|secondary:"
                        + normalize(person.secondaryName());

        String material =
                eventId
                        + "|"
                        + teamId
                        + "|"
                        + type.name()
                        + "|"
                        + identity;

        String sourceKey =
                UUID.nameUUIDFromBytes(
                                material.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                        .toString();

        return new AwardTeamCandidateSnapshot.Candidate(
                type,
                sourceKey,
                externalPersonId,
                person.name().trim(),
                blankToNull(
                        person.secondaryName()
                ),
                blankToNull(
                        person.role()
                ),
                blankToNull(
                        person.imageUrl()
                )
        );
    }

    private boolean isHeadCoach(
            String role
    ) {
        String normalized = normalize(
                role
        );

        if (normalized.isBlank()) {
            return false;
        }

        if (normalized.contains("auxiliar")
                || normalized.contains("assistente")
                || normalized.contains("preparador")) {
            return false;
        }

        return normalized.contains("tecnico")
                || normalized.contains("treinador");
    }

    private String externalPersonId(
            String imageUrl
    ) {
        if (imageUrl == null
                || imageUrl.isBlank()) {
            return null;
        }

        Matcher matcher =
                IMAGE_ID.matcher(
                        imageUrl
                );

        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private String normalize(
            String value
    ) {
        return Normalizer.normalize(
                        value == null
                                ? ""
                                : value,
                        Normalizer.Form.NFD
                )
                .replaceAll(
                        "\\p{M}",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^\\p{L}\\p{N}]+",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String blankToNull(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? null
                : value.trim();
    }

    private <T> List<T> safe(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : values;
    }
}
