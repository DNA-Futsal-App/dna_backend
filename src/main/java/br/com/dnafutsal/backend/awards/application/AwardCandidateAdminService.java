package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardCandidateResponse;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.AwardPlayerPosition;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AwardCandidateAdminService {

    private final AwardCandidateRepository candidates;
    private final AwardEditionRepository editions;

    public AwardCandidateAdminService(
            AwardCandidateRepository candidates,
            AwardEditionRepository editions
    ) {
        this.candidates = candidates;
        this.editions = editions;
    }

    @Transactional(readOnly = true)
    public List<AwardCandidateResponse> list(
            UUID editionId
    ) {
        edition(
                editionId
        );

        return candidates
                .findByEditionIdOrderByTeamNameAscNameAsc(
                        editionId
                )
                .stream()
                .map(
                        AwardCandidateResponse::from
                )
                .toList();
    }

    @Transactional
    public AwardCandidateResponse assignPosition(
            UUID candidateId,
            AwardPlayerPosition position
    ) {
        AwardCandidate candidate =
                candidates.findById(
                                candidateId
                        )
                        .orElseThrow(() -> Errors.notFound(
                                "AWARD_CANDIDATE_NOT_FOUND",
                                "Candidato não encontrado."
                        ));

        AwardEdition edition =
                edition(
                        candidate.getEditionId()
                );

        if (edition.getStatus() != AwardEditionStatus.DRAFT) {
            throw Errors.conflict(
                    "AWARD_SNAPSHOT_LOCKED",
                    "A posição dos atletas não pode ser alterada depois da abertura da votação."
            );
        }

        if (candidate.getCandidateType()
                != AwardCandidateType.ATHLETE) {
            throw Errors.badRequest(
                    "AWARD_POSITION_NOT_APPLICABLE",
                    "Posição de jogo só pode ser atribuída a atletas."
            );
        }

        candidate.assignPosition(
                position
        );

        return AwardCandidateResponse.from(
                candidates.saveAndFlush(
                        candidate
                )
        );
    }

    private AwardEdition edition(
            UUID editionId
    ) {
        return editions.findById(
                        editionId
                )
                .orElseThrow(() -> Errors.notFound(
                        "AWARD_EDITION_NOT_FOUND",
                        "Edição do prêmio não encontrada."
                ));
    }
}
