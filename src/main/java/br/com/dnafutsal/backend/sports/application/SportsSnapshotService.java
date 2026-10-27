package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SportsSnapshotService {

    private final SportsDataGateway gateway;

    public SportsSnapshotService(SportsDataGateway gateway) {
        this.gateway = gateway;
    }

    @Cacheable(cacheNames = "sports-snapshot", key = "#eventId", sync = true)
    public SportsSnapshot snapshot(long eventId) {
        SportsSnapshot result = gateway.snapshot(eventId);
        if (result == null || result.event() == null || result.event().eventId() != eventId) {
            throw Errors.badGateway(
                    "SPORTS_EVENT_MISMATCH",
                    "A fonte de dados retornou uma competição diferente da solicitada."
            );
        }
        return result;
    }
}
