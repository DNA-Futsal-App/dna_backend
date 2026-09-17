package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.ImportAwardTeamRequest;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import br.com.dnafutsal.backend.sports.domain.SportsPersonView;
import br.com.dnafutsal.backend.sports.domain.SportsTeamDetailsView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardCandidateImportServiceTest {

    @Mock
    AwardEditionRepository editions;

    @Mock
    SportsCatalogService sports;

    @Mock
    AwardCandidateSnapshotWriter writer;

    AwardCandidateImportService service;

    @BeforeEach
    void setUp() {
        service = new AwardCandidateImportService(
                editions,
                sports,
                writer
        );
    }

    @Test
    void importsAthletesAndOnlyHeadCoachFromStaff() {
        AwardEdition edition =
                new AwardEdition(
                        "premio-dna-2026",
                        "Prêmio DNA Futsal 2026",
                        2026,
                        AwardEditionStatus.DRAFT,
                        null,
                        null
                );

        when(editions.findById(
                edition.getId()
        )).thenReturn(
                Optional.of(
                        edition
                )
        );

        when(sports.divisions(
                2026
        )).thenReturn(
                List.of(
                        new CatalogItemView(
                                3,
                                "A1"
                        )
                )
        );

        when(sports.categories(
                2026,
                3
        )).thenReturn(
                List.of(
                        new CatalogCategoryView(
                                7,
                                "Sub-15",
                                904
                        )
                )
        );

        when(sports.teams(
                904
        )).thenReturn(
                List.of(
                        new TeamView(
                                "123",
                                "Time A",
                                null,
                                "https://img.example/time.png"
                        )
                )
        );

        when(sports.teamDetails(
                904,
                123
        )).thenReturn(
                new SportsTeamDetailsView(
                        904,
                        "123",
                        "Time A",
                        "https://img.example/time.png",
                        List.of(
                                new SportsPersonView(
                                        "Atleta Um",
                                        "Um",
                                        null,
                                        "https://img.example/111.jpeg"
                                ),
                                new SportsPersonView(
                                        "Atleta Dois",
                                        null,
                                        null,
                                        "https://img.example/222.jpeg"
                                )
                        ),
                        List.of(
                                new SportsPersonView(
                                        "Treinador Principal",
                                        null,
                                        "Técnico",
                                        "https://img.example/333.jpeg"
                                ),
                                new SportsPersonView(
                                        "Auxiliar",
                                        null,
                                        "Técnico Auxiliar",
                                        "https://img.example/444.jpeg"
                                )
                        ),
                        false,
                        "https://source.example/team"
                )
        );

        when(writer.write(
                any()
        )).thenReturn(
                new AwardCandidateSnapshotWriter.Result(
                        3,
                        0,
                        0,
                        2,
                        List.of()
                )
        );

        service.importTeam(
                new ImportAwardTeamRequest(
                        edition.getId(),
                        904,
                        3,
                        7,
                        123
                )
        );

        ArgumentCaptor<AwardTeamCandidateSnapshot> captor =
                ArgumentCaptor.forClass(
                        AwardTeamCandidateSnapshot.class
                );

        org.mockito.Mockito.verify(
                writer
        ).write(
                captor.capture()
        );

        AwardTeamCandidateSnapshot snapshot =
                captor.getValue();

        assertThat(
                snapshot.candidates()
        ).hasSize(
                3
        );

        assertThat(
                snapshot.candidates()
                        .stream()
                        .filter(candidate ->
                                candidate.type()
                                        == AwardCandidateType.COACH
                        )
                        .map(
                                AwardTeamCandidateSnapshot.Candidate::name
                        )
        ).containsExactly(
                "Treinador Principal"
        );
    }
}
