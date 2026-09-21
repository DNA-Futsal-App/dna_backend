package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateSource;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AwardCandidateSnapshotWriter {

    private final AwardCandidateRepository candidates;
    private final Clock clock;

    public AwardCandidateSnapshotWriter(
            AwardCandidateRepository candidates,
            Clock clock
    ) {
        this.candidates = candidates;
        this.clock = clock;
    }

    @Transactional
    public Result write(
            AwardTeamCandidateSnapshot snapshot
    ) {
        return write(
                snapshot,
                null,
                true
        );
    }

    @Transactional
    public Result write(
            AwardTeamCandidateSnapshot snapshot,
            AwardCandidateType managedType
    ) {
        return write(
                snapshot,
                managedType,
                true
        );
    }

    @Transactional
    public Result write(
            AwardTeamCandidateSnapshot snapshot,
            AwardCandidateType managedType,
            boolean deactivateMissing
    ) {
        Instant now = clock.instant();

        List<AwardCandidate> existing =
                candidates.findByEditionIdAndEventIdAndTeamIdAndSource(
                        snapshot.editionId(),
                        snapshot.eventId(),
                        snapshot.teamId(),
                        AwardCandidateSource.SCRAPER
                );

        Map<String, AwardCandidate> remaining =
                new LinkedHashMap<>();

        for (AwardCandidate candidate : existing) {
            if ((managedType == null
                    || candidate.getCandidateType() == managedType)
                    && candidate.getSourceKey() != null) {
                remaining.put(
                        candidate.getSourceKey(),
                        candidate
                );
            }
        }

        Map<String, AwardTeamCandidateSnapshot.Candidate> incoming =
                new LinkedHashMap<>();

        for (AwardTeamCandidateSnapshot.Candidate candidate
                : snapshot.candidates()) {
            if (managedType != null
                    && candidate.type() != managedType) {
                continue;
            }

            if (incoming.putIfAbsent(
                    candidate.sourceKey(),
                    candidate
            ) != null) {
                throw Errors.conflict(
                        "AWARD_CANDIDATE_IDENTITY_COLLISION",
                        "Dois candidatos do mesmo time foram identificados como a mesma pessoa."
                );
            }
        }

        int created = 0;
        int updated = 0;
        List<AwardCandidate> active = new ArrayList<>();

        for (AwardTeamCandidateSnapshot.Candidate source
                : incoming.values()) {
            AwardCandidate candidate =
                    remaining.remove(
                            source.sourceKey()
                    );

            if (candidate == null) {
                candidate = AwardCandidate.imported(
                        snapshot.editionId(),
                        source.type(),
                        source.externalPersonId(),
                        source.name(),
                        source.secondaryName(),
                        source.sourceRole(),
                        source.sourceKey(),
                        snapshot.eventId(),
                        snapshot.divisionId(),
                        snapshot.categoryId(),
                        snapshot.teamId(),
                        snapshot.teamName(),
                        snapshot.teamLogoUrl(),
                        source.imageUrl(),
                        now
                );
                created++;
            } else {
                candidate.refreshImported(
                        source.externalPersonId(),
                        source.name(),
                        source.secondaryName(),
                        source.sourceRole(),
                        snapshot.divisionId(),
                        snapshot.categoryId(),
                        snapshot.teamName(),
                        snapshot.teamLogoUrl(),
                        source.imageUrl(),
                        now
                );
                updated++;
            }

            active.add(candidate);
        }

        int deactivated = 0;

        if (deactivateMissing) {
            for (AwardCandidate stale : remaining.values()) {
                if (stale.isActive()) {
                    stale.deactivate();
                    deactivated++;
                }
            }
        }

        List<AwardCandidate> toSave =
                new ArrayList<>(active);

        toSave.addAll(
                remaining.values()
        );

        candidates.saveAll(toSave);
        candidates.flush();

        active.sort(
                Comparator
                        .comparing(
                                AwardCandidate::getCandidateType
                        )
                        .thenComparing(
                                AwardCandidate::getName,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        int positionsPending =
                (int) active.stream()
                        .filter(AwardCandidate::isActive)
                        .filter(candidate ->
                                candidate.getCandidateType()
                                        == AwardCandidateType.ATHLETE
                        )
                        .filter(candidate ->
                                candidate.getPositionCode()
                                        == null
                        )
                        .count();

        return new Result(
                created,
                updated,
                deactivated,
                positionsPending,
                List.copyOf(active)
        );
    }

    public record Result(
            int created,
            int updated,
            int deactivated,
            int positionsPending,
            List<AwardCandidate> candidates
    ) {
    }
}
