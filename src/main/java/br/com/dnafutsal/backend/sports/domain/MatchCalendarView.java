package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;
import java.util.List;

public record MatchCalendarView(
        String currentPhase,
        List<MatchView> played,
        List<MatchView> upcoming
) implements Serializable {
}
