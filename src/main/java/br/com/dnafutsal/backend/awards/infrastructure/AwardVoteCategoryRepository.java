package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AwardVoteCategoryRepository
        extends JpaRepository<AwardVoteCategory, UUID> {

    List<AwardVoteCategory> findByEditionIdOrderByDisplayOrderAsc(
            UUID editionId
    );
}
