package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class CoachSharedAccessService {
    private final AwardEditionRepository editions;
    private final AwardCoachAccessLinkRepository links;
    private final AwardCoachCredentialRepository credentials;
    private final AwardCoachVoterRepository contexts;
    private final AwardCandidateRepository candidates;
    private final AwardBallotRepository ballots;
    private final UserAccountRepository users;
    private final CurrentUserService currentUser;
    private final TokenSupport tokens;
    private final AppProperties appProperties;
    private final SportsCatalogService sports;
    private final AwardLiveRosterService liveRoster;
    private final Clock clock;

    public CoachSharedAccessService(AwardEditionRepository editions,
                                    AwardCoachAccessLinkRepository links,
                                    AwardCoachCredentialRepository credentials,
                                    AwardCoachVoterRepository contexts,
                                    AwardCandidateRepository candidates,
                                    AwardBallotRepository ballots,
                                    UserAccountRepository users,
                                    CurrentUserService currentUser,
                                    TokenSupport tokens,
                                    AppProperties appProperties,
                                    SportsCatalogService sports,
                                    AwardLiveRosterService liveRoster,
                                    Clock clock) {
        this.editions=editions; this.links=links; this.credentials=credentials; this.contexts=contexts;
        this.candidates=candidates; this.ballots=ballots; this.users=users; this.currentUser=currentUser;
        this.tokens=tokens; this.appProperties=appProperties; this.sports=sports; this.liveRoster=liveRoster; this.clock=clock;
    }

    @Transactional
    public CreateCoachAccessLinkResponse createLink(UUID editionId) {
        AwardEdition edition=edition(editionId);
        if(edition.isClosed()) throw Errors.conflict("AWARD_EDITION_CLOSED","Não é possível gerar o credenciamento para uma edição encerrada.");
        links.findFirstByEditionIdAndStatusOrderByCreatedAtDesc(editionId,AwardCoachAccessLinkStatus.ACTIVE)
                .ifPresent(x->{throw Errors.conflict("COACH_ACCESS_LINK_ALREADY_ACTIVE","Já existe um link de credenciamento ativo para esta edição.");});
        String raw=tokens.generate();
        AwardCoachAccessLink link=new AwardCoachAccessLink(editionId,tokens.hash(raw),currentUser.userId());
        try { links.saveAndFlush(link); }
        catch(DataIntegrityViolationException ex){ throw Errors.conflict("COACH_ACCESS_LINK_ALREADY_ACTIVE","Já existe um link de credenciamento ativo para esta edição."); }
        return new CreateCoachAccessLinkResponse(link.getId(),stripTrailingSlash(appProperties.frontendBaseUrl())+"/premio-dna/treinadores/convite/"+raw,link.getCreatedAt());
    }

    @Transactional(readOnly=true)
    public CoachAccessLinkInfoResponse linkStatus(UUID editionId){
        AwardEdition edition=edition(editionId);
        return links.findFirstByEditionIdOrderByCreatedAtDesc(editionId).map(x->linkResponse(x,edition))
                .orElse(new CoachAccessLinkInfoResponse(null,false,"NONE",edition.getId(),edition.getSlug(),edition.getName(),edition.getSeason(),null,null));
    }

    @Transactional
    public CoachAccessLinkInfoResponse revokeLink(UUID editionId){
        AwardEdition edition=edition(editionId);
        AwardCoachAccessLink link=links.findFirstByEditionIdAndStatusOrderByCreatedAtDesc(editionId,AwardCoachAccessLinkStatus.ACTIVE)
                .orElseThrow(()->Errors.notFound("COACH_ACCESS_LINK_NOT_FOUND","Nenhum link ativo foi encontrado para esta edição."));
        link.revoke(clock.instant()); links.saveAndFlush(link); return linkResponse(link,edition);
    }

    @Transactional(readOnly=true)
    public CoachAccessLinkInfoResponse inspect(String rawToken){
        AwardCoachAccessLink link=requireLink(rawToken); return linkResponse(link,edition(link.getEditionId()));
    }

    @Transactional
    public CoachCredentialResponse claimCurrentUser(String rawToken){ return claimForUser(currentUser.userId(),rawToken); }

    @Transactional
    public CoachCredentialResponse claimForUser(UUID userId,String rawToken){
        AwardCoachAccessLink link=requireLink(rawToken); AwardEdition edition=edition(link.getEditionId());
        if(!available(link,edition)) throw Errors.conflict("COACH_ACCESS_LINK_UNAVAILABLE","Este link foi revogado ou a edição já foi encerrada.");
        AwardCoachCredential existing=credentials.findByEditionIdAndUserId(edition.getId(),userId).orElse(null);
        if(existing!=null) return credential(existing);
        AwardCoachCredential created=new AwardCoachCredential(edition.getId(),userId,link.getId());
        try { credentials.saveAndFlush(created); }
        catch(DataIntegrityViolationException ex){ created=credentials.findByEditionIdAndUserId(edition.getId(),userId).orElseThrow(()->ex); }
        return credential(created);
    }

    @Transactional(readOnly=true)
    public CoachCredentialResponse currentCredential(){
        UUID userId=currentUser.userId();
        AwardCoachCredential credential=credentials.findByUserIdAndActiveTrueOrderByCreatedAtDesc(userId).stream().findFirst()
                .orElseThrow(()->Errors.forbidden("COACH_CREDENTIAL_REQUIRED","Esta conta não possui credenciamento para o júri técnico."));
        return credential(credential);
    }

    @Transactional
    public CoachCredentialResponse addSelfContext(CreateCoachContextRequest request){
        UUID userId=currentUser.userId();
        AwardCoachCredential credential=credentials.findByIdAndActiveTrue(request.credentialId()).filter(x->x.getUserId().equals(userId))
                .orElseThrow(()->Errors.forbidden("COACH_CREDENTIAL_REQUIRED","A credencial informada não pertence a esta conta."));
        if(!contexts.findByCredentialIdAndActiveTrueOrderByCreatedAtAsc(credential.getId()).isEmpty())
            throw Errors.conflict("COACH_SELF_CONTEXT_ALREADY_DEFINED","Contextos extras são liberados apenas pela administração.");
        createContext(credential,request,AwardCoachVoterContextSource.SELF_SELECTED); return credential(credential);
    }

    @Transactional(readOnly=true)
    public List<CoachCredentialResponse> adminVoters(UUID editionId){ edition(editionId); return credentials.findByEditionIdOrderByCreatedAtAsc(editionId).stream().map(this::credential).toList(); }

    @Transactional
    public CoachCredentialResponse adminAddContext(UUID credentialId,CreateCoachContextRequest request){
        AwardCoachCredential credential=credentials.findByIdAndActiveTrue(credentialId).orElseThrow(()->Errors.notFound("COACH_CREDENTIAL_NOT_FOUND","Treinador credenciado não encontrado."));
        if(!credentialId.equals(request.credentialId())) throw Errors.badRequest("COACH_CREDENTIAL_MISMATCH","A credencial da requisição é inválida.");
        createContext(credential,request,AwardCoachVoterContextSource.ADMIN); return credential(credential);
    }

    @Transactional
    public CoachCredentialResponse deactivateContext(UUID credentialId,UUID contextId){
        AwardCoachCredential credential=credentials.findByIdAndActiveTrue(credentialId).orElseThrow(()->Errors.notFound("COACH_CREDENTIAL_NOT_FOUND","Treinador credenciado não encontrado."));
        AwardCoachVoter context=contexts.findByIdAndCredentialIdAndActiveTrue(contextId,credentialId).orElseThrow(()->Errors.notFound("COACH_CONTEXT_NOT_FOUND","Contexto não encontrado."));
        if(ballots.existsByEditionIdAndCoachVoterId(credential.getEditionId(),context.getId())) throw Errors.conflict("COACH_CONTEXT_ALREADY_VOTED","Um contexto que já registrou voto não pode ser desativado.");
        context.deactivate(); contexts.saveAndFlush(context); return credential(credential);
    }

    @Transactional
    public CoachCredentialResponse deactivateCredential(UUID credentialId){
        AwardCoachCredential credential=credentials.findByIdAndActiveTrue(credentialId).orElseThrow(()->Errors.notFound("COACH_CREDENTIAL_NOT_FOUND","Treinador credenciado não encontrado."));
        credential.deactivate(); credentials.save(credential);
        contexts.findByCredentialIdAndActiveTrueOrderByCreatedAtAsc(credentialId).forEach(c->{if(!ballots.existsByEditionIdAndCoachVoterId(credential.getEditionId(),c.getId())) c.deactivate();});
        contexts.flush(); return credential(credential);
    }

    private void createContext(AwardCoachCredential credential,CreateCoachContextRequest request,AwardCoachVoterContextSource source){
        AwardEdition edition=edition(credential.getEditionId()); validateContext(edition,request); String teamId=request.teamId().trim();
        if(contexts.existsByCredentialIdAndEventIdAndDivisionIdAndCategoryIdAndTeamIdAndActiveTrue(credential.getId(),request.eventId(),request.divisionId(),request.categoryId(),teamId))
            throw Errors.conflict("COACH_CONTEXT_ALREADY_EXISTS","Este treinador já possui acesso a esta divisão, categoria e time.");
        AwardCandidate own=liveRoster.coachTeamVote(edition.getId(),request.eventId(),request.divisionId(),request.categoryId(),teamId);
        contexts.saveAndFlush(new AwardCoachVoter(edition.getId(),credential.getUserId(),credential.getId(),own.getId(),request.eventId(),request.divisionId(),request.categoryId(),teamId,source));
    }

    private void validateContext(AwardEdition edition,CreateCoachContextRequest request){
        CatalogCategoryView category=sports.categories(edition.getSeason(),request.divisionId()).stream().filter(x->x.id()==request.categoryId()).findFirst()
                .orElseThrow(()->Errors.badRequest("COACH_CONTEXT_CATEGORY_INVALID","A categoria não pertence à divisão informada."));
        if(category.eventId()!=request.eventId()) throw Errors.badRequest("COACH_CONTEXT_EVENT_INVALID","O evento não corresponde à divisão e categoria selecionadas.");
        boolean team=sports.teams(request.eventId()).stream().map(TeamView::id).anyMatch(request.teamId().trim()::equals);
        if(!team) throw Errors.badRequest("COACH_CONTEXT_TEAM_INVALID","O time não pertence ao campeonato informado.");
    }

    private CoachCredentialResponse credential(AwardCoachCredential credential){
        AwardEdition edition=edition(credential.getEditionId()); UserAccount user=users.findById(credential.getUserId()).orElseThrow(()->Errors.conflict("COACH_CREDENTIAL_INCONSISTENT","Conta do treinador não encontrada."));
        List<CoachVoterContextResponse> list=contexts.findByCredentialIdAndActiveTrueOrderByCreatedAtAsc(credential.getId()).stream().map(c->{
            String teamName=candidates.findById(c.getSelfCoachCandidateId()).map(AwardCandidate::getTeamName).orElse(c.getTeamId());
            return new CoachVoterContextResponse(c.getId(),c.getEventId(),c.getDivisionId(),c.getCategoryId(),c.getTeamId(),teamName,c.getContextSource().name(),c.isActive(),ballots.existsByEditionIdAndCoachVoterId(edition.getId(),c.getId()),c.getCreatedAt());
        }).toList();
        return new CoachCredentialResponse(credential.getId(),edition.getId(),edition.getSlug(),edition.getName(),edition.getSeason(),user.getId(),user.getName(),user.getEmail(),credential.isActive(),credential.getCreatedAt(),list);
    }

    private AwardCoachAccessLink requireLink(String raw){ if(raw==null||raw.isBlank()) throw Errors.badRequest("COACH_ACCESS_TOKEN_REQUIRED","Token obrigatório."); return links.findByTokenHash(tokens.hash(raw.trim())).orElseThrow(()->Errors.notFound("COACH_ACCESS_LINK_NOT_FOUND","Link de credenciamento não encontrado.")); }
    private CoachAccessLinkInfoResponse linkResponse(AwardCoachAccessLink link,AwardEdition edition){ boolean ok=available(link,edition); String status=link.getStatus()==AwardCoachAccessLinkStatus.REVOKED?"REVOKED":edition.isClosed()?"EDITION_CLOSED":"AVAILABLE"; return new CoachAccessLinkInfoResponse(link.getId(),ok,status,edition.getId(),edition.getSlug(),edition.getName(),edition.getSeason(),link.getCreatedAt(),link.getRevokedAt()); }
    private boolean available(AwardCoachAccessLink link,AwardEdition edition){return link.getStatus()==AwardCoachAccessLinkStatus.ACTIVE&&!edition.isClosed();}
    private AwardEdition edition(UUID id){return editions.findById(id).orElseThrow(()->Errors.notFound("AWARD_EDITION_NOT_FOUND","Edição do prêmio não encontrada."));}
    private String stripTrailingSlash(String value){String result=value;while(result.endsWith("/"))result=result.substring(0,result.length()-1);return result;}
}
