package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardPreVoteFinalist;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AwardPreVoteFinalistRepository extends JpaRepository<AwardPreVoteFinalist, UUID> {
    List<AwardPreVoteFinalist> findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategoryOrderByRankAsc(
            UUID editionId, long eventId, long divisionId, long categoryId,
            AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory);
}
