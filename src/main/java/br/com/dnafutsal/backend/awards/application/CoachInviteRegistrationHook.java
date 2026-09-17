package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInvite;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInviteStatus;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachInviteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AwardProperties;
import br.com.dnafutsal.backend.identity.api.RegisterRequest;
import br.com.dnafutsal.backend.identity.application.RegistrationLifecycleHook;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class CoachInviteRegistrationHook
        implements RegistrationLifecycleHook {

    private final AwardCoachInviteRepository invites;
    private final AwardEditionRepository editions;
    private final AwardCandidateRepository candidates;
    private final AwardCoachVoterRepository voters;
    private final TokenSupport tokenSupport;
    private final AwardProperties properties;
    private final Clock clock;

    public CoachInviteRegistrationHook(
            AwardCoachInviteRepository invites,
            AwardEditionRepository editions,
            AwardCandidateRepository candidates,
            AwardCoachVoterRepository voters,
            TokenSupport tokenSupport,
            AwardProperties properties,
            Clock clock
    ) {
        this.invites = invites;
        this.editions = editions;
        this.candidates = candidates;
        this.voters = voters;
        this.tokenSupport = tokenSupport;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void afterRegistration(
            UserAccount user,
            RegisterRequest request
    ) {
        String rawToken =
                request.coachInviteToken();

        if (rawToken == null
                || rawToken.isBlank()) {
            return;
        }

        Instant now =
                clock.instant();

        AwardCoachInvite invite =
                invites.findByTokenHashForUpdate(
                                tokenSupport.hash(
                                        rawToken.trim()
                                )
                        )
                        .orElseThrow(() -> Errors.notFound(
                                "COACH_INVITE_NOT_FOUND",
                                "Convite de treinador não encontrado."
                        ));

        AwardEdition edition =
                editions.findById(
                                invite.getEditionId()
                        )
                        .orElseThrow(() -> Errors.notFound(
                                "AWARD_EDITION_NOT_FOUND",
                                "Edição do prêmio não encontrada."
                        ));

        AwardCandidate coach =
                candidates.findByIdAndEditionId(
                                invite.getCoachCandidateId(),
                                invite.getEditionId()
                        )
                        .orElseThrow(() -> Errors.notFound(
                                "COACH_CANDIDATE_NOT_FOUND",
                                "Treinador não encontrado nesta edição."
                        ));

        if (invite.getStatus()
                != AwardCoachInviteStatus.PENDING
                || invite.isExpiredAt(now)) {
            throw Errors.conflict(
                    "COACH_INVITE_UNAVAILABLE",
                    "Este convite expirou ou não está mais disponível."
            );
        }

        if (edition.isClosed()) {
            throw Errors.conflict(
                    "AWARD_EDITION_CLOSED",
                    "Esta edição do prêmio já foi encerrada."
            );
        }

        if (coach.getCandidateType()
                != AwardCandidateType.COACH
                || !coach.isActive()) {
            throw Errors.conflict(
                    "COACH_CANDIDATE_INACTIVE",
                    "O cadastro deste treinador não está ativo."
            );
        }

        if (voters.existsByEditionIdAndSelfCoachCandidateId(
                edition.getId(),
                coach.getId()
        )) {
            throw Errors.conflict(
                    "COACH_ALREADY_CLAIMED",
                    "Este treinador já está vinculado a outra conta."
            );
        }

        if (invite.hasActiveReservationAt(now)
                && !user.getId()
                .equals(
                        invite.getReservedByUserId()
                )) {
            throw Errors.conflict(
                    "COACH_INVITE_RESERVED",
                    "Este convite já está reservado para outro cadastro."
            );
        }

        if (invite.hasExpiredReservationAt(now)) {
            invite.clearReservation();
        }

        invite.reserve(
                user.getId(),
                now,
                now.plus(
                        properties.coachInviteReservationTtl()
                )
        );

        invites.save(
                invite
        );
    }

    @Override
    public void afterEmailVerified(
            UserAccount user
    ) {
        Instant now =
                clock.instant();

        AwardCoachInvite invite =
                invites.findByReservedByUserIdForUpdate(
                                user.getId()
                        )
                        .orElse(null);

        if (invite == null) {
            return;
        }

        if (invite.getStatus()
                != AwardCoachInviteStatus.PENDING
                || invite.hasExpiredReservationAt(now)) {
            invite.clearReservation();
            invites.save(
                    invite
            );
            return;
        }

        AwardEdition edition =
                editions.findById(
                                invite.getEditionId()
                        )
                        .orElse(null);

        AwardCandidate coach =
                candidates.findByIdAndEditionId(
                                invite.getCoachCandidateId(),
                                invite.getEditionId()
                        )
                        .orElse(null);

        if (edition == null
                || edition.isClosed()
                || coach == null
                || !coach.isActive()
                || coach.getCandidateType()
                != AwardCandidateType.COACH) {
            invite.clearReservation();
            invites.save(
                    invite
            );
            return;
        }

        AwardCoachVoter existing =
                voters.findByEditionIdAndUserId(
                                edition.getId(),
                                user.getId()
                        )
                        .orElse(null);

        if (existing != null) {
            if (existing.getSelfCoachCandidateId()
                    .equals(
                            coach.getId()
                    )) {
                invite.claim(
                        user.getId(),
                        now
                );
                invites.save(
                        invite
                );
            } else {
                invite.clearReservation();
                invites.save(
                        invite
                );
            }

            return;
        }

        if (voters.existsByEditionIdAndSelfCoachCandidateId(
                edition.getId(),
                coach.getId()
        )) {
            invite.clearReservation();
            invites.save(
                    invite
            );
            return;
        }

        AwardCoachVoter voter =
                new AwardCoachVoter(
                        edition.getId(),
                        user.getId(),
                        invite.getId(),
                        coach.getId(),
                        coach.getEventId(),
                        coach.getDivisionId(),
                        coach.getCategoryId(),
                        coach.getTeamId()
                );

        voters.saveAndFlush(
                voter
        );

        invite.claim(
                user.getId(),
                now
        );

        invites.save(
                invite
        );
    }
}
