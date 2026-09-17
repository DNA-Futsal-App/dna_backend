package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardEditionRepository extends JpaRepository<AwardEdition, UUID> {

    Optional<AwardEdition> findBySlug(
            String slug
    );

    List<AwardEdition> findAllByOrderBySeasonDescNameAsc();
}
