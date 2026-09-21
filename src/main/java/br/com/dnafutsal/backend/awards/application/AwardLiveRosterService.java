package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.SportsPersonView;
import br.com.dnafutsal.backend.sports.domain.SportsTeamDetailsView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AwardLiveRosterService {

    private static final Pattern IMAGE_ID =
            Pattern.compile(
                    "/([A-Za-z0-9_-]{3,120})\\.[A-Za-z0-9]{2,5}(?:[?#].*)?$"
            );

    private final SportsCatalogService sports;
    private final AwardCandidateSnapshotWriter writer;

    public AwardLiveRosterService(
            SportsCatalogService sports,
            AwardCandidateSnapshotWriter writer
    ) {
        this.sports = sports;
        this.writer = writer;
    }

    public List<TeamView> teams(
            long eventId
    ) {
        return sports.teams(eventId);
    }

    public List<AwardCandidate> athletes(
            UUID editionId,
            long eventId,
            long divisionId,
            long categoryId,
            String teamId
    ) {
        return syncTeam(
                editionId,
                eventId,
                divisionId,
                categoryId,
                requireTeam(eventId, teamId),
                AwardCandidateType.ATHLETE
        );
    }

    public List<AwardCandidate> coaches(
            UUID editionId,
            long eventId,
            long divisionId,
            long categoryId,
            String teamId
    ) {
        return syncTeam(
                editionId,
                eventId,
                divisionId,
                categoryId,
                requireTeam(eventId, teamId),
                AwardCandidateType.COACH
        );
    }

    public AwardCandidate coachTeamVote(
            UUID editionId,
            long eventId,
            long divisionId,
            long categoryId,
            String teamId
    ) {
        TeamView team =
                requireTeam(
                        eventId,
                        teamId
                );

        String material =
                eventId
                        + "|"
                        + team.id()
                        + "|COACH|TEAM_VOTE";

        String sourceKey =
                UUID.nameUUIDFromBytes(
                                material.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                        .toString();

        AwardCandidateSnapshotWriter.Result result =
                writer.write(
                        new AwardTeamCandidateSnapshot(
                                editionId,
                                eventId,
                                divisionId,
                                categoryId,
                                team.id(),
                                team.name(),
                                team.logoUrl(),
                                List.of(
                                        new AwardTeamCandidateSnapshot.Candidate(
                                                AwardCandidateType.COACH,
                                                sourceKey,
                                                null,
                                                team.name(),
                                                null,
                                                AwardCandidate.TEAM_COACH_VOTE_ROLE,
                                                team.logoUrl()
                                        )
                                )
                        ),
                        AwardCandidateType.COACH,
                        false
                );

        return result.candidates()
                .stream()
                .filter(candidate ->
                        sourceKey.equals(
                                candidate.getSourceKey()
                        )
                )
                .findFirst()
                .orElseThrow(() -> Errors.conflict(
                        "AWARD_COACH_TEAM_VOTE_SYNC_FAILED",
                        "Não foi possível preparar a equipe para a votação de técnico."
                ));
    }

    public SyncResult syncCoaches(
            UUID editionId,
            long eventId,
            long divisionId,
            long categoryId
    ) {
        List<TeamView> teams =
                sports.teams(eventId);

        List<AwardCandidate> coaches =
                new ArrayList<>();

        for (TeamView team : teams) {
            coaches.addAll(
                    syncTeam(
                            editionId,
                            eventId,
                            divisionId,
                            categoryId,
                            team,
                            AwardCandidateType.COACH
                    )
            );
        }

        coaches.sort(
                Comparator
                        .comparing(
                                AwardCandidate::getTeamName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                AwardCandidate::getName,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        return new SyncResult(
                teams.size(),
                List.copyOf(coaches)
        );
    }

    private List<AwardCandidate> syncTeam(
            UUID editionId,
            long eventId,
            long divisionId,
            long categoryId,
            TeamView team,
            AwardCandidateType type
    ) {
        long numericTeamId =
                numericTeamId(
                        team.id()
                );

        SportsTeamDetailsView details =
                sports.teamDetails(
                        eventId,
                        numericTeamId
                );

        if (details.personalDataSuppressed()) {
            throw Errors.conflict(
                    "AWARD_PERSONAL_DATA_UNAVAILABLE",
                    "O scraper não disponibilizou os dados pessoais necessários para a votação."
            );
        }

        List<AwardTeamCandidateSnapshot.Candidate> incoming =
                new ArrayList<>();

        if (type == AwardCandidateType.ATHLETE) {
            for (SportsPersonView athlete : safe(details.athletes())) {
                if (validPerson(athlete)) {
                    incoming.add(
                            candidate(
                                    eventId,
                                    team.id(),
                                    AwardCandidateType.ATHLETE,
                                    athlete
                            )
                    );
                }
            }
        } else {
            for (SportsPersonView staff : safe(details.staff())) {
                if (validPerson(staff)
                        && isHeadCoach(staff.role())) {
                    incoming.add(
                            candidate(
                                    eventId,
                                    team.id(),
                                    AwardCandidateType.COACH,
                                    staff
                            )
                    );
                }
            }
        }

        AwardCandidateSnapshotWriter.Result result =
                writer.write(
                        new AwardTeamCandidateSnapshot(
                                editionId,
                                eventId,
                                divisionId,
                                categoryId,
                                team.id(),
                                team.name(),
                                team.logoUrl(),
                                List.copyOf(incoming)
                        ),
                        type,
                        type == AwardCandidateType.COACH
                );

        return result.candidates();
    }

    private TeamView requireTeam(
            long eventId,
            String teamId
    ) {
        String normalized =
                teamId == null
                        ? ""
                        : teamId.trim();

        if (normalized.isBlank()) {
            throw Errors.badRequest(
                    "AWARD_TEAM_INVALID",
                    "Selecione um time válido."
            );
        }

        return sports.teams(eventId)
                .stream()
                .filter(team ->
                        normalized.equals(
                                team.id()
                        )
                )
                .findFirst()
                .orElseThrow(() -> Errors.badRequest(
                        "AWARD_TEAM_INVALID",
                        "O time informado não pertence à divisão e categoria deste treinador."
                ));
    }

    private long numericTeamId(
            String teamId
    ) {
        try {
            long parsed =
                    Long.parseLong(
                            teamId
                    );

            if (parsed <= 0) {
                throw new NumberFormatException(
                        "team id must be positive"
                );
            }

            return parsed;
        } catch (NumberFormatException exception) {
            throw Errors.badRequest(
                    "AWARD_TEAM_INVALID",
                    "O identificador do time não é válido para carregar o elenco."
            );
        }
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
                blankToNull(person.secondaryName()),
                blankToNull(person.role()),
                blankToNull(person.imageUrl())
        );
    }

    private boolean validPerson(
            SportsPersonView person
    ) {
        return person != null
                && person.name() != null
                && !person.name().isBlank();
    }

    private boolean isHeadCoach(
            String role
    ) {
        String normalized =
                normalize(role);

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

    public record SyncResult(
            int teamsScanned,
            List<AwardCandidate> coaches
    ) {
    }
}
