package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.ClaimCoachInviteRequest;
import br.com.dnafutsal.backend.awards.api.CoachAccessResponse;
import br.com.dnafutsal.backend.awards.api.CoachInviteResponse;
import br.com.dnafutsal.backend.awards.api.CreateCoachInviteRequest;
import br.com.dnafutsal.backend.awards.api.CreateCoachInviteResponse;
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
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.AwardProperties;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class CoachInviteService {

    private final AwardEditionRepository editions;
    private final AwardCandidateRepository candidates;
    private final AwardCoachInviteRepository invites;
    private final AwardCoachVoterRepository voters;
    private final TokenSupport tokenSupport;
    private final CurrentUserService currentUser;
    private final AppProperties appProperties;
    private final AwardProperties awardProperties;
    private final Clock clock;

    public CoachInviteService(
            AwardEditionRepository editions,
            AwardCandidateRepository candidates,
            AwardCoachInviteRepository invites,
            AwardCoachVoterRepository voters,
            TokenSupport tokenSupport,
            CurrentUserService currentUser,
            AppProperties appProperties,
            AwardProperties awardProperties,
            Clock clock
    ) {
        this.editions = editions;
        this.candidates = candidates;
        this.invites = invites;
        this.voters = voters;
        this.tokenSupport = tokenSupport;
        this.currentUser = currentUser;
        this.appProperties = appProperties;
        this.awardProperties = awardProperties;
        this.clock = clock;
    }

    @Transactional
    public CreateCoachInviteResponse create(CreateCoachInviteRequest request) {
        AwardEdition edition = edition(request.editionId());

        if (edition.isClosed()) {
            throw Errors.conflict(
                    "AWARD_EDITION_CLOSED",
                    "Não é possível criar convites para uma edição encerrada."
            );
        }

        AwardCandidate coach = coachCandidate(
                request.coachCandidateId(),
                request.editionId()
        );

        if (!coach.isActive()) {
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
                    "Este treinador já está vinculado a uma conta."
            );
        }

        Instant now = clock.instant();

        invites.findByEditionIdAndCoachCandidateIdAndStatus(
                edition.getId(),
                coach.getId(),
                AwardCoachInviteStatus.PENDING
        ).ifPresent(existing -> {
            if (existing.hasActiveReservationAt(now)) {
                throw Errors.conflict(
                        "COACH_INVITE_RESERVED",
                        "Já existe um cadastro em andamento para este treinador."
                );
            }

            if (!existing.isExpiredAt(now)
                    && !existing.hasExpiredReservationAt(now)) {
                throw Errors.conflict(
                        "COACH_INVITE_ALREADY_EXISTS",
                        "Já existe um convite válido para este treinador."
                );
            }

            existing.revoke();
            invites.saveAndFlush(existing);
        });

        String rawToken = tokenSupport.generate();
        String tokenHash = tokenSupport.hash(rawToken);
        Instant expiresAt = now.plus(awardProperties.coachInviteTtl());

        AwardCoachInvite invite = new AwardCoachInvite(
                edition.getId(),
                coach.getId(),
                tokenHash,
                expiresAt
        );

        try {
            invites.saveAndFlush(invite);
        } catch (DataIntegrityViolationException exception) {
            throw Errors.conflict(
                    "COACH_INVITE_ALREADY_EXISTS",
                    "Já existe um convite válido para este treinador."
            );
        }

        String baseUrl = stripTrailingSlash(appProperties.frontendBaseUrl());
        String inviteUrl = baseUrl
                + "/premio-dna/treinadores/convite/"
                + rawToken;

        return new CreateCoachInviteResponse(
                invite.getId(),
                inviteUrl,
                expiresAt
        );
    }

    @Transactional
    public void revoke(
            UUID inviteId
    ) {
        AwardCoachInvite invite =
                invites.findById(inviteId)
                        .orElseThrow(() -> Errors.notFound(
                                "COACH_INVITE_NOT_FOUND",
                                "Convite de treinador não encontrado."
                        ));

        if (invite.getStatus() == AwardCoachInviteStatus.CLAIMED) {
            throw Errors.conflict(
                    "COACH_INVITE_ALREADY_CLAIMED",
                    "Um convite já utilizado não pode ser revogado."
            );
        }

        invite.revoke();
        invites.saveAndFlush(invite);
    }

    @Transactional(readOnly = true)
    public CoachInviteResponse inspect(String rawToken) {
        Instant now = clock.instant();

        AwardCoachInvite invite = invites.findByTokenHash(
                        tokenSupport.hash(rawToken)
                )
                .orElseThrow(() -> Errors.notFound(
                        "COACH_INVITE_NOT_FOUND",
                        "Convite de treinador não encontrado."
                ));

        AwardEdition edition = edition(invite.getEditionId());
        AwardCandidate coach = coachCandidate(
                invite.getCoachCandidateId(),
                invite.getEditionId()
        );

        String availability = availability(
                invite,
                edition,
                coach,
                now
        );

        return new CoachInviteResponse(
                invite.getId(),
                "AVAILABLE".equals(availability),
                availability,
                invite.getExpiresAt(),
                edition.getId(),
                edition.getSlug(),
                edition.getName(),
                edition.getSeason(),
                coach.getName(),
                coach.getEventId(),
                coach.getDivisionId(),
                coach.getCategoryId(),
                coach.getTeamId(),
                coach.getTeamName()
        );
    }

    @Transactional
    public CoachAccessResponse claim(ClaimCoachInviteRequest request) {
        UUID userId = currentUser.userId();
        Instant now = clock.instant();
        String tokenHash = tokenSupport.hash(request.token());

        AwardCoachInvite invite = invites.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> Errors.notFound(
                        "COACH_INVITE_NOT_FOUND",
                        "Convite de treinador não encontrado."
                ));

        AwardEdition edition = edition(invite.getEditionId());
        AwardCandidate coach = coachCandidate(
                invite.getCoachCandidateId(),
                invite.getEditionId()
        );

        if (invite.getStatus() == AwardCoachInviteStatus.CLAIMED) {
            if (userId.equals(invite.getClaimedByUserId())) {
                AwardCoachVoter existing = voters
                        .findByEditionIdAndUserId(
                                edition.getId(),
                                userId
                        )
                        .orElseThrow(() -> Errors.conflict(
                                "COACH_INVITE_INCONSISTENT",
                                "O convite já foi utilizado, mas o vínculo do treinador não foi encontrado."
                        ));

                return access(existing, edition, coach);
            }

            throw Errors.conflict(
                    "COACH_INVITE_ALREADY_CLAIMED",
                    "Este convite já foi utilizado."
            );
        }

        boolean ownReservation =
                userId.equals(
                        invite.getReservedByUserId()
                )
                        && invite.hasActiveReservationAt(now);

        if (invite.hasActiveReservationAt(now)
                && !ownReservation) {
            throw Errors.conflict(
                    "COACH_INVITE_RESERVED",
                    "Este convite está reservado para outro cadastro."
            );
        }

        if (invite.hasExpiredReservationAt(now)) {
            invite.clearReservation();
        }

        if (!ownReservation
                && !invite.isUsableAt(now)) {
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

        if (!coach.isActive()) {
            throw Errors.conflict(
                    "COACH_CANDIDATE_INACTIVE",
                    "O cadastro deste treinador não está ativo."
            );
        }

        validateContext(request, coach);

        if (voters.existsByEditionIdAndUserId(
                edition.getId(),
                userId
        )) {
            throw Errors.conflict(
                    "USER_ALREADY_COACH_VOTER",
                    "Esta conta já está vinculada a um treinador nesta edição."
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

        AwardCoachVoter voter = new AwardCoachVoter(
                edition.getId(),
                userId,
                invite.getId(),
                coach.getId(),
                request.eventId(),
                request.divisionId(),
                request.categoryId(),
                request.teamId().trim()
        );

        invite.claim(userId, now);
        invites.save(invite);

        try {
            voters.saveAndFlush(voter);
        } catch (DataIntegrityViolationException exception) {
            throw Errors.conflict(
                    "COACH_INVITE_CLAIM_CONFLICT",
                    "Não foi possível vincular este treinador porque o convite ou a conta já foram utilizados."
            );
        }

        return access(voter, edition, coach);
    }

    private AwardEdition edition(UUID editionId) {
        return editions.findById(editionId)
                .orElseThrow(() -> Errors.notFound(
                        "AWARD_EDITION_NOT_FOUND",
                        "Edição do prêmio não encontrada."
                ));
    }

    private AwardCandidate coachCandidate(
            UUID candidateId,
            UUID editionId
    ) {
        AwardCandidate candidate = candidates
                .findByIdAndEditionId(candidateId, editionId)
                .orElseThrow(() -> Errors.notFound(
                        "COACH_CANDIDATE_NOT_FOUND",
                        "Treinador não encontrado nesta edição."
                ));

        if (candidate.getCandidateType() != AwardCandidateType.COACH) {
            throw Errors.badRequest(
                    "CANDIDATE_IS_NOT_COACH",
                    "O candidato informado não é um treinador."
            );
        }

        return candidate;
    }

    private void validateContext(
            ClaimCoachInviteRequest request,
            AwardCandidate coach
    ) {
        boolean matches =
                request.eventId() == coach.getEventId()
                        && request.divisionId() == coach.getDivisionId()
                        && request.categoryId() == coach.getCategoryId()
                        && request.teamId().trim().equals(coach.getTeamId());

        if (!matches) {
            throw Errors.badRequest(
                    "COACH_CONTEXT_MISMATCH",
                    "A divisão, categoria e o time selecionados não correspondem ao convite deste treinador."
            );
        }
    }

    private String availability(
            AwardCoachInvite invite,
            AwardEdition edition,
            AwardCandidate coach,
            Instant now
    ) {
        if (invite.getStatus() == AwardCoachInviteStatus.CLAIMED) {
            return "CLAIMED";
        }

        if (invite.hasActiveReservationAt(now)) {
            return "RESERVED";
        }

        if (invite.hasExpiredReservationAt(now)) {
            return "RESERVATION_EXPIRED";
        }

        if (invite.getStatus() == AwardCoachInviteStatus.REVOKED) {
            return "REVOKED";
        }

        if (invite.isExpiredAt(now)) {
            return "EXPIRED";
        }

        if (edition.isClosed()) {
            return "EDITION_CLOSED";
        }

        if (!coach.isActive()) {
            return "CANDIDATE_INACTIVE";
        }

        return "AVAILABLE";
    }

    private CoachAccessResponse access(
            AwardCoachVoter voter,
            AwardEdition edition,
            AwardCandidate coach
    ) {
        return new CoachAccessResponse(
                voter.getId(),
                edition.getId(),
                edition.getSlug(),
                edition.getName(),
                edition.getSeason(),
                coach.getName(),
                voter.getEventId(),
                voter.getDivisionId(),
                voter.getCategoryId(),
                voter.getTeamId(),
                coach.getTeamName()
        );
    }

    private String stripTrailingSlash(String value) {
        String result = value;

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }
}
