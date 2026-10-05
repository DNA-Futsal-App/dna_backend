package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CoachVotingV2Service {
    private final CurrentUserService currentUser;
    private final AwardCoachCredentialRepository credentials;
    private final AwardCoachVoterRepository contexts;
    private final AwardEditionRepository editions;
    private final AwardVoteCategoryRepository voteCategories;
    private final AwardCandidateRepository candidates;
    private final AwardBallotRepository ballots;
    private final AwardBallotVoteRepository ballotVotes;
    private final AwardLiveRosterService liveRoster;
    private final UserAccountRepository users;
    private final Clock clock;

    public CoachVotingV2Service(CurrentUserService currentUser,AwardCoachCredentialRepository credentials,AwardCoachVoterRepository contexts,
                                AwardEditionRepository editions,AwardVoteCategoryRepository voteCategories,AwardCandidateRepository candidates,
                                AwardBallotRepository ballots,AwardBallotVoteRepository ballotVotes,AwardLiveRosterService liveRoster,
                                UserAccountRepository users,Clock clock){
        this.currentUser=currentUser;this.credentials=credentials;this.contexts=contexts;this.editions=editions;this.voteCategories=voteCategories;
        this.candidates=candidates;this.ballots=ballots;this.ballotVotes=ballotVotes;this.liveRoster=liveRoster;this.users=users;this.clock=clock;
    }

    @Transactional(readOnly=true)
    public CoachVotingContextResponse context(UUID contextId){
        Access a=access(contextId,false);
        List<CoachVotingTeamResponse> teams=liveRoster.teams(a.context.getEventId()).stream().map(t->new CoachVotingTeamResponse(t.id(),t.name(),t.logoUrl())).toList();
        return new CoachVotingContextResponse(a.edition.getId(),a.edition.getSlug(),a.edition.getName(),a.edition.getSeason(),state(a.edition,a.submitted,a.now).name(),
                a.edition.getVotingOpensAt(),a.edition.getVotingClosesAt(),a.context.getId(),a.user.getName(),a.context.getEventId(),a.context.getDivisionId(),a.context.getCategoryId(),
                a.context.getTeamId(),ownTeamName(a.context),a.submitted,voteCategories.findByEditionIdOrderByDisplayOrderAsc(a.edition.getId()).stream().map(CoachVotingCategoryResponse::from).toList(),teams);
    }

    @Transactional
    public List<CoachVotingCandidateResponse> candidates(UUID contextId,UUID voteCategoryId,String teamId){
        Access a=access(contextId,true);
        AwardVoteCategory category=voteCategories.findById(voteCategoryId).orElseThrow(()->Errors.badRequest("AWARD_VOTE_CATEGORY_INVALID","Categoria de votação inválida."));
        if(!category.getEditionId().equals(a.edition.getId())) throw Errors.badRequest("AWARD_VOTE_CATEGORY_INVALID","A categoria não pertence a esta edição.");
        List<AwardCandidate> available;
        if(category.getTargetType()==AwardCandidateType.ATHLETE){
            available=liveRoster.athletes(a.edition.getId(),a.context.getEventId(),a.context.getDivisionId(),a.context.getCategoryId(),teamId);
        } else {
            String normalized=teamId==null?"":teamId.trim();
            if(normalized.equals(a.context.getTeamId())) throw Errors.badRequest("AWARD_SELF_COACH_VOTE_FORBIDDEN","O treinador não pode votar na própria equipe para Técnico.");
            available=List.of(liveRoster.coachTeamVote(a.edition.getId(),a.context.getEventId(),a.context.getDivisionId(),a.context.getCategoryId(),normalized));
        }
        return available.stream().filter(c->c.getCandidateType()==category.getTargetType()).map(CoachVotingCandidateResponse::from).toList();
    }

    @Transactional
    public CoachBallotResponse submit(SubmitCoachBallotV2Request request){
        Access a=access(request.contextId(),true);
        List<AwardVoteCategory> categories=voteCategories.findByEditionIdOrderByDisplayOrderAsc(a.edition.getId());
        Map<UUID,AwardVoteCategory> byId=categories.stream().collect(Collectors.toMap(AwardVoteCategory::getId,Function.identity()));
        Map<UUID,CoachBallotVoteRequest> requested=new HashMap<>();
        for(CoachBallotVoteRequest vote:request.votes()){
            if(requested.putIfAbsent(vote.voteCategoryId(),vote)!=null) throw Errors.badRequest("AWARD_DUPLICATE_VOTE_CATEGORY","Cada categoria pode receber apenas um voto.");
            if(!byId.containsKey(vote.voteCategoryId())) throw Errors.badRequest("AWARD_VOTE_CATEGORY_INVALID","Uma categoria não pertence a esta edição.");
        }
        for(AwardVoteCategory category:categories) if(category.isRequired()&&!requested.containsKey(category.getId())) throw Errors.badRequest("AWARD_REQUIRED_VOTE_MISSING","Preencha todas as categorias obrigatórias.");
        Set<UUID> ids=request.votes().stream().map(CoachBallotVoteRequest::candidateId).collect(Collectors.toSet());
        Map<UUID,AwardCandidate> selected=candidates.findAllById(ids).stream().collect(Collectors.toMap(AwardCandidate::getId,Function.identity()));
        if(selected.size()!=ids.size()) throw Errors.badRequest("AWARD_CANDIDATE_INVALID","Um candidato selecionado não existe.");
        List<CoachBallotChoiceResponse> choices=new ArrayList<>();
        for(CoachBallotVoteRequest vote:request.votes()){
            AwardVoteCategory category=byId.get(vote.voteCategoryId()); AwardCandidate candidate=selected.get(vote.candidateId());
            validate(a,category,candidate);
            choices.add(new CoachBallotChoiceResponse(category.getId(),category.getCode(),category.getLabel(),candidate.getId(),candidate.getName(),candidate.getTeamId(),candidate.getTeamName()));
        }
        AwardBallot ballot=new AwardBallot(a.edition.getId(),a.context.getId(),a.user.getId(),clock.instant());
        try{
            ballots.saveAndFlush(ballot);
            ballotVotes.saveAll(request.votes().stream().map(v->new AwardBallotVote(ballot.getId(),v.voteCategoryId(),v.candidateId())).toList());
            ballotVotes.flush();
        }catch(DataIntegrityViolationException ex){throw Errors.conflict("AWARD_BALLOT_ALREADY_SUBMITTED","O voto deste contexto já foi registrado.");}
        choices.sort(Comparator.comparingInt(c->byId.get(c.voteCategoryId()).getDisplayOrder()));
        return new CoachBallotResponse(ballot.getId(),ballot.getEditionId(),ballot.getSubmittedAt(),List.copyOf(choices));
    }

    @Transactional(readOnly=true)
    public CoachBallotResponse ballot(UUID contextId){
        Access a=access(contextId,false);
        AwardBallot ballot=ballots.findByEditionIdAndCoachVoterId(a.edition.getId(),a.context.getId()).orElseThrow(()->Errors.notFound("AWARD_BALLOT_NOT_FOUND","Nenhum voto foi encontrado para este contexto."));
        Map<UUID,AwardVoteCategory> cats=voteCategories.findByEditionIdOrderByDisplayOrderAsc(ballot.getEditionId()).stream().collect(Collectors.toMap(AwardVoteCategory::getId,Function.identity()));
        List<AwardBallotVote> rows=ballotVotes.findByBallotId(ballot.getId());
        Map<UUID,AwardCandidate> selected=candidates.findAllById(rows.stream().map(AwardBallotVote::getCandidateId).toList()).stream().collect(Collectors.toMap(AwardCandidate::getId,Function.identity()));
        List<CoachBallotChoiceResponse> choices=rows.stream().map(v->{AwardVoteCategory c=cats.get(v.getAwardVoteCategoryId());AwardCandidate p=selected.get(v.getCandidateId());return new CoachBallotChoiceResponse(c.getId(),c.getCode(),c.getLabel(),p.getId(),p.getName(),p.getTeamId(),p.getTeamName());}).sorted(Comparator.comparingInt(c->cats.get(c.voteCategoryId()).getDisplayOrder())).toList();
        return new CoachBallotResponse(ballot.getId(),ballot.getEditionId(),ballot.getSubmittedAt(),choices);
    }

    private Access access(UUID contextId,boolean requireOpen){
        UUID userId=currentUser.userId();
        AwardCoachVoter context=contexts.findById(contextId).filter(AwardCoachVoter::isActive).filter(c->c.getUserId().equals(userId)).orElseThrow(()->Errors.forbidden("COACH_CONTEXT_REQUIRED","Este contexto não pertence a esta conta."));
        if(context.getCredentialId()==null) throw Errors.forbidden("COACH_CONTEXT_LEGACY","Este vínculo antigo ainda não possui credencial migrada.");
        AwardCoachCredential credential=credentials.findByIdAndActiveTrue(context.getCredentialId()).filter(c->c.getUserId().equals(userId)).orElseThrow(()->Errors.forbidden("COACH_CREDENTIAL_REQUIRED","O credenciamento não está ativo."));
        AwardEdition edition=editions.findById(context.getEditionId()).orElseThrow(()->Errors.conflict("COACH_CONTEXT_INCONSISTENT","Edição não encontrada."));
        UserAccount user=users.findById(userId).orElseThrow(()->Errors.conflict("COACH_CONTEXT_INCONSISTENT","Conta não encontrada."));
        Instant now=clock.instant(); boolean submitted=ballots.existsByEditionIdAndCoachVoterId(edition.getId(),context.getId());
        if(requireOpen){ if(submitted)throw Errors.conflict("AWARD_BALLOT_ALREADY_SUBMITTED","O voto deste contexto já foi registrado."); if(!edition.isVotingOpenAt(now))throw Errors.conflict("AWARD_VOTING_NOT_OPEN","A votação não está aberta neste momento."); }
        return new Access(credential,context,edition,user,submitted,now);
    }

    private void validate(Access a,AwardVoteCategory category,AwardCandidate candidate){
        boolean same=candidate.isActive()&&candidate.getEditionId().equals(a.edition.getId())&&candidate.getEventId()==a.context.getEventId()&&candidate.getDivisionId()==a.context.getDivisionId()&&candidate.getCategoryId()==a.context.getCategoryId();
        if(!same) throw Errors.badRequest("AWARD_CANDIDATE_OUT_OF_CONTEXT","Um candidato não pertence a este contexto.");
        if(candidate.getCandidateType()!=category.getTargetType()) throw Errors.badRequest("AWARD_CANDIDATE_TYPE_MISMATCH","O candidato não corresponde à categoria de votação.");
        if(category.getTargetType()==AwardCandidateType.COACH){ if(!candidate.isTeamCoachVoteCandidate())throw Errors.badRequest("AWARD_COACH_TEAM_SELECTION_REQUIRED","Selecione uma equipe válida."); if(candidate.getTeamId().equals(a.context.getTeamId()))throw Errors.badRequest("AWARD_SELF_COACH_VOTE_FORBIDDEN","O treinador não pode votar na própria equipe."); }
    }
    private String ownTeamName(AwardCoachVoter c){return candidates.findById(c.getSelfCoachCandidateId()).map(AwardCandidate::getTeamName).orElse(c.getTeamId());}
    private CoachVotingState state(AwardEdition e,boolean submitted,Instant now){if(submitted)return CoachVotingState.SUBMITTED;if(e.getStatus()==AwardEditionStatus.DRAFT)return CoachVotingState.DRAFT;if(e.getStatus()==AwardEditionStatus.CLOSED)return CoachVotingState.CLOSED;if(e.getVotingOpensAt()!=null&&now.isBefore(e.getVotingOpensAt()))return CoachVotingState.SCHEDULED;if(e.getVotingClosesAt()!=null&&!now.isBefore(e.getVotingClosesAt()))return CoachVotingState.CLOSED;return CoachVotingState.OPEN;}
    private record Access(AwardCoachCredential credential,AwardCoachVoter context,AwardEdition edition,UserAccount user,boolean submitted,Instant now){}
}
