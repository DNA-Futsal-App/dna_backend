package br.com.dnafutsal.backend.mail.infrastructure;

import br.com.dnafutsal.backend.mail.domain.MailOutbox;
import br.com.dnafutsal.backend.mail.domain.MailOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, UUID> {

    @Query("select m.id from MailOutbox m where m.status in :statuses and m.nextAttemptAt <= :now order by m.createdAt")
    List<UUID> findDueIds(List<MailOutboxStatus> statuses, Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MailOutbox m where m.id = :id")
    Optional<MailOutbox> findByIdForUpdate(UUID id);
}
