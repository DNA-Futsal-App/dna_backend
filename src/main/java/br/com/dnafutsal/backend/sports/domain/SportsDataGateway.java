package br.com.dnafutsal.backend.sports.domain;

import java.util.List;

public interface SportsDataGateway {
    List<SportsEventView> searchEvents(SportsEventSearch search);

    SportsEventView event(long eventId);

    List<TeamView> teams(long eventId);

    SportsSnapshot snapshot(long eventId);
}
