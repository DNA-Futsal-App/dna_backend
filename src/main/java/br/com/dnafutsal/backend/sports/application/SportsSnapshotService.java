package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.infrastructure.SportsSnapshotArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SportsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SportsSnapshotService.class);
    private static final Set<String> RECOVERABLE_ERRORS = Set.of(
            "SPORTS_DATA_UNAVAILABLE",
            "SPORTS_API_TIMEOUT",
            "SPORTS_API_CONNECTION_FAILED",
            "SPORTS_API_RATE_LIMITED",
            "SPORTS_API_ACCESS_DENIED",
            "SPORTS_DATA_INVALID"
    );

    private final SportsDataGateway gateway;
    private final SportsSnapshotArchive archive;

    public SportsSnapshotService(SportsDataGateway gateway, SportsSnapshotArchive archive) {
        this.gateway = gateway;
        this.archive = archive;
    }

    @Cacheable(cacheNames = "sports-snapshot", key = "#eventId", sync = true)
    public SportsSnapshot snapshot(long eventId) {
        try {
            SportsSnapshot result = gateway.snapshot(eventId);
            if (result == null || result.event() == null || result.event().eventId() != eventId) {
                throw Errors.badGateway(
                        "SPORTS_EVENT_MISMATCH",
                        "A fonte de dados retornou uma competição diferente da solicitada."
                );
            }
            archive.save(result);
            return result;
        } catch (BusinessException exception) {
            if (!RECOVERABLE_ERRORS.contains(exception.code())) {
                throw exception;
            }
            return archive.findRecent(eventId)
                    .filter(previous -> previous.event() != null && previous.event().eventId() == eventId)
                    .map(previous -> {
                log.warn("Serving archived sports snapshot eventId={} upstreamCode={} collectedAt={}",
                        eventId, exception.code(), previous.collectedAt());
                return previous;
            }).orElseThrow(() -> exception);
        }
    }
}
