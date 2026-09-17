package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardBallotVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AwardBallotVoteRepository
        extends JpaRepository<AwardBallotVote, UUID> {

    List<AwardBallotVote> findByBallotId(
            UUID ballotId
    );
}
