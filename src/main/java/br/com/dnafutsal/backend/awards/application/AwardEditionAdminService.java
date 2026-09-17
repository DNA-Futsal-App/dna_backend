package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardEditionAdminResponse;
import br.com.dnafutsal.backend.awards.api.AwardVoteCategoryResponse;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AwardEditionAdminService {

    private final AwardEditionRepository editions;
    private final AwardVoteCategoryRepository voteCategories;

    public AwardEditionAdminService(
            AwardEditionRepository editions,
            AwardVoteCategoryRepository voteCategories
    ) {
        this.editions = editions;
        this.voteCategories = voteCategories;
    }

    @Transactional(readOnly = true)
    public List<AwardEditionAdminResponse> editions() {
        return editions
                .findAllByOrderBySeasonDescNameAsc()
                .stream()
                .map(
                        AwardEditionAdminResponse::from
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AwardVoteCategoryResponse> voteCategories(
            UUID editionId
    ) {
        if (!editions.existsById(
                editionId
        )) {
            throw Errors.notFound(
                    "AWARD_EDITION_NOT_FOUND",
                    "Edição do prêmio não encontrada."
            );
        }

        return voteCategories
                .findByEditionIdOrderByDisplayOrderAsc(
                        editionId
                )
                .stream()
                .map(
                        AwardVoteCategoryResponse::from
                )
                .toList();
    }
}
