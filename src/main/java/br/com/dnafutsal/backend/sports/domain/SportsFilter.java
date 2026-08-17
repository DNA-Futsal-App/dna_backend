package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record SportsFilter(long eventId, String teamId) implements Serializable {
}
