package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardCandidateResponse;
import br.com.dnafutsal.backend.awards.api.SyncAwardCoachesRequest;
import br.com.dnafutsal.backend.awards.api.SyncAwardCoachesResponse;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AwardCoachSyncService {

    private final AwardEditionRepository editions;
    private final SportsCatalogService sports;
    private final AwardLiveRosterService liveRoster;

    public AwardCoachSyncService(
            AwardEditionRepository editions,
            SportsCatalogService sports,
            AwardLiveRosterService liveRoster
    ) {
        this.editions = editions;
        this.sports = sports;
        this.liveRoster = liveRoster;
    }

    public SyncAwardCoachesResponse sync(
            UUID editionId,
            SyncAwardCoachesRequest request
    ) {
        AwardEdition edition =
                editions.findById(editionId)
                        .orElseThrow(() -> Errors.notFound(
                                "AWARD_EDITION_NOT_FOUND",
                                "Edição do prêmio não encontrada."
                        ));

        if (edition.getStatus() != AwardEditionStatus.DRAFT) {
            throw Errors.conflict(
                    "AWARD_COACH_SYNC_LOCKED",
                    "Os treinadores só podem ser sincronizados enquanto a edição estiver em rascunho."
            );
        }

        boolean divisionExists =
                sports.divisions(edition.getSeason())
                        .stream()
                        .anyMatch(item ->
                                item.id() == request.divisionId()
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
                                item.id() == request.categoryId()
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

        AwardLiveRosterService.SyncResult result =
                liveRoster.syncCoaches(
                        edition.getId(),
                        request.eventId(),
                        request.divisionId(),
                        request.categoryId()
                );

        return new SyncAwardCoachesResponse(
                edition.getId(),
                request.eventId(),
                request.divisionId(),
                request.categoryId(),
                result.teamsScanned(),
                result.coaches().size(),
                result.coaches()
                        .stream()
                        .map(AwardCandidateResponse::from)
                        .toList()
        );
    }
}
